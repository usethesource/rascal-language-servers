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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
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
    private final BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser;
    private final ISourceLocation file;
    private final AtomicReference<@NonNull Update> inProgress;
    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> lastWithoutErrors;
    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> last;

    public TextDocumentState(
            BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser,
            ISourceLocation file, int initialVersion, String initialContent) {

        this.parser = parser;
        this.file = file;
        this.inProgress = new AtomicReference<>(new Update(initialVersion, initialContent));
        this.lastWithoutErrors = new AtomicReference<>();
        this.last = new AtomicReference<>();
    }

    /**
     * WARNING: OUTDATED DOCUMENTATION
     *
     * The current call of this method guarantees that, until the next call,
     * each intermediate call of `getCurrentTreeAsync` returns (a future for) a
     * *correct* versioned tree. This means that:
     *   - the version of the tree is parameter `version`;
     *   - the tree is produced by parsing parameter `content`.
     *
     * Thus, callers of `getCurrentTreeAsync` are guaranteed to obtain a
     * consistent <version, tree> pair.
     */
    public void update(int version, String content) {
        DEBOUNCER.debounce(() -> {
            var u = new Update(version, content);
            replaceIfNewer(inProgress, u);
        });
    }

    public ISourceLocation getLocation() {
        return file;
    }

    public Versioned<String> getCurrentContent() {
        return inProgress.get().content;
    }

    public CompletableFuture<Versioned<ITree>> getCurrentTreeAsync() {
        return inProgress.get().tree;
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
        public final Versioned<String> content;
        public final CompletableFuture<Versioned<ITree>> tree;

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
                    // Add diagnostics
                })
                ;
        }

        public int getVersion() {
            return content.version();
        }
    }

    private static boolean replaceIfNewer(AtomicReference<Update> ref, Update newValue) {
        Update oldValue = ref.get();
        if (oldValue == null || oldValue.getVersion() < newValue.getVersion()) {
            return ref.compareAndSet(oldValue, newValue) || replaceIfNewer(ref, newValue);
        } else {
            return false;
        }
    }

    private static class Debouncer {
        private final Executor executor;
        private final AtomicInteger numberOfDebounces;

        private Debouncer(Duration delay) {
            this.executor = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS);
            this.numberOfDebounces = new AtomicInteger(0);
        }

        private void debounce(Runnable doContinue) {
            debounce(doContinue, () -> {});
        }

        private void debounce(Runnable doContinue, Runnable doBreak) {
            var n = numberOfDebounces.incrementAndGet();
            CompletableFuture.runAsync(() -> {
                if (numberOfDebounces.get() == n) {
                    doContinue.run();
                } else {
                    doBreak.run();
                }
            }, executor);
        }
    }

    private static final Duration  DELAY     = Duration.ofMillis(500);
    private static final Debouncer DEBOUNCER = new Debouncer(DELAY);
}
