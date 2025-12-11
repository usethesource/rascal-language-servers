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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Spy private CompletionCapability completion;
    @Spy private ServerCapabilities serverCapabilities;
    @Captor ArgumentCaptor<RegistrationParams> registrationCaptor;
    @Captor ArgumentCaptor<UnregistrationParams> unregistrationCaptor;

    @Before
    public void setUp() {
        dynCap = new DynamicCapabilities(client, exec, List.of(completion), clientCapabilities(true));
        dynCap.registerStaticCapabilities(serverCapabilities);
    }

    private static ClientCapabilities clientCapabilities(boolean supportsDynamicCompletion) {
        var complCaps = new CompletionCapabilities();
        complCaps.setDynamicRegistration(supportsDynamicCompletion);
        var docCaps = new TextDocumentClientCapabilities();
        docCaps.setCompletion(complCaps);
        return new ClientCapabilities(null, docCaps, null);
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

        public SomeContribs(String completionTriggerChar) {
            this(List.of(completionTriggerChar));
        }

        public SomeContribs(List<String> completionTriggerChars) {
            this(completionTriggerChars, exec);
        }

        public SomeContribs(List<String> completionTriggerChars, Executor exec) {
            super("test-contribs", exec);

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

        @Override
        public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
            return CompletableFuture.completedFuture(null);
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
    public void mergeCompletionOptions() {
        var opts1 = new CompletionRegistrationOptions(List.of(",", ":"), false);
        var opts2 = new CompletionRegistrationOptions(List.of(".", ":"), false);
        var mergedOpts = completion.mergeOptions(opts1, opts2);
        assertNotNull(mergedOpts);
        assertEquals(Set.of(",", ":", "."), new HashSet<>(mergedOpts.getTriggerCharacters()));

        assertEquals(opts1, completion.mergeOptions(opts1, null));
        assertEquals(opts1, completion.mergeOptions(null, opts1));
    }

    @Test
    public void registerSingleContribution() throws InterruptedException, ExecutionException {
        var trigChars = List.of(".", "::");
        var contribs = new SomeContribs(trigChars);

        dynCap.updateCapabilities(List.of(contribs)).get();
        verify(client, only()).registerCapability(registrationCaptor.capture());
        verify(serverCapabilities, never()).setCompletionProvider(any()); // no static registration

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

        verify(client, only()).registerCapability(any());
    }

    @Test
    public void registerEmpty() throws InterruptedException, ExecutionException {
        var contrib = new NoContributions("NoneLang", exec);
        dynCap.updateCapabilities(List.of(contrib)).get();

        verify(client, never()).unregisterCapability(any());
        verify(client, never()).registerCapability(any());
    }

    @Test
    public void nullOptionCapability() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");

        when(completion.options(contrib)).thenReturn(CompletableFuture.completedFuture(null));
        dynCap.updateCapabilities(List.of(contrib)).get();

        verify(client, only()).registerCapability(registrationCaptor.capture());
        assertNull(registrationCaptor.getValue().getRegistrations().get(0).getRegisterOptions());
    }

    @Test
    public void hasNoDynamicCapability() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");

        dynCap = new DynamicCapabilities(client, exec, List.of(completion), clientCapabilities(false));
        dynCap.registerStaticCapabilities(serverCapabilities);

        dynCap.updateCapabilities(List.of(contrib)).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
        verify(serverCapabilities).setCompletionProvider(Mockito.notNull());
    }

    @Test
    public void preferStaticRegistration() throws InterruptedException, ExecutionException {
        dynCap = new DynamicCapabilities(client, exec, List.of(new CompletionCapability(true)), clientCapabilities(true));
        dynCap.registerStaticCapabilities(serverCapabilities);

        dynCap.updateCapabilities(List.of(new SomeContribs("."))).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
        verify(serverCapabilities).setCompletionProvider(Mockito.notNull());
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

    @Test
    public void unregisterFails() throws InterruptedException, ExecutionException {
        when(client.unregisterCapability(any())).thenThrow(new RuntimeException("Unregistration failed!"));

        dynCap.updateCapabilities(List.of(new SomeContribs(List.of(".")))).get();
        dynCap.updateCapabilities(List.of(new SomeContribs(List.of(".")), new SomeContribs(List.of(":")))).get();

        verify(client).registerCapability(any()); // once, since on the second round, unregister fails
        verify(client).unregisterCapability(any());
    }

}
