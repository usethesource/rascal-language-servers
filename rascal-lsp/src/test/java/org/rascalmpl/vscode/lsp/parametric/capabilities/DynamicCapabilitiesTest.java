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
package org.rascalmpl.vscode.lsp.parametric.capabilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionRegistrationOptions;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.parametric.NoContributions;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

import io.usethesource.vallang.IList;

@RunWith(MockitoJUnitRunner.class)
public class DynamicCapabilitiesTest {

    private final ExecutorService exec = Executors.newCachedThreadPool();

    private DynamicCapabilities dynCap;

    // Mocks
    @Spy private LanguageClientStub client;
    @Captor ArgumentCaptor<RegistrationParams> registrationCaptor;
    @Captor ArgumentCaptor<UnregistrationParams> unregistrationCaptor;

    @Before
    public void setUp() {
        dynCap = new DynamicCapabilities(client, exec, List.of(
            new CompletionCapability()
        ));
    }

    abstract class LanguageClientStub implements LanguageClient {

        @Override
        public CompletableFuture<Void> registerCapability(RegistrationParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
            return CompletableFuture.completedFuture(null);
        }

    }

    class SomeContribs extends NoContributions {

        private final IList completionTriggerChars;
        private final Executor exec;

        public SomeContribs(String completionTriggerChar) {
            this(List.of(completionTriggerChar));
        }

        public SomeContribs(List<String> completionTriggerChars) {
            this(completionTriggerChars, Executors.newCachedThreadPool());
        }

        public SomeContribs(List<String> completionTriggerChars, Executor exec) {
            super("test-contribs", exec);

            this.exec = exec;

            var vf = IRascalValueFactory.getInstance();
            this.completionTriggerChars = completionTriggerChars.stream()
                .map(vf::string)
                .collect(vf.listWriter());
        }

        @Override
        public CompletableFuture<IList> completionTriggerCharacters() {
            return CompletableFutureUtils.completedFuture(completionTriggerChars, exec);
        }

        @Override
        public CompletableFuture<Boolean> hasCompletion() {
            return CompletableFutureUtils.completedFuture(true, exec);
        }

    }

    private Map<String, @Nullable Object> registrationOptions(RegistrationParams params) {
        return params.getRegistrations().stream().collect(Collectors.toMap(Registration::getMethod, Registration::getRegisterOptions));
    }

    private List<String> unregistrationOptions(UnregistrationParams params) {
        return params.getUnregisterations().stream().map(Unregistration::getMethod).collect(Collectors.toList());
    }

    @SafeVarargs
    private void registerIncrementally(List<String>... options) throws InterruptedException, ExecutionException {
        registerIncrementally(Stream.of(options).map(SomeContribs::new).map(ILanguageContributions.class::cast).collect(Collectors.toList()));
    }

    private void registerIncrementally(List<ILanguageContributions> contribs) throws InterruptedException, ExecutionException {
        for (int i = 0; i < contribs.size(); i++) {
            dynCap.updateCapabilities(contribs.subList(0, i + 1)).get();
        }
    }

    //// TESTS

