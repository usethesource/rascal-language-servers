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
package org.rascalmpl.vscode.lsp.parametric.routing;

import static org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils.NOOP;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentRefreshParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.uri.remote.jsonrpc.ISourceLocationChanged;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IString;

/**
 * Client proxy implementation that aggregates results from multiple servers before forwarding to its own client.
 *
 * Most of the implementation of this class is straightforward. The tricky bits concern the registration and
 * unregistration of capabilities, which require additional thread-safe bookkeeping and conditional forwarding logic.
 * All that is encapsulated in a few separate helper classes and explained in their JavaDoc.
 */
public class MultipleClientProxy implements IBaseLanguageClient {

    private static final Logger logger = LogManager.getLogger(MultipleClientProxy.class);

    private final IBaseLanguageClient client;
    private final ExecutorService exec;
    private final CapabilityRegistry capabilities;

    protected MultipleClientProxy(LanguageClient client, ExecutorService exec) {
        this.client = (IBaseLanguageClient) client;
        this.exec = exec;
        this.capabilities = new CapabilityRegistry();
    }

    @Override
    public void telemetryEvent(Object object) {
        client.telemetryEvent(object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        client.publishDiagnostics(diagnostics);
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        client.showMessage(messageParams);
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return client.showMessageRequest(requestParams);
    }

    @Override
    public void logMessage(MessageParams message) {
        client.logMessage(message);
    }

    @Override
    public void showContent(URI uri, IString title, IInteger viewColumn) {
        client.showContent(uri, title, viewColumn);
    }

    @Override
    public void receiveRegisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/receiveRegisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        client.receiveRegisterLanguage(lang);
    }

