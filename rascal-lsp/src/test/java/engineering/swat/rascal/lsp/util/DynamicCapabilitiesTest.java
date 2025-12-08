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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
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
import org.rascalmpl.vscode.lsp.parametric.NoContributions;
import org.rascalmpl.vscode.lsp.parametric.capabilities.CompletionCapability;
import org.rascalmpl.vscode.lsp.parametric.capabilities.DynamicCapabilities;

import io.usethesource.vallang.IList;

@RunWith(MockitoJUnitRunner.class)
public class DynamicCapabilitiesTest {

    private final Executor exec = Executors.newCachedThreadPool();

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

        public SomeContribs(List<String> completionTriggerChars) {
            super("test-contribs", exec);

            var vf = IRascalValueFactory.getInstance();
            this.completionTriggerChars = completionTriggerChars.stream()
                .map(vf::string)
                .collect(vf.listWriter());
        }

        @Override
        public CompletableFuture<IList> completionTriggerCharacters() {
            return CompletableFuture.completedFuture(completionTriggerChars);
        }

        @Override
        public CompletableFuture<Boolean> hasCompletion() {
            return CompletableFuture.completedFuture(true);
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
    public void registerOverlappingContributions() throws InterruptedException, ExecutionException {
        for (var trigChars : Arrays.asList(List.of(".", "::"), List.of("+", "*", "-", "/", "%"))) {
            var contribs = new SomeContribs(trigChars);
            dynCap.registerCapabilities(contribs).get();
        }

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(registrationCaptor.capture());
        inOrder.verify(client).unregisterCapability(unregistrationCaptor.capture());
        inOrder.verify(client).registerCapability(registrationCaptor.capture());

        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of(".", "::"), false)), registrationOptions(registrationCaptor.getAllValues().get(0)));
        assertEquals(List.of("textDocument/completion"), unregistrationOptions(unregistrationCaptor.getValue()));
        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(List.of(".", "::", "+", "*", "-", "/", "%"), false)), registrationOptions(registrationCaptor.getAllValues().get(1)));
    }

    @Test
    public void registerAndUnregister() throws InterruptedException, ExecutionException {
        dynCap.registerCapabilities(new SomeContribs(List.of("."))).get();
        dynCap.updateCapabilities(Map.of()).get(); // empty multiplexer

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).registerCapability(any());
        inOrder.verify(client).unregisterCapability(any());
    }

    @Test
    public void registerAndUpdateEmpty() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(List.of("."));
        dynCap.registerCapabilities(contrib).get();
        dynCap.registerCapabilities(new NoContributions(contrib.getName(), exec)).get();

        verify(client).registerCapability(any());
        verify(client, never()).unregisterCapability(any());
    }

    @Test
    public void hasNoDynamicCapability() throws InterruptedException, ExecutionException {
        var contrib = new SomeContribs(List.of("."));

        var complCaps = new CompletionCapabilities();
        complCaps.setDynamicRegistration(false);
        var docCaps = new TextDocumentClientCapabilities();
        docCaps.setCompletion(complCaps);
        var clientCaps = new ClientCapabilities(null, docCaps, null);

        dynCap.setStaticCapabilities(clientCaps, new ServerCapabilities());
        dynCap.registerCapabilities(contrib).get();

        verify(client, never()).registerCapability(any());
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
        dynCap.registerCapabilities(new SomeContribs(List.of("."))).get();

        verify(client, never()).registerCapability(any());
        verify(serverCaps).setCompletionProvider(Mockito.notNull());
    }

}
