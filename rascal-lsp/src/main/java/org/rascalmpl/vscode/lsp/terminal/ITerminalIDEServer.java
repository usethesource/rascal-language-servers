package org.rascalmpl.vscode.lsp.terminal;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.io.StandardTextReader;
import io.usethesource.vallang.type.TypeFactory;

/**
 * Server interface for remote implementation of @see IDEServices
 */
public interface ITerminalIDEServer {
    @JsonRequest
    default CompletableFuture<Void> browse(BrowseParameter uri) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest
    default CompletableFuture<Void> edit(EditParameter edit)  {
        throw new UnsupportedOperationException();
    }

    @JsonRequest
    default CompletableFuture<SourceLocationParameter> resolveProjectLocation(SourceLocationParameter edit) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/receiveRegisterLanguage") 
    default CompletableFuture<Void> receiveRegisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException();
    }

    public static class EditParameter {
        private String module;
    
        public EditParameter(String module) {
            this.module = module;
        }
    
        public String getModule() {
            return module;
        }

        @Override
        public String toString() {
            return "editParameter: " + module;
        }
    }

    public static class BrowseParameter {
        private String uri;
    
        public BrowseParameter(String uri) {
            this.uri = uri;
        }
    
        public String getUri() {
            return uri;
        }

        @Override
        public String toString() {
            return "browseParameter:" + uri;
        }
    }

    public static class SourceLocationParameter {
        private String loc;
    
        public SourceLocationParameter(ISourceLocation loc) {
            this.loc = loc.toString();
        }
    
        public ISourceLocation getLocation() {
            try {
                return (ISourceLocation) new StandardTextReader().read(
                    IRascalValueFactory.getInstance(), 
                    TypeFactory.getInstance().sourceLocationType(),
                    new StringReader(loc));
            } catch (FactTypeUseException | IOException e) {
                // this should really never happen
                assert false;
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "sourceLocationParameter: " + loc;
        }
    }

    public static class LanguageParameter {
        private final String pathConfig;
	    private final String name; // name of the language
	    private final String extension; // extension for files in this language
	    private final String mainModule; // main module to locate mainFunction in
	    private final String mainFunction; // main function which contributes the language implementation

        LanguageParameter(String pathConfig, String name, String extension, String mainModule, String mainFunction) {
            this.pathConfig = pathConfig.toString();
            this.name = name;
            this.extension = extension;
            this.mainModule = mainModule;
            this.mainFunction = mainFunction;
        }

        public String getPathConfig() {
            return pathConfig;
        }

        public String getName() {
            return name;
        }

        public String getExtension() {
            return extension;
        }

        public String getMainFunction() {
            return mainFunction;
        }

        public String getMainModule() {
            return mainModule;
        }
    }
}
