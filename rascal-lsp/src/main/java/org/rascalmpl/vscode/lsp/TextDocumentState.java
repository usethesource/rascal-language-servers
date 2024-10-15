/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.rascalmpl.library.util.ErrorRecovery;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.Versioned;

import io.usethesource.vallang.ISourceLocation;

/**
 * TextDocumentState encapsulates the current contents of every open file editor,
 * and the corresponding latest parse tree that belongs to it.
 * It is parametrized by the parser that must be used to map the string
 * contents to a tree. All other TextDocumentServices depend on this information.
 *
 * Objects of this class are used by the implementations of RascalTextDocumentService
 * and ParametricTextDocumentService.
 */
public class TextDocumentState {
    private static final Logger logger = LogManager.getLogger(TextDocumentState.class);

    private final BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser;
    private final ISourceLocation file;
    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> lastWithoutErrors;
    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> last;
    private final Debouncer<Update> debouncer;

    @SuppressWarnings("java:S3077") // It's safe...
    private volatile CompletableFuture<Update> inProgress;

    public TextDocumentState(
            BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser,
            ISourceLocation file, int initialVersion, String initialContent) {

        this.parser = parser;
        this.file = file;
        this.lastWithoutErrors = new AtomicReference<>();
        this.last = new AtomicReference<>();
        this.debouncer = new Debouncer<>(Duration.ofMillis(500));

        var initialUpdate = new Update(initialVersion, initialContent);
        this.inProgress = CompletableFuture.completedFuture(initialUpdate);
    }

    public void update(int version, String content, boolean debounce) {
        inProgress = debouncer.schedule(() -> {
            logger.trace("Processing document update after debounce");
            return new Update(version, content);
        }, debounce);
    }

    public ISourceLocation getLocation() {
        return file;
    }

    public Versioned<String> getCurrentContent() {
        return inProgress.thenApply(Update::getContent).join();
    }

    public CompletableFuture<Versioned<ITree>> getCurrentTreeAsync() {
        return inProgress.thenApply(Update::getTree).thenCompose(Function.identity());
    }

    public @MonotonicNonNull Versioned<ITree> getLastTree() {
        return last.get();
    }

    public @MonotonicNonNull Versioned<ITree> getLastTreeWithoutErrors() {
        return lastWithoutErrors.get();
    }

    private static final IRascalValueFactory VALUES = (RascalValueFactory) ValueFactoryFactory.getValueFactory();
    private static final ErrorRecovery RECOVERY = new ErrorRecovery(VALUES);

    private class Update {
        private final Versioned<String> content;
        private final CompletableFuture<Versioned<ITree>> tree;

        public Update(int version, String content) {
            this.content = new Versioned<>(version, content);
            this.tree = parser
                .apply(file, content)
                .thenApply(t -> new Versioned<>(version, t))
                .whenComplete((t, error) -> {
                    if (t != null) {
                        var errors = RECOVERY.findAllErrors(t.get());
                        if (errors.isEmpty()) {
                            Versioned.replaceIfNewer(lastWithoutErrors, t);
                        }
                        Versioned.replaceIfNewer(last, t);
                    }
                    // TODO: Add diagnostics
                });
        }

        public Versioned<String> getContent() {
            return content;
        }

        public CompletableFuture<Versioned<ITree>> getTree() {
            return tree;
        }
    }

    private static class Debouncer<T> {
        private final Executor delayedExecutor;
        private final AtomicStampedReference<@MonotonicNonNull CompletableFuture<T>> latest;

        private Debouncer(Duration delay) {
            this.delayedExecutor = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS);
            this.latest = new AtomicStampedReference<>(null, 0);
        }

        private CompletableFuture<T> schedule(Supplier<T> supplier, boolean debounce) {
            var executor = debounce ? delayedExecutor : ForkJoinPool.commonPool();

            var oldStamp = latest.getStamp();
            var oldRef   = latest.getReference();
            var newStamp = oldStamp + 1;
            var newRef   = CompletableFuture
                .supplyAsync(() -> {
                    if (newStamp == latest.getStamp()) {
                        return CompletableFuture.completedFuture(supplier.get());
                    } else {
                        return latest.getReference();
                    }
                }, executor)
                .thenCompose(Function.identity());

            latest.weakCompareAndSet(oldRef, newRef, oldStamp, newStamp);
            return newRef;
        }
    }
}
