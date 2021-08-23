/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.terminal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

import javax.print.attribute.standard.Compression;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.io.StandardTextReader;
import io.usethesource.vallang.io.binary.stream.IValueInputStream;
import io.usethesource.vallang.io.binary.stream.IValueOutputStream;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

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

    @JsonNotification("rascal/applyDocumentEdits")
    default CompletableFuture<Void> applyDocumentEdits(DocumentEditsParameter edits) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/applyDocumentEdits")
    default CompletableFuture<Void> showHTML(BrowseParameter content) {
        throw new UnsupportedOperationException();
    }

    public static class DocumentEditsParameter {
        private String edits;

        public DocumentEditsParameter(IList edits) {
            try (
                ByteArrayOutputStream stream = new ByteArrayOutputStream(512);
                IValueOutputStream out = new IValueOutputStream(stream, IRascalValueFactory.getInstance());
            ) {
                out.write(edits);
                this.edits = new String(stream.toByteArray(), Charset.forName("UTF8"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public IList getEdits() {
            try (
                ByteArrayInputStream stream = new ByteArrayInputStream(edits.getBytes(Charset.forName("UTF8")));
                IValueInputStream in = new IValueInputStream(stream, IRascalValueFactory.getInstance(), () -> new TypeStore());
            ) {
                return (IList) in.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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