    @Override
    public void receiveUnregisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/receiveUnregisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        client.receiveUnregisterLanguage(lang);
    }

    @Override
    public void editDocument(URI uri, @Nullable Range range, int viewColumn) {
        client.editDocument(uri, range, viewColumn);
    }

    @Override
    public void startDebuggingSession(int serverPort) {
        client.startDebuggingSession(serverPort);
    }

    @Override
    public void registerDebugServerPort(int processID, int serverPort) {
        client.registerDebugServerPort(processID, serverPort);
    }

    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        return client.createProgress(params);
    }

    @Override
    public void notifyProgress(ProgressParams params) {
        client.notifyProgress(params);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        return client.applyEdit(params);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        return client.configuration(configurationParams);
    }

    @Override
    public void logTrace(LogTraceParams params) {
        client.logTrace(params);
    }

    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
        return client.refreshCodeLenses();
    }

    @Override
    public CompletableFuture<Void> refreshDiagnostics() {
        return client.refreshDiagnostics();
    }

    @Override
    public CompletableFuture<Void> refreshFoldingRanges() {
        return client.refreshFoldingRanges();
    }

    @Override
    public CompletableFuture<Void> refreshInlayHints() {
        return client.refreshInlayHints();
    }

    @Override
    public CompletableFuture<Void> refreshInlineValues() {
        return client.refreshInlineValues();
    }

    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
        return client.refreshSemanticTokens();
    }

    @Override
    public CompletableFuture<Void> refreshTextDocumentContent(TextDocumentContentRefreshParams params) {
        return client.refreshTextDocumentContent(params);
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        return client.showDocument(params);
    }

    /**
     * Handles incoming capability registrations.
     *
     * The one and only responsibility of this method is to make sure this capability is registered with the client (if it was not already registered by another remote).
     * @see org.eclipse.lsp4j.services.LanguageClient#registerCapability(org.eclipse.lsp4j.RegistrationParams)
     * @see org.rascalmpl.vscode.lsp.parametric.capabilities.CapabilityRegistration
     */
    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return installUpdates(params.getRegistrations(), capabilities::registerCapability);
    }

    /**
     * Handles incoming capability unregistrations.
     *
     * The one and only responsibility of this method is to make sure this capability is unregistered with the client, if no other remote has it registered (anymore).
     * @see org.eclipse.lsp4j.services.LanguageClient#unregisterCapability(org.eclipse.lsp4j.UnregistrationParams)
     * @see org.rascalmpl.vscode.lsp.parametric.capabilities.CapabilityRegistration
     */
    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        return installUpdates(params.getUnregisterations(), capabilities::unregisterCapability);
    }

    private <T> CompletableFuture<Void> installUpdates(List<T> updates, Function<T, CompletableFuture<Void>> installer) {
        var futures = updates.stream().map(installer).map(f -> f == null ? NOOP : f);
        return CompletableFutureUtils.reduce(futures, exec).thenAccept(v -> {});
    }

    private boolean matches(Registration r, Unregistration u) {
        return r.getId().equals(u.getId())
            && r.getMethod().equals(u.getMethod());
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return client.workspaceFolders();
    }

    @Override
    public void sourceLocationChanged(ISourceLocationChanged changed) {
        client.sourceLocationChanged(changed);
    }

    /**
    * <p>
    * Managed collection of capability registrations. Each capability is identified by a method-options pair. For each
    * capability, instances of this class keep track (and protect the consistency) of:
    * <ul>
    *     <li>one-or-more registrations sent by the servers (i.e., multiple servers may register the same capability,
    *     but different servers aren't aware of each others' registrations);
    *     <li>one registration received by the client (i.e., only one registration of the same capability must be
    *     forwarded to VS Code).
    * </ul>
    * Instances of this class ensure that calls of {@link #registerCapability(Registration)} and
    * {@link #unregisterCapability(Unregistration)} take effect atomically. This is non-trivial but important, because
    * even if calls of these methods are made in a single thread (seemingly sequential), the completion of their work is
    * asynchronous (because it may require RPC with the client). As a result, without proper protection, subtle races
    * could arise. Here are two examples.
    * </p>
    *
    * <p>
    * <b>Example 1:</b> Suppose there are two consecutive calls of {@code registerCapability}, R1 and R2. First, R1
    * checks if any registration has been forwarded already to the client (suppose it hasn't), forwards the
    * registration, submits a callback to asynchronously complete the work after RPC, and returns. Next, R2 checks if
    * any registration has been forwarded already (it has, by R1) and returns. Next, the client receives the forwarded
    * registration of R1, fails to process it properly (for whatever reason), and sends back a failure signal. Next, the
    * callback to asynchronously complete the work of R1 propagates to its caller something went wrong. Now, the
    * complication is that R2 either needs to propagate to its caller something went wrong, too, or be retried (but R2
    * has already returned at this point).
    * </p>
    *
    * <p>
    * <b>Example 2:</b> Suppose there are two consecutive calls of {@code registerCapability} and
    * {@code unregisterCapability}, R and U. First, R checks if any registration has been forwarded already (suppose it
    * hasn't), forwards the registration, submits a callback, and returns. Next, U checks if any registration has been
    * forwarded already (it has, by R1), forwards the unregistration, submits a callback, and returns. Next, the client
    * receives the forwarded registration of R, succeeds to process it, and sends back a success signal. Next, the
    * client receives the forwarded unregistration of U, succeeds to process it, and sends back a success signal. Now,
    * the complication is that the callback of R needs to be executed before the callback of U (but this may not be
    * guaranteed by the underlying executor service).
    * </p>
    *
    * <p>
    * There are more examples (e.g., a race between two consecutive calls of {@code unregisterCapability} with a similar
    * complication as in Example 1).
    * </p>
    *
    * <p>
    * To coordinate calls of {@code registerCapability} and {@code unregisterCapability} and avoid races, instances of
    * this class internally use a basic lock-free scheduler. Essentially, the scheduler ensures that the work of each
    * next call of {@code registerCapability} or {@code unregisterCapability} will begin only when the work of the
    * current call, <em>including its asyncronous completion</em>, has ended. See the JavaDoc of {@link Scheduler} for
    * details.
    * </p>
    */
    class CapabilityRegistry {
        private final Scheduler<Void> scheduler = new Scheduler<>(exec);
        private final Map<String, Map<Object, Set<Registration>>> sentByServers = new ConcurrentHashMap<>();
        private final Map<String, Map<Object, Registration>> receivedByClient = new ConcurrentHashMap<>();
        // Notes:
        //   - All usages of `sentByServers` and `receivedByClient` must happen inside tasks submitted to `scheduler`.
        //   - For convenience, classes `MapOfMaps` and `MapOfMapOfSets` offer a number of static utility methods to
        //     access/mutate the inner maps and sets of `sentByServers` and `receivedByClient`.

        /**
         * Forwards the provided capability registration from a server to the client when there are no remaining
         * registrations for that capability sent by servers. This method, together with
         * {@link #unregisterCapability(Unregistration)}, ensures the registrations successfully received by the client
         * are the sum of the registrations sent by the servers.
         */
        public CompletableFuture<Void> registerCapability(Registration fromServer) {
            var method = fromServer.getMethod();
            var id = fromServer.getId();

            logger.trace("Register capability {} ({}): Submitting to scheduler...", method, id);
            return scheduler.submit(result -> {
                var options = fromServer.getRegisterOptions();
                var remaining = MapOfMapsOfSets.size(sentByServers, method, options);

                // Case: Must forward registration
                if (remaining == 0) {
                    logger.trace("Register capability {} ({}): Forwarding registration to client...", method, id);
                    var toClient = new Registration(UUID.randomUUID().toString(), method, options);
                    forwardRegistration(toClient).whenCompleteAsync((v, t) -> {
                        // Case: Forwarding succeeded
                        if (t == null) {
                            logger.trace("Register capability {} ({}): Forwarded registration to client. Succeeded.", method, id);
                            MapOfMaps.put(receivedByClient, method, options, toClient);
                            MapOfMapsOfSets.add(sentByServers, method, options, fromServer);
                            result.complete(null);
                        }
                        // Case: Forwarding failed
                        else {
                            logger.trace("Register capability {} ({}): Forwarded registration to client. Failed: {}", method, id, t);
                            result.completeExceptionally(t);
                        }
                    }, exec);
                    // Don't complete `result` yet. Instead, doing so is the responsibility of the closure on the
                    // previous lines and should happen only when it is known if the registration succeeded or failed at
                    // the client (which isn't immediately after `whenCompleteAsync` returns, but asynchronously).
                }

                // Case: Must not forward
                else {
                    logger.trace("Register capability {} ({}): Not forwarding registration to client (remaining other registrations for same capability: {})", method, id, remaining);
                    MapOfMapsOfSets.add(sentByServers, method, options, fromServer);
                    result.complete(null);
                }
            });
        }

        /**
         * Forwards the provided capability unregistration from a server to the client when it is the last remaining
         * registration for that capability sent by a server. This method, together with
         * {@link #registerCapability(Registration)}, ensures the registrations successfully received by the client are
         * the sum of the registrations sent by the servers.
         */
        public CompletableFuture<Void> unregisterCapability(Unregistration u) {
            var method = u.getMethod();
            var id = u.getId();

            logger.trace("Unregister capability {} ({}): Submitting to scheduler...", method, id);
            return scheduler.submit(result -> {

                // Find the corresponding registration previously sent by a server
                var fromServer = MapOfMapsOfSets.findAny(sentByServers, r -> matches(r, u));
                if (fromServer == null) {
                    var t = new IllegalStateException("Cannot unregister a capability for which no registration was sent by a server");
                    logger.trace("Unregister capability {} ({}). Failed: {}", method, id, t);
                    result.completeExceptionally(t);
                    return;
                }

                var options = fromServer.getRegisterOptions();
                var remaining = MapOfMapsOfSets.size(sentByServers, method, options);

                // Case: Must forward unregistration
                if (remaining == 1) {
                    logger.trace("Unregister capability {} ({}): Forwarding unregistration to client...", method, id);

                    // Find the corresponding registration previously received by the client
                    var toClient = MapOfMaps.get(receivedByClient, method, options);
                    if (toClient == null) {
                        var t = new IllegalStateException("Cannot unregister a capability for which no registration was received by the client");
                        logger.trace("Unregister capability {} ({}). Failed: {}", method, id, t);
                        result.completeExceptionally(t);
                        return;
                    }

                    forwardUnregistration(toClient).whenCompleteAsync((v, t) -> {
                        // Case: Forwarding succeeded
                        if (t == null) {
                            logger.trace("Unregister capability {} ({}): Forwarded unregistration to client. Succeeded.", method, id);
                            MapOfMaps.remove(receivedByClient, method, options);
                            MapOfMapsOfSets.remove(sentByServers, method, options, fromServer);
                            result.complete(null);
                        }
                        // Case: Forwarding failed
                        else {
                            logger.trace("Unregister capability {} ({}): Forwarded unregistration to client. Failed: {}", method, id, t);
                            result.completeExceptionally(t);
                        }
                    }, exec);
                    // Don't complete `result` yet. Instead, doing so is the responsibility of the closure on the
                    // previous lines and should happen only when it is known if the unregistration succeeded or failed
                    // at the client (which isn't immediately after `whenCompleteAsync` returns, but asynchronously).
                }

                // Case: Must not forward unregistration
                else {
                    logger.trace("Unregister capability {} ({}): Not forwarding unregistration to client (remaining registrations for same capability: {})", method, id, remaining);
                    MapOfMapsOfSets.remove(sentByServers, method, options, fromServer);
                    result.complete(null);
                }
            });
        }

        private CompletableFuture<Void> forwardRegistration(Registration r) {
            return client.registerCapability(new RegistrationParams(List.of(r)));
        }

        private CompletableFuture<Void> forwardUnregistration(Registration r) {
            var u = new Unregistration(r.getId(), r.getMethod());
            return client.unregisterCapability(new UnregistrationParams(List.of(u)));
        }
    }
}


