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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.rascalmpl.library.util.ErrorRecovery;
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

    private static final ErrorRecovery RECOVERY =
        new ErrorRecovery((RascalValueFactory) ValueFactoryFactory.getValueFactory());

    private final BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser;
    private final ISourceLocation location;

    @SuppressWarnings("java:S3077") // Visibility of writes is enough
    private volatile Update current;
    private final Debouncer<Versioned<ITree>> currentTreeAsyncDebouncer;

    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> lastWithoutErrors;
    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> last;

    public TextDocumentState(
            BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser,
            ISourceLocation location, int initialVersion, String initialContent) {

        this.parser = parser;
        this.location = location;

        this.current = new Update(initialVersion, initialContent);
        this.currentTreeAsyncDebouncer = new Debouncer<>(50,
            this::getCurrentTreeAsyncIfParsing, this::getCurrentTreeAsync);

        this.lastWithoutErrors = new AtomicReference<>();
        this.last = new AtomicReference<>();
    }

    public ISourceLocation getLocation() {
        return location;
    }

    public void update(int version, String content) {
        current = new Update(version, content);
        // The creation of the `Update` object doesn't trigger the parser yet.
        // This happens only when the tree is requested.
    }

    public Versioned<String> getCurrentContent() {
        return current.getContent();
    }

    public CompletableFuture<Versioned<ITree>> getCurrentTreeAsync() {
        return current.getTreeAsync(); // Triggers the parser
    }

    public CompletableFuture<Versioned<ITree>> getCurrentTreeAsync(Duration delay) {
        return currentTreeAsyncDebouncer.get(delay);
    }

    public @Nullable CompletableFuture<Versioned<ITree>> getCurrentTreeAsyncIfParsing() {
        var update = current;
        return update.isParsing() ? update.getTreeAsync() : null;
    }

    public CompletableFuture<Versioned<List<Diagnostic>>> getCurrentDiagnostics() {
        throw new UnsupportedOperationException();
        // TODO: In a separate PR
    }

    public @MonotonicNonNull Versioned<ITree> getLastTree() {
        return last.get();
    }

    public @MonotonicNonNull Versioned<ITree> getLastTreeWithoutErrors() {
        return lastWithoutErrors.get();
    }

    private class Update {
        private final int version;
        private final String content;
        private final CompletableFuture<Versioned<ITree>> treeAsync;
        private final AtomicBoolean parsing;

        public Update(int version, String content) {
            this.version = version;
            this.content = content;
            this.treeAsync = new CompletableFuture<>();
            this.parsing = new AtomicBoolean(false);
        }

        public Versioned<String> getContent() {
            return new Versioned<>(version, content);
        }

        public CompletableFuture<Versioned<ITree>> getTreeAsync() {
            parseIfNotParsing();
            return treeAsync;
        }

        public boolean isParsing() {
            return parsing.get();
        }

        private void parseIfNotParsing() {
            if (parsing.compareAndSet(false, true)) {
                parser
                    .apply(location, content)
                    .thenApply(t -> new Versioned<>(version, t))
                    .whenComplete((t, error) -> {
                        if (t != null) {
                            var errors = RECOVERY.findAllErrors(t.get());
                            if (errors.isEmpty()) {
                                Versioned.replaceIfNewer(lastWithoutErrors, t);
                            }
                            Versioned.replaceIfNewer(last, t);
                            treeAsync.complete(t);
                        }
                        if (error != null) {
                            treeAsync.completeExceptionally(error);
                        }
                    });
            }
        }
    }
}

/**
 * A *debouncer* is an object to get a *resource* from an *underlying resource
 * provider* with a certain delay. From the perspective of the debouncer, the
 * underlying resource provider has two states: initialized and not-initialized.
 *
 *  1. While the underlying resource provider is not-initialized (e.g., the
 *     computation of a parse tree has not yet started), the debouncer waits
 *     until the delay is over.
 *
 *  2. When the underlying resource provider becomes initialized (e.g., the
 *     computation of a parse tree has started, but possibly not yet finished),
 *     the debouncer returns a future for the resource.
 *
 *  3. When the underlying resource provider is not-initialized, but the delay
 *     is over, the debouncer forcibly initializes the resource (e.g., it starts
 *     the asynchronous computation of a parse tree) and returns a future for
 *     the resource.
 */
class Debouncer<T> {

