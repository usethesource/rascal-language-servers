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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
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
import org.mockito.exceptions.verification.VerificationInOrderFailure;
import org.mockito.junit.MockitoJUnitRunner;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.parametric.NoContributions;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

import io.usethesource.vallang.IList;

@RunWith(MockitoJUnitRunner.class)
public class DynamicCapabilitiesTest {

    private final ExecutorService exec = Executors.newCachedThreadPool();

    private DynamicRegistration dynCap;

    // Mocks
    @Spy private LanguageClientStub client;
    @Spy private CompletionCapability completion = new CompletionCapability(exec);
    @Spy private ServerCapabilities serverCapabilities;
    @Captor ArgumentCaptor<RegistrationParams> registrationCaptor;
    @Captor ArgumentCaptor<UnregistrationParams> unregistrationCaptor;

    @Before
    public void setUp() {
        dynCap = new DynamicRegistration(client, exec,
            Set.of(completion),
            Set.of(/*new FileOperationsProperty()*/),
            clientCapabilities(true)
        );
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

    static class P implements ICapabilityParams {

        private final Set<ILanguageContributions> contribs;
        private final Set<String> extensions;

        P(Set<ILanguageContributions> contribs) {
            this(Set.of(), contribs);
        }

        P(Set<String> extensions, Set<ILanguageContributions> contribs) {
            this.extensions = extensions;
            this.contribs = contribs;
        }

        static P of(ILanguageContributions... contribs) {
            return new P(Set.of(contribs));
        }

        static P of(List<ILanguageContributions> contribs) {
            return new P(new HashSet<>(contribs));
        }

        @Override
        public Collection<ILanguageContributions> contributions() {
            return contribs;
        }

        @Override
        public Set<String> extensions() {
            return extensions;
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

    private <T> @Nullable T registrationOptions(String name, RegistrationParams params, Class<T> t) {
        var optReg = params.getRegistrations().stream().filter(r -> name.equals(r.getMethod())).findFirst();
        if (optReg.isEmpty()) {
            throw new IllegalArgumentException("No registration of " + name);
        }
        return t.cast(optReg.get().getRegisterOptions());
    }

    @SafeVarargs
    private void registerSequentially(List<String>... options) throws InterruptedException, ExecutionException {
        registerSequentially(Stream.of(options).map(SomeContribs::new).map(ILanguageContributions.class::cast).collect(Collectors.toList()));
    }

    private void registerSequentially(List<ILanguageContributions> contribs) throws InterruptedException, ExecutionException {
        for (int i = 0; i < contribs.size(); i++) {
            dynCap.updateRegistrations(P.of(contribs.subList(0, i + 1))).get();
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

        assertEquals(opts1, completion.mergeNullableOptions(opts1, null));
        assertEquals(opts1, completion.mergeNullableOptions(null, opts1));
        assertEquals(null, completion.mergeNullableOptions(null, null));
    }

    @Test
    public void registerSingleContribution() throws InterruptedException, ExecutionException {
        var trigChars = List.of(".", "::");
        var contribs = new SomeContribs(trigChars);

        dynCap.updateRegistrations(P.of(List.of(contribs))).get();
        verify(client, only()).registerCapability(registrationCaptor.capture());
        verify(serverCapabilities, never()).setCompletionProvider(any()); // no static registration

        assertEquals(trigChars, registrationOptions("textDocument/completion", registrationCaptor.getValue(), CompletionRegistrationOptions.class).getTriggerCharacters());
    }

    @Test
    public void registerIncrementalContribution() throws InterruptedException, ExecutionException {
        registerSequentially(List.of(".", "::"), List.of("+", "*", "-", "/", "%"));

        verify(client, atLeastOnce()).registerCapability(registrationCaptor.capture());
        verify(client, atMostOnce()).unregisterCapability(unregistrationCaptor.capture());

        assertEquals(Set.of(".", "::", "+", "*", "-", "/", "%"), Set.copyOf(registrationOptions("textDocument/completion", registrationCaptor.getValue(), CompletionRegistrationOptions.class).getTriggerCharacters()));
    }

    @Test
    public void registerIdenticalContribution() throws InterruptedException, ExecutionException {
        registerSequentially(List.of(".", "::"), List.of(".", "::"));

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        inOrder.verifyNoMoreInteractions();

        assertEquals(Set.of(".", "::"), Set.copyOf(registrationOptions("textDocument/completion", registrationCaptor.getValue(), CompletionRegistrationOptions.class).getTriggerCharacters()));
    }

    @Test
    public void registerOverlappingContributions() throws InterruptedException, ExecutionException {
        var contribs = Stream.of(List.of("."), List.of(":"))
            .map(SomeContribs::new)
            .map(ILanguageContributions.class::cast)
            .collect(Collectors.toList());

        registerSequentially(contribs);

        // unregister one of both
        dynCap.updateRegistrations(P.of(contribs.subList(1, 2))).get();

        InOrder inOrder = inOrder(client);
        // initial registration
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        assertEquals(Set.of("."), Set.copyOf(registrationOptions("textDocument/completion", registrationCaptor.getValue(), CompletionRegistrationOptions.class).getTriggerCharacters()));

        // extra registration with extra trigger characters
        inOrder.verify(client).unregisterCapability(any());
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        assertEquals(Set.of(".", ":"), Set.copyOf(registrationOptions("textDocument/completion", registrationCaptor.getValue(), CompletionRegistrationOptions.class).getTriggerCharacters()));

        // unregistration (partial)
        inOrder.verify(client).unregisterCapability(any());
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        assertEquals(Set.of(":"), Set.copyOf(registrationOptions("textDocument/completion", registrationCaptor.getValue(), CompletionRegistrationOptions.class).getTriggerCharacters()));

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void registerAndUnregister() throws InterruptedException, ExecutionException {
        dynCap.updateRegistrations(P.of(List.of(new SomeContribs(List.of("."))))).get();
        dynCap.updateRegistrations(P.of(List.of())).get(); // empty multiplexer

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(any());
        inOrder.verify(client).unregisterCapability(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void registerAndUpdateEmpty() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");
        dynCap.updateRegistrations(P.of(contrib)).get();
        dynCap.updateRegistrations(P.of(contrib, new NoContributions(contrib.getName(), exec))).get();

        verify(client, only()).registerCapability(any());
    }

    @Test
    public void registerEmpty() throws InterruptedException, ExecutionException {
        var contrib = new NoContributions("NoneLang", exec);
        dynCap.updateRegistrations(P.of(contrib)).get();

        verify(client, never()).unregisterCapability(any());
        verify(client, never()).registerCapability(any());
    }

    @Test
    public void nullOptionCapability() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");

        when(completion.options(contrib)).thenReturn(CompletableFuture.completedFuture(null));
        dynCap.updateRegistrations(P.of(contrib)).get();

        verify(client, only()).registerCapability(registrationCaptor.capture());
        assertNull(registrationCaptor.getValue().getRegistrations().get(0).getRegisterOptions());
    }

    @Test
    public void hasNoDynamicCapability() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");

        dynCap = new DynamicRegistration(client, exec, Set.of(completion), Set.of(), clientCapabilities(false));
        dynCap.registerStaticCapabilities(P.of(contrib), serverCapabilities);

        dynCap.updateRegistrations(P.of(contrib)).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
        verify(serverCapabilities).setCompletionProvider(Mockito.notNull());
    }

    @Test
    public void preferStaticRegistration() throws InterruptedException, ExecutionException {
        var contrib = P.of(new SomeContribs("."));
        dynCap = new DynamicRegistration(client, exec, Set.of(new CompletionCapability(true, exec)), Set.of(), clientCapabilities(true));
        dynCap.registerStaticCapabilities(contrib, serverCapabilities);

        dynCap.updateRegistrations(contrib).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
        verify(serverCapabilities).setCompletionProvider(Mockito.notNull());
    }

    @Test
    public void multiThreadingAtomicity() throws InterruptedException, ExecutionException {
        int N = 50;
        List<CompletableFuture<Void>> jobs = new ArrayList<>(N);
        var callerExec = Executors.newFixedThreadPool(N / 2); // less threads than jobs; causes some overlap
        var expectedTrigChars = IntStream.range(0, N).boxed().map(Object::toString).collect(Collectors.toList());
        var contribs = expectedTrigChars.stream().map(SomeContribs::new).map(ILanguageContributions.class::cast).collect(Collectors.toList());
        for (int i = 0; i < N; i++) {
            var sl = contribs.subList(0, i + 1);
            var job = CompletableFuture.supplyAsync(() -> dynCap.updateRegistrations(P.of(sl)), callerExec).thenCompose(Function.identity());
            jobs.add(job);
        }
        var optionSublists = IntStream.range(0, N).boxed().map(i -> expectedTrigChars.subList(0, i + 1)).collect(Collectors.toList());

        // Await all parallel jobs
        CompletableFutureUtils.reduce(jobs, callerExec).get();

        InOrder inOrder = inOrder(client);

        inOrder.verify(client).registerCapability(any());
        boolean atomic = true;
        int i = 1;
        while (atomic && i < N) {
            try {
                inOrder.verify(client).unregisterCapability(any());
                atomic = false;
                inOrder.verify(client).registerCapability(registrationCaptor.capture());
                atomic = true;
            } catch (VerificationInOrderFailure e) {
                if (atomic) {
                    break;
                }
                fail(e.toString());
            }
            i++;
        }
        inOrder.verifyNoMoreInteractions();

        var lastOpts = (CompletionRegistrationOptions) registrationCaptor.getValue().getRegistrations().get(0).getRegisterOptions();
        assertTrue(optionSublists.stream().anyMatch(opts -> opts.equals(lastOpts.getTriggerCharacters())));
    }

    @Test
    public void noRegisterWhenUnregisterFails() throws InterruptedException, ExecutionException {
        when(client.unregisterCapability(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Unregistration failed!")));

        dynCap.updateRegistrations(P.of(new SomeContribs(List.of(".")))).get();
        dynCap.updateRegistrations(P.of(new SomeContribs(List.of(".")), new SomeContribs(List.of(":")))).get();

        verify(client).registerCapability(any()); // once, since on the second round, unregister fails
        verify(client).unregisterCapability(any());
    }

}