/**
 * <p>
 * Basic lock-free scheduler that requires submitted tasks to signal their completion explicitly (and possibly
 * asynchronously). Only after the current task has signaled its completion will the next task be started.
 * </p>
 *
 * <p>
 * Each task is represented as a pair that consists of: (1) a closure that represents the work of the task, with a
 * formal parameter of type {@link CompletableFuture}, and (2) a future that represents the result of the task, which is
 * passed to the closure as actual parameter when the task is started. The body of the closure must eventually call
 * {@link CompletableFuture#complete} (or any other {@code complete...} method) on the future to signal its completion
 * and provide the result. Not performing such a call causes the scheduler to get stuck.
 * </p>
 *
 * <p>
 * When a new task is submitted, and if no existing task is in progress yet, then the new task is started immediately.
 * In contrast, if an existing task is in progress already, then the new task is started when the current task and all
 * other pending existing tasks in the queue have signaled their completion. To this end, attempts to start tasks are
 * made in two places: in the method to submit a new task, and in the closure that is run when an existing task has
 * signaled its completion.
 * </p>
 *
 * @param R Result type of tasks
 */
class Scheduler<R> {
    private final ExecutorService exec;
    private final Queue<Task<R>> tasks;
    private final AtomicBoolean busy;

    public Scheduler(ExecutorService exec) {
        this.exec = exec;
        this.busy = new AtomicBoolean(false);
        this.tasks = new ConcurrentLinkedQueue<>();
    }