    // A debouncer is implemented using a *delayed executor* as `scheduler`. The
    // idea is to *periodically* check the state of the underlying resource
    // provider. More precisely, each time when the resource is requested,
    // immediately check if case 1 or case 3 (above) are applicable. If so,
    // return. If not, schedule a *delayed future* to retry the request, to be
    // completed after a small `period` (e.g., 50 milliseconds).
    //
    // The reason why multiple futures are scheduled in small periods, instead
    // of a single future for the entire large delay, is that futures (of type
    // `CompletableFuture`) cannot be interrupted.

    private final int period; // Milliseconds
    private final Executor scheduler;

    // At any point in time, only one delayed future to retry the request for
    // the resource should be `scheduled`, tied with the total remaining delay.
    // For bookkeeping, a *stamped reference* is used. The reference is the
    // delayed future, while the stamp is the remaining delay *upon completion
    // of the delayed future*.

    private final AtomicStampedReference<@Nullable CompletableFuture<T>> scheduled;

    // The underlying resource provider is represented abstractly in terms of
    // two suppliers, each of which corresponds with a state of the underlying
    // resource provider. `getIfInitialized` should return `null` iff the
    // underlying resource provided is not-initialized.

    private final Supplier<@Nullable CompletableFuture<T>> getIfInitialized;
    private final Supplier<CompletableFuture<T>> initializeAndGet;

    public Debouncer(Duration period,
            Supplier<@Nullable CompletableFuture<T>> getIfInitialized,
            Supplier<CompletableFuture<T>> initializeAndGet) {

        this(Math.toIntExact(period.toMillis()), getIfInitialized, initializeAndGet);
    }

    public Debouncer(int period,
            Supplier<@Nullable CompletableFuture<T>> getIfInitialized,
            Supplier<CompletableFuture<T>> initializeAndGet) {

        this.period = period;
        this.scheduler = CompletableFuture.delayedExecutor(period, TimeUnit.MILLISECONDS);
        this.scheduled = new AtomicStampedReference<>(null, 0);
        this.getIfInitialized = getIfInitialized;
        this.initializeAndGet = initializeAndGet;
    }

    public CompletableFuture<T> get(Duration delay) {
        return get(Math.toIntExact(delay.toMillis()));
    }

    public CompletableFuture<T> get(int delay) {
        return schedule(delay, false);
    }

    private CompletableFuture<T> schedule(int delay, boolean reschedule) {

        // Get a consistent old stamp and old reference
        var oldRef = scheduled.getReference();
        var oldStamp = scheduled.getStamp();
        while (!scheduled.weakCompareAndSet(oldRef, oldRef, oldStamp, oldStamp));

        // Compute a new reference (delayed future to retry this method)
        var delayArg = new CompletableFuture<Integer>();
        var newRef = delayArg
            .thenApplyAsync(this::reschedule, scheduler)
            .thenCompose(Function.identity());

        // Compute a new stamp
        var delayRemaining = Math.max(oldStamp, delay);
        var newStamp = delayRemaining - period;

        // If the underlying resource provider is initialized, then return the
        // future to get the resource
        var future = getIfInitialized.get();
        if (future != null && scheduled.weakCompareAndSet(oldRef, null, oldStamp, 0)) {
            return future;
        }

        // Otherwise, if the delay is over already, then initialize the
        // underlying resource provider and return the future to get the
        // resource
        if (delayRemaining <= 0 && scheduled.weakCompareAndSet(oldRef, null, oldStamp, 0)) {
            return initializeAndGet.get();
        }

        // Otherwise (i.e., the delay isn't over yet), if a delayed future to
        // retry this method hasn't been scheduled yet, or if it must be
        // rescheduled regardless, then schedule it
        if ((oldRef == null || reschedule) && scheduled.weakCompareAndSet(oldRef, newRef, oldStamp, newStamp)) {
            delayArg.complete(newStamp);
            return newRef;
        }

        // Otherwise (i.e, the delay is not yet over, but a delayed future has
        // been scheduled already), then update the remaining delay; it will be
        // used to complete the already-scheduled delayed future.
        if (scheduled.attemptStamp(oldRef, newStamp)) {
            return oldRef;
        }

        // When this point is reached, concurrent modifications to the stamp or
        // the reference in `scheduled` have happened. In that case, retry
        // immediately.
        return schedule(delay, reschedule);
    }

    private CompletableFuture<T> reschedule(int delay) {
        return schedule(delay, true);
    }
}
