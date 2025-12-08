package engineering.swat.rascal.lsp.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.only;

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
import org.eclipse.lsp4j.CompletionRegistrationOptions;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
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
    private final IRascalValueFactory VF = IRascalValueFactory.getInstance();

    private DynamicCapabilities dynCap;

    @Spy private LanguageClientStub client;

    @Captor ArgumentCaptor<RegistrationParams> registrationCaptor;
    @Captor ArgumentCaptor<UnregistrationParams> unregistrationCaptor;

    public DynamicCapabilitiesTest() {
        // when(client.registerCapability(any(RegistrationParams.class))).thenReturn(CompletableFuture.completedFuture(null));
        // when(client.unregisterCapability(any(UnregistrationParams.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Before
    public void setUp() {
        dynCap = new DynamicCapabilities(client, List.of(
            new CompletionCapability()
        ));
    }

    class LanguageClientStub implements LanguageClient {

        @Override
        public CompletableFuture<Void> registerCapability(RegistrationParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void telemetryEvent(Object object) {
            throw new UnsupportedOperationException("Unimplemented method 'telemetryEvent'");
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            throw new UnsupportedOperationException("Unimplemented method 'publishDiagnostics'");
        }

        @Override
        public void showMessage(MessageParams messageParams) {
            throw new UnsupportedOperationException("Unimplemented method 'showMessage'");
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            throw new UnsupportedOperationException("Unimplemented method 'showMessageRequest'");
        }

        @Override
        public void logMessage(MessageParams message) {
            throw new UnsupportedOperationException("Unimplemented method 'logMessage'");
        }

    }

    class SomeContribs extends NoContributions {

        private final IList completionTriggerChars;

        public SomeContribs(List<String> completionTriggerChars) {
            super("test-contribs", exec);

            this.completionTriggerChars = completionTriggerChars.stream()
                .map(VF::string)
                .collect(VF.listWriter());
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
    public void basicRegistration() throws InterruptedException, ExecutionException {
        var trigChars = List.of(".", "::");
        var contribs = new SomeContribs(trigChars);

        dynCap.registerCapabilities(contribs).get();
        Mockito.verify(client, only()).registerCapability(registrationCaptor.capture());

        assertEquals(Map.of("textDocument/completion", new CompletionRegistrationOptions(trigChars, false)), registrationOptions(registrationCaptor.getValue()));
    }

    @Test
    public void additionalRegistration() throws InterruptedException, ExecutionException {
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

}