    @Test
    public void registerSingleContribution() throws InterruptedException, ExecutionException {
        var trigChars = List.of(".", "::");
        var contribs = new SomeContribs(trigChars);

        dynCap.updateCapabilities(List.of(contribs)).get();
        verify(client).registerCapability(registrationCaptor.capture());
        verify(client, never()).unregisterCapability(any());

        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(trigChars, false)), registrationOptions(registrationCaptor.getValue()));
    }

    @Test
    public void registerIncrementalContribution() throws InterruptedException, ExecutionException {
        registerIncrementally(List.of(".", "::"), List.of("+", "*", "-", "/", "%"));

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        inOrder.verify(client).unregisterCapability(unregistrationCaptor.capture());
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        inOrder.verifyNoMoreInteractions();

        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of(".", "::"), false)), registrationOptions(registrationCaptor.getAllValues().get(0)));
        assertEquals(List.of("textDocument/completion"), unregistrationOptions(unregistrationCaptor.getValue()));
        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of(".", "::", "+", "*", "-", "/", "%"), false)), registrationOptions(registrationCaptor.getAllValues().get(1)));
    }

    @Test
    public void registerIdenticalContribution() throws InterruptedException, ExecutionException {
        registerIncrementally(List.of(".", "::"), List.of(".", "::"));

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        inOrder.verifyNoMoreInteractions();

        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of(".", "::"), false)), registrationOptions(registrationCaptor.getAllValues().get(0)));
    }

    @Test
    public void registerOverlappingContributions() throws InterruptedException, ExecutionException {
        var contribs = Stream.of(List.of("."), List.of(":"))
            .map(SomeContribs::new)
            .map(ILanguageContributions.class::cast)
            .collect(Collectors.toList());

        registerIncrementally(contribs);

        // unregister one of both
        dynCap.updateCapabilities(contribs.subList(1, 2)).get();

        InOrder inOrder = inOrder(client);
        // intial registration
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of("."), false)), registrationOptions(registrationCaptor.getValue()));

        // extra registration with extra trigger characters
        inOrder.verify(client).unregisterCapability(any());
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of(".", ":"), false)), registrationOptions(registrationCaptor.getValue()));

        // unregistration (partial)
        inOrder.verify(client).unregisterCapability(any());
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of(":"), false)), registrationOptions(registrationCaptor.getValue()));

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void registerAndUnregister() throws InterruptedException, ExecutionException {
        dynCap.updateCapabilities(List.of(new SomeContribs(List.of(".")))).get();
        dynCap.updateCapabilities(List.of()).get(); // empty multiplexer

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(any());
        inOrder.verify(client).unregisterCapability(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void registerAndUpdateEmpty() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");
        dynCap.updateCapabilities(List.of(contrib)).get();
        dynCap.updateCapabilities(List.of(contrib, new NoContributions(contrib.getName(), exec))).get();

        verify(client, never()).unregisterCapability(any());
        verify(client).registerCapability(any());
    }

    @Test
    public void hasNoDynamicCapability() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");

        var complCaps = new CompletionCapabilities();
        complCaps.setDynamicRegistration(false);
        var docCaps = new TextDocumentClientCapabilities();
        docCaps.setCompletion(complCaps);
        var clientCaps = new ClientCapabilities(null, docCaps, null);

        dynCap.setStaticCapabilities(clientCaps, new ServerCapabilities());
        dynCap.updateCapabilities(List.of(contrib)).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
    }

    @Test
    public void preferStaticRegistration() throws InterruptedException, ExecutionException {
        class StaticCompletionCapabilty extends CompletionCapability {
            @Override
            protected boolean preferStaticRegistration() {
                return true;
            }
        }

        dynCap = new DynamicCapabilities(client, exec, List.of(new StaticCompletionCapabilty()));

        ServerCapabilities serverCaps = Mockito.mock();
        dynCap.setStaticCapabilities(null, serverCaps);
        dynCap.updateCapabilities(List.of(new SomeContribs("."))).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
        verify(serverCaps).setCompletionProvider(Mockito.notNull());
    }

    @Test
    public void multiThreadingConisistency() throws InterruptedException, ExecutionException {
        int N = 50;
        List<CompletableFuture<Void>> jobs = new ArrayList<>(N);
        var exec = Executors.newFixedThreadPool(N / 2); // less threads than jobs; causes some overlap
        var expectedTrigChars = IntStream.range(0, N).boxed().map(Object::toString).collect(Collectors.toList());
        var contribs = expectedTrigChars.stream().map(SomeContribs::new).map(ILanguageContributions.class::cast).collect(Collectors.toList());
        for (int i = 0; i < N; i++) {
            var sl = contribs.subList(0, i + 1);
            var job = CompletableFuture.supplyAsync(() -> dynCap.updateCapabilities(sl), exec).thenCompose(Function.identity());
            jobs.add(job);
        }
        var optionSublists = IntStream.range(0, N).boxed().map(i -> expectedTrigChars.subList(0, i + 1)).collect(Collectors.toList());

        // Await all parallel jobs
        CompletableFutureUtils.reduce(jobs, exec).get();

        InOrder inOrder = inOrder(client);

        inOrder.verify(client).registerCapability(any());
        for (int i = 1; i < N; i++) {
            inOrder.verify(client).unregisterCapability(any());
            inOrder.verify(client).registerCapability(registrationCaptor.capture());
        }
        inOrder.verifyNoMoreInteractions();

        var lastOpts = (CompletionRegistrationOptions) registrationCaptor.getValue().getRegistrations().get(0).getRegisterOptions();
        assertTrue(optionSublists.stream().anyMatch(opts -> opts.equals(lastOpts.getTriggerCharacters())));
    }

}