    public CompletableFuture<R> submit(Consumer<CompletableFuture<R>> action) {
        var result = new CompletableFuture<R>();
        var task = new Task<>(action, result);
        tasks.offer(task);
        exec.submit(this::attemptStartTask);
        return result;
    }

    private void attemptStartTask() {
        if (busy.compareAndSet(false, true)) {
            var task = tasks.poll();
            if (task != null) {
                // Install a finally-block-like closure that is run when the current task has signaled its completion.
                // This is to ensure that the next task is subsequently started (if any). Note: Result `v` and exception
                // `t` are ignored; it is the responsibility of other calls on `task.result` to handle them.
                task.result.whenComplete((v, t) -> {
                    busy.set(false);
                    exec.submit(this::attemptStartTask);
                });
                task.action.accept(task.result);
                // Don't unset `busy` yet. Instead, doing so is the responsibility of the closure on the previous lines
                // and should happen only when the task has signaled its completion.
            } else {
                busy.set(false);
            }
        }
    }

    private static class Task<R> {
        private final Consumer<CompletableFuture<R>> action;
        private final CompletableFuture<R> result;

        public Task(Consumer<CompletableFuture<R>> action, CompletableFuture<R> result) {
            this.action = action;
            this.result = result;
        }
    }
}

/**
 * Utility methods to perform operations on maps of maps
 */
