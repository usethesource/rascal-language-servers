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
package engineering.swat.rascal.lsp.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.parametric.LanguageContributionsMultiplexer;
import org.rascalmpl.vscode.lsp.parametric.NoContributions;
import org.rascalmpl.vscode.lsp.parametric.capabilities.CompletionCapability;
import org.rascalmpl.vscode.lsp.parametric.capabilities.DynamicCapabilities;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

import io.usethesource.vallang.IList;

@RunWith(MockitoJUnitRunner.class)
public class DynamicCapabilitiesTest {

    private DynamicCapabilities dynCap;

    // Mocks
    @Spy private LanguageClientStub client;
    @Captor ArgumentCaptor<RegistrationParams> registrationCaptor;
    @Captor ArgumentCaptor<UnregistrationParams> unregistrationCaptor;

    @Before
    public void setUp() {
        dynCap = new DynamicCapabilities(client, List.of(
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

        @Override
        public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
            return CompletableFutureUtils.completedFuture(SummaryConfig.FALSY, exec);
        }

        @Override
        public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
            return CompletableFutureUtils.completedFuture(SummaryConfig.FALSY, exec);
        }

        @Override
        public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
            return CompletableFutureUtils.completedFuture(SummaryConfig.FALSY, exec);
        }

    }

    class CapabilityMatcher implements ArgumentMatcher<RegistrationParams> {

        private final Map<String, @Nullable Object> caps;

        CapabilityMatcher(Map<String, @Nullable Object> caps) {
            this.caps = caps;
        }

        @Override
        public boolean matches(RegistrationParams p) {
            Map<String, Object> registrationOptions = new HashMap<>();
            for (var r : p.getRegistrations()) {
                // We intentionally ignore the ID here, since it's randomly generated
                registrationOptions.put(r.getMethod(), r.getRegisterOptions());
            }

            return registrationOptions.equals(caps);
        }

    }

    private Map<String, @Nullable Object> registrationOptions(RegistrationParams params) {
        return params.getRegistrations()
            .stream()
            .collect(Collectors.toMap(Registration::getMethod, Registration::getRegisterOptions));
    }

    private List<String> unregistrationOptions(UnregistrationParams params) {
        return params.getUnregisterations()
            .stream()
            .map(Unregistration::getMethod)
            .collect(Collectors.toList());
    }

    @Test
    public void registerSingleContribution() throws InterruptedException, ExecutionException {
        var trigChars = List.of(".", "::");
        var contribs = new SomeContribs(trigChars);

        dynCap.registerCapabilities(contribs).get();
        Mockito.verify(client, only()).registerCapability(registrationCaptor.capture());

        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(trigChars, false)), registrationOptions(registrationCaptor.getValue()));
    }

    @Test
    public void registerReplacingContribution() throws InterruptedException, ExecutionException {
        for (var trigChars : Arrays.asList(List.of(".", "::"), List.of("+", "*", "-", "/", "%"))) {
            var contribs = new SomeContribs(trigChars);
            dynCap.registerCapabilities(contribs).get();
        }

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
    public void registerIndenticalContribution() throws InterruptedException, ExecutionException {
        for (var trigChars : Arrays.asList(List.of(".", "::"), List.of(".", "::"))) {
            var contribs = new SomeContribs(trigChars);
            dynCap.registerCapabilities(contribs).get();
        }

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        inOrder.verifyNoMoreInteractions();

        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of(".", "::"), false)), registrationOptions(registrationCaptor.getAllValues().get(0)));
    }

    @Test
    public void registerOverlappingContributions() throws InterruptedException, ExecutionException {
        Map<String, LanguageContributionsMultiplexer> contribs = new HashMap<>();
        for (var trigChars : Arrays.asList(List.of("."), List.of(":"))) {
            var c = new SomeContribs(trigChars);
            dynCap.registerCapabilities(c).get();
            var plex = contribs.computeIfAbsent(c.getName(), _k -> new LanguageContributionsMultiplexer(c.getName(), Executors.newCachedThreadPool()));
            // unique contribution key, so we keep both
            plex.addContributor(trigChars.toString(), c);
        }

        // unregister one of both
        var name = contribs.keySet().iterator().next();
        contribs.get(name).removeContributor(List.of(".").toString());

        dynCap.updateCapabilities(contribs).get();

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
        dynCap.registerCapabilities(new SomeContribs(List.of("."))).get();
        dynCap.updateCapabilities(Map.of()).get(); // empty multiplexer

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(any());
        inOrder.verify(client).unregisterCapability(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void registerAndUpdateEmpty() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(".");
        dynCap.registerCapabilities(contrib).get();
        dynCap.registerCapabilities(new NoContributions(contrib.getName(), Executors.newCachedThreadPool())).get();

        verify(client).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
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
        dynCap.registerCapabilities(contrib).get();

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

        dynCap = new DynamicCapabilities(client, List.of(new StaticCompletionCapabilty()));

        ServerCapabilities serverCaps = Mockito.mock();
        dynCap.setStaticCapabilities(null, serverCaps);
        dynCap.registerCapabilities(new SomeContribs(".")).get();

        verify(client, never()).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
        verify(serverCaps).setCompletionProvider(Mockito.notNull());
    }

    @Test
    public void multiThreadingConisistency() throws InterruptedException, ExecutionException {
        int N = 50;
        List<CompletableFuture<Void>> jobs = new ArrayList<>(N);
        var exec = Executors.newFixedThreadPool(N / 2); // less threads than jobs; causes some overlap

        for (int i = 0; i < N; i++) {
            final var trig = Integer.toString(i);
            var job = CompletableFuture.supplyAsync(() -> {
                var contribs = new SomeContribs(List.of(trig), exec);
                return dynCap.registerCapabilities(contribs);
            }, exec).thenCompose(Function.identity());
            jobs.add(job);
        }

        // Await all parallel jobs
        CompletableFutureUtils.reduce(jobs).get();

        InOrder inOrder = inOrder(client);

        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        for (int i = 1; i < N; i++) {
            inOrder.verify(client).unregisterCapability(unregistrationCaptor.capture());
            inOrder.verify(client).registerCapability(registrationCaptor.capture());
        }
        inOrder.verifyNoMoreInteractions();

        var opts = (CompletionRegistrationOptions) registrationCaptor.getValue().getRegistrations().get(0).getRegisterOptions();
        var expectedTrigChars = IntStream.range(0, N).boxed().map(Object::toString).collect(Collectors.toList());
        var trigChars = opts.getTriggerCharacters();

        Collections.sort(expectedTrigChars);
        Collections.sort(trigChars);

        assertEquals(expectedTrigChars, trigChars);
    }

}
