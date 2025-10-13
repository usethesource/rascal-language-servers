/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.ISourceLocation;

/**
 * A monitor implementation that forwards progress reports to the LSP client
 *
 * We're bundling all progress grouped by executing thread. We assume a job never switches threads before it's finished.
 */
public class RascalLSPMonitor implements IRascalMonitor {
    private final Logger logger;
    private final IBaseLanguageClient languageClient;
    private final String progressPrefix;
    // Future work: think if we can calculate a progress bar (not sure how VS Code likes us jumping back as soon as we know about more work)

    public RascalLSPMonitor(IBaseLanguageClient languageClient, Logger logger) {
        this(languageClient, logger, "");
    }
    /**
     * @param languageClient lsp client to forward messages to
     * @param logger log4j target to send `warning` messages to
     * @param progressPrefix an optional prefix for progress bar messages (uses to prefix the language name to the progress bar)
     */
    public RascalLSPMonitor(IBaseLanguageClient languageClient, Logger logger, String progressPrefix) {
        this.logger = logger;
        this.languageClient = languageClient;
        this.progressPrefix = progressPrefix;
    }

    private class LSPProgressBar {
        private final String rootName;
        private final String progressId;
        private final CompletableFuture<Void> created;
        /** Sometimes we get multiple starts for the same job, so we count them to responds to the right end */
        private int nested = 0;

        public LSPProgressBar(String rootName, String progressId) {
            this.rootName = rootName;
            this.progressId = progressId;
            this.created = createProgressBar(progressId);

            var msg = new WorkDoneProgressBegin();
            msg.setTitle(progressPrefix + rootName);
            msg.setCancellable(activeFutures.containsKey(progressId));
            notifyProgress(msg);
        }

        private void notifyProgress(WorkDoneProgressNotification value) {
            created.thenRun(() -> languageClient
                .notifyProgress(new ProgressParams(Either.forLeft(progressId), Either.forLeft(value))));
        }

        public void progress(String message) {
            var msg = new WorkDoneProgressReport();
            msg.setMessage(message);
            msg.setCancellable(activeFutures.containsKey(progressId));
            notifyProgress(msg);
        }

        public void finish() {
            notifyProgress(new WorkDoneProgressEnd());
        }

        private CompletableFuture<Void> createProgressBar(String id) {
            return tryRegisterProgress(id)
                .thenApply(CompletableFuture::completedFuture)
                .exceptionally(t -> retry(t, 0, id))
                .thenCompose(Function.identity());
        }

        private CompletableFuture<Void> tryRegisterProgress(String id) {
            return languageClient.createProgress(new WorkDoneProgressCreateParams(Either.forLeft(id)));
        }

        private CompletableFuture<Void> retry(Throwable first, int retry, String id) {
            if(retry >= 100) {
                return CompletableFuture.failedFuture(first);
            }
            return tryRegisterProgress(id)
                .thenApply(CompletableFuture::completedFuture)
                .exceptionally(t -> {
                    first.addSuppressed(t);
                    return retry(first, retry+1, id);
                })
                .thenCompose(Function.identity());
        }


    }

    private final ThreadLocal<@Nullable LSPProgressBar> activeProgress = new ThreadLocal<>();
    private final Map<String, InterruptibleFuture<?>> activeFutures = new ConcurrentHashMap<>();

    /**
     * Register a running {@link InterruptibleFuture}, so it can be interrupted later.
     * Must be called from the same thread as the corresponding {@link jobStarted}.
     * @param name The task name, equal to the one used for {@link jobStarted}.
     * @param future The future doing the work.
     */
    public void registerActiveFuture(String name, InterruptibleFuture<? extends @PolyNull Object> future) {
        activeFutures.put(generateProgressId(name), future);
    }

    /**
     * Unregister an {@link InterruptibleFuture} that has finished.
     * Must be called from the same thread as the corresponding {@link jobEnded}.
     * @param name The task name, equal to the one used for {@link jobEnded}.
     */
    public void unregisterActiveFuture(String name) {
        activeFutures.remove(generateProgressId(name));
    }

    @Override
    public void jobStart(String name, int workShare, int totalWork) {
        var progress = this.activeProgress.get();
        if (progress != null) {
            if (progress.rootName.equals(name)) {
                progress.nested++;
            }
            // an top-level progress is already active, so just report this as sub-progress
            progress.progress(name);
        }
        else {
            // we have to register a progress bar, since we're the first one on this thread
            progress = new LSPProgressBar(name, generateProgressId(name));
            this.activeProgress.set(progress);
        }
    }

    private static String generateProgressId(String topLevelName) {
        Thread t = Thread.currentThread();
        return "T" + Integer.toHexString(t.hashCode()) + "" + Long.toHexString(t.getId()) + "" + Integer.toHexString(System.identityHashCode(topLevelName));
    }


    @Override
    public void jobStep(String name, String message, int workShare) {
        var progress = this.activeProgress.get();
        if (progress == null) {
            logger.warn("Got a job-step while we never started something. Name: {} - Message: {}", name, message);
            return;
        }
        progress.progress(message);
    }

    @Override
    public int jobEnd(String name, boolean succeeded) {
        var progress = this.activeProgress.get();
        if (progress == null) {
            logger.warn("Got a jobEnd while we never started something. Name: {}", name);
            return 1;
        }
        if (name != null && name.equals(progress.rootName)){
            // okay, we might want to stop the progress bar
            if (progress.nested > 0) {
                // unless the top level was started multiple times (aka nested)
                progress.nested--;
                return 1;
            }
            this.activeProgress.remove();
            progress.finish();
        }
        return 1;
    }


    @Override
    public void endAllJobs() {
        var progress = this.activeProgress.get();
        if (progress != null) {
            this.activeProgress.remove();
            progress.finish();
        }
    }

    @Override
    public void jobTodo(String name, int work) {
        // left empty, since we don't support progress bar right now
        // it's left for future work
    }

    @Override
    public boolean jobIsCanceled(String name) {
        return false;
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        logger.warn("{} : {}", src, message);
    }

    /**
     * Cancel the running {@link InterruptibleFuture} corresponding to a specific progress bar.
     * @param progressId The identifier of the progress bar.
     */
    public void cancelProgress(String progressId) {
        var future = activeFutures.get(progressId);
        if (future != null) {
            future.interrupt();
        }
    }
}