class MapOfMaps {
    private MapOfMaps() {}

    public static <K1 extends @NonNull Object, K2 extends @NonNull Object, V> @Nullable V get(Map<K1, Map<K2, V>> mapOfMaps, K1 key1, K2 key2) {
        return mapOfMaps
            .getOrDefault(key1, Collections.emptyMap())
            .get(key2);
    }

    public static <K1 extends @NonNull Object, K2 extends @NonNull Object, V> @Nullable V put(Map<K1, Map<K2, V>> mapOfMaps, K1 key1, K2 key2, V value) {
        return mapOfMaps
            .computeIfAbsent(key1, m -> new ConcurrentHashMap<>())
            .put(key2, value);
    }

    public static <K1 extends @NonNull Object, K2 extends @NonNull Object, V> @Nullable V remove(Map<K1, Map<K2, V>> mapOfMaps, K1 key1, K2 key2) {
        // Default needs to be mutable (support `remove` calls) so `Collections.emptyMap()` cannot be used
        var map = mapOfMaps.getOrDefault(key1, new HashMap<>());
        var removed = map.remove(key2);
        if (map.isEmpty()) {
            mapOfMaps.remove(key1);
        }
        return removed;
    }
}

/**
 * Utility methods to perform operations on maps of maps of sets
 */
class MapOfMapsOfSets {
    private MapOfMapsOfSets() {}

    public static <K1 extends @NonNull Object, K2 extends @NonNull Object, V> boolean add(Map<K1, Map<K2, Set<V>>> mapOfMapOfSets, K1 key1, K2 key2, V value) {
        return mapOfMapOfSets
            .computeIfAbsent(key1, m -> new ConcurrentHashMap<>())
            .computeIfAbsent(key2, o -> ConcurrentHashMap.newKeySet())
            .add(value);
    }

    public static <K1 extends @NonNull Object, K2 extends @NonNull Object, V> @Nullable V findAny(Map<K1, Map<K2, Set<V>>> mapOfMapOfSets, Predicate<V> predicate) {
        return mapOfMapOfSets
            .values()
            .stream()                    // Stream of maps of sets of values
            .map(Map::values)            // Stream of collections of sets of values
            .flatMap(Collection::stream) // Stream of sets of values
            .flatMap(Collection::stream) // Stream of values
            .filter(predicate)
            .findAny()
            .orElse(null);
    }

    public static <K1 extends @NonNull Object, K2 extends @NonNull Object, V> boolean remove(Map<K1, Map<K2, Set<V>>> mapOfMapOfSets, K1 key1, K2 key2, V value) {
        // Defaults need to be mutable (support `remove` calls) so `Collections.empty...()` cannot be used
        var mapOfSets = mapOfMapOfSets.getOrDefault(key1, new HashMap<>());
        var set = mapOfSets.getOrDefault(key2, new HashSet<>());
        var removed = false;
        if (value != null) { // Convince Checker Framework
            removed = set.remove(value);
        }
        if (set.isEmpty()) {
            mapOfSets.remove(key2);
        }
        if (mapOfSets.isEmpty()) {
            mapOfMapOfSets.remove(key1);
        }
        return removed;
    }

    public static <K1 extends @NonNull Object, K2 extends @NonNull Object, V> int size(Map<K1, Map<K2, Set<V>>> mapOfMapOfSets, K1 key1, K2 key2) {
        return mapOfMapOfSets
            .getOrDefault(key1, Collections.emptyMap())
            .getOrDefault(key2, Collections.emptySet())
            .size();
    }
}
