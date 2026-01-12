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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
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
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionOptions;
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
        dynCap = new DynamicCapabilities(client, exec, Set.of(completion), clientCapabilities(true));
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

    @SafeVarargs
    private void registerSequentially(List<String>... options) throws InterruptedException, ExecutionException {
        registerSequentially(Stream.of(options).map(SomeContribs::new).map(ILanguageContributions.class::cast).collect(Collectors.toList()));
    }

    private void registerSequentially(List<ILanguageContributions> contribs) throws InterruptedException, ExecutionException {
        for (int i = 0; i < contribs.size(); i++) {
            dynCap.update(contribs.subList(0, i + 1)).get();
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

        dynCap.update(List.of(contribs)).get();
        verify(client, only()).registerCapability(registrationCaptor.capture());
        verify(serverCapabilities, never()).setCompletionProvider(any()); // no static registration

        assertSingleRegistration("textDocument/completion", new CompletionRegistrationOptions(trigChars, false), registrationCaptor.getValue());
    }

    @Test
    public void registerIncrementalContribution() throws InterruptedException, ExecutionException {
        registerSequentially(List.of(".", "::"), List.of("+", "*", "-", "/", "%"));

        verify(client, atLeastOnce()).registerCapability(registrationCaptor.capture());
        verify(client, atMostOnce()).unregisterCapability(unregistrationCaptor.capture());

        assertSingleRegistration("textDocument/completion", new CompletionRegistrationOptions(List.of(".", "::", "+", "*", "-", "/", "%"), false), registrationCaptor.getValue());
    }

    private void assertSingleRegistration(String method, Object expectedOpts, RegistrationParams actualParams) {
        assertEquals("Should have a single registration", 1, actualParams.getRegistrations().size());
        var r = actualParams.getRegistrations().get(0);
        assertEquals("Should have the right method", method, r.getMethod());
        assertRegistrationOptionEquals(method, expectedOpts, r.getRegisterOptions());
    }

    private <T extends Comparable<? super T>> List<T> sorted(List<T> l) {
        var l2 = new ArrayList<>(l);
        Collections.sort(l2);
        return l2;
    }

    private void assertRegistrationOptionEquals(String method, Object expected, Object actual) {
        if (expected instanceof CompletionRegistrationOptions) {
            var e = (CompletionRegistrationOptions) expected;
            var a = (CompletionRegistrationOptions) actual;
            assertEquals(String.format("%s should have equal resolve provider flag", method), e.getResolveProvider(), a.getResolveProvider());
            assertEquals(String.format("%s should have equal trigger characters", method), sorted(e.getTriggerCharacters()), sorted(a.getTriggerCharacters()));
        } else {
            assertEquals(String.format("%s should have equal options"), expected, actual);
        }
    }

    @Test
    public void registerIdenticalContribution() throws InterruptedException, ExecutionException {
        registerSequentially(List.of(".", "::"), List.of(".", "::"));

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        inOrder.verifyNoMoreInteractions();

        assertSingleRegistration("textDocument/completion", new CompletionRegistrationOptions(List.of(".", "::"), false), registrationCaptor.getAllValues().get(0));
    }

    @Test
    public void registerOverlappingContributions() throws InterruptedException, ExecutionException {
        var contribs = Stream.of(List.of("."), List.of(":"))
            .map(SomeContribs::new)
            .map(ILanguageContributions.class::cast)
            .collect(Collectors.toList());

        registerSequentially(contribs);

        // unregister one of both
        dynCap.update(contribs.subList(1, 2)).get();

        InOrder inOrder = inOrder(client);
        // initial registration
        inOrder.verify(client, atLeast(3)).registerCapability(registrationCaptor.capture());
        var args = registrationCaptor.getAllValues();
        assertSingleRegistration("textDocument/completion", new CompletionRegistrationOptions(List.of("."), false), args.get(0));
        assertSingleRegistration("textDocument/completion", new CompletionRegistrationOptions(List.of(".", ":"), false), args.get(1));
        assertSingleRegistration("textDocument/completion", new CompletionRegistrationOptions(List.of(":"), false), args.get(2));

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void registerAndUnregister() throws InterruptedException, ExecutionException {
        dynCap.update(List.of(new SomeContribs(List.of(".")))).get();
        dynCap.update(List.of()).get(); // empty multiplexer

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(any());
        inOrder.verify(client).unregisterCapability(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void registerAndUpdateEmpty() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");
        dynCap.update(List.of(contrib)).get();
        dynCap.update(List.of(contrib, new NoContributions(contrib.getName(), exec))).get();

        verify(client, only()).registerCapability(any());
    }

    @Test
    public void registerEmpty() throws InterruptedException, ExecutionException {
        var contrib = new NoContributions("NoneLang", exec);
        dynCap.update(List.of(contrib)).get();

        verify(client, never()).unregisterCapability(any());
        verify(client, never()).registerCapability(any());
    }

    @Test
    public void nullOptionCapability() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");

        when(completion.options(contrib)).thenReturn(CompletableFuture.completedFuture(null));
        dynCap.update(List.of(contrib)).get();

        verify(client, only()).registerCapability(registrationCaptor.capture());
        assertNull(registrationCaptor.getValue().getRegistrations().get(0).getRegisterOptions());
    }

    @Test
    public void hasNoDynamicCapability() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");

        dynCap = new DynamicCapabilities(client, exec, Set.of(completion), clientCapabilities(false));
        dynCap.registerStaticCapabilities(serverCapabilities);

        dynCap.update(List.of(contrib)).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
        verify(serverCapabilities).setCompletionProvider(new CompletionOptions());
    }

    @Test
    public void preferStaticRegistration() throws InterruptedException, ExecutionException {
        dynCap = new DynamicCapabilities(client, exec, Set.of(new CompletionCapability(true)), clientCapabilities(true));
        dynCap.registerStaticCapabilities(serverCapabilities);

        dynCap.update(List.of(new SomeContribs("."))).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
        verify(serverCapabilities).setCompletionProvider(Mockito.notNull());
    }

    @Test
    public void multiThreadingAtomicity() throws InterruptedException, ExecutionException {
        int N = 50;
        var jobs = new ArrayList<CompletableFuture<Void>>(N);
        var callerExec = Executors.newSingleThreadExecutor(); // less threads than jobs; causes some overlap
        var expectedTrigChars = IntStream.range(0, N).boxed().map(Object::toString).collect(Collectors.toList());
        var contribs = expectedTrigChars.stream().map(SomeContribs::new).map(ILanguageContributions.class::cast).collect(Collectors.toList());
        for (int i = 0; i < N; i++) {
            var sl = contribs.subList(0, i + 1);
            var job = CompletableFuture.supplyAsync(() -> dynCap.update(sl), callerExec).thenCompose(Function.identity());
            jobs.add(job);
        }
        var optionSublists = IntStream.range(0, N).boxed().map(i -> expectedTrigChars.subList(0, i + 1)).collect(Collectors.toList());

        // Await all parallel jobs
        CompletableFutureUtils.reduce(jobs, callerExec).get();

        InOrder inOrder = inOrder(client);

        inOrder.verify(client, atLeast(N)).registerCapability(registrationCaptor.capture());
        inOrder.verifyNoMoreInteractions();

        var lastOpts = (CompletionRegistrationOptions) registrationCaptor.getValue().getRegistrations().get(0).getRegisterOptions();
        var trigChars = sorted(lastOpts.getTriggerCharacters());
        assertTrue(optionSublists.stream().map(this::sorted).anyMatch(opts -> opts.equals(trigChars)));
    }

}
