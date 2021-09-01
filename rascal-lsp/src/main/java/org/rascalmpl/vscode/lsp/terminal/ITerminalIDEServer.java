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
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.concurrent.CompletableFuture;

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

    @JsonRequest("rascal/receiveRegisterLanguage")
    default CompletableFuture<Void> receiveRegisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/applyDocumentEdits")
    default CompletableFuture<Void> applyDocumentEdits(DocumentEditsParameter edits) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/showHTML")
    default void showHTML(BrowseParameter content) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/jobStart")
    default CompletableFuture<Void>  jobStart(JobStartParameter param) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/jobStep")
    default CompletableFuture<Void>  jobStep(JobStepParameter param) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/jobEnd")
    default CompletableFuture<AmountOfWork> jobEnd(BooleanParameter param) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/jobIsCanceled")
    default CompletableFuture<BooleanParameter> jobIsCanceled() {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/jobTodo")
    default CompletableFuture<Void> jobTodo(AmountOfWork param) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/warning")
    default void warning(WarningMessage param) {
        throw new UnsupportedOperationException();
    }

    public static class WarningMessage {
        private final String location;
        private final String message;

        public WarningMessage(String message, ISourceLocation src) {
            this.message = message;
            this.location = src.toString();
        }

        public String getMessage() {
            return message;
        }

        public ISourceLocation getLocation() {
            try {
                return (ISourceLocation) new StandardTextReader().read(IRascalValueFactory.getInstance(), TypeFactory.getInstance().sourceLocationType(), new StringReader(location));
            } catch (FactTypeUseException | IOException e) {
                throw new RuntimeException("this should never happen:", e);
            }
        }
    }
    public static class AmountOfWork {
        private final int amount;

        public AmountOfWork(int amount) {
            this.amount = amount;
        }

        public int getAmount() {
            return amount;
        }
    }

    public static class BooleanParameter {
        private final boolean truth;

        public BooleanParameter(boolean s) {
            this.truth = s;
        }

        public boolean isTrue() {
            return truth;
        }
    }
    public static class JobStartParameter {
        private final String name;
        private final int workShare;
        private final int totalWork;

        public JobStartParameter(String name, int workShare, int totalWork) {
            this.name = name;
            this.workShare = workShare;
            this.totalWork = totalWork;
        }

        public String getName() {
            return name;
        }

        public int getTotalWork() {
            return totalWork;
        }

        public int getWorkShare() {
            return workShare;
        }
    }

    public static class JobStepParameter {
        private final String name;
        private final int inc;

        public JobStepParameter(String name, int inc) {
            this.name = name;
            this.inc = inc;
        }

        public String getName() {
            return name;
        }

        public int getInc() {
            return inc;
        }
    }

    public static class DocumentEditsParameter {
        private final Decoder decoder = Base64.getDecoder();
        private final Encoder encoder = Base64.getEncoder();
        private String edits;

        public DocumentEditsParameter(IList edits) {
            try (
                ByteArrayOutputStream stream = new ByteArrayOutputStream(512);
                IValueOutputStream out = new IValueOutputStream(stream, IRascalValueFactory.getInstance());
            ) {
                out.write(edits);
                this.edits = new String(encoder.encodeToString(stream.toByteArray()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public IList getEdits() {
            try (
                ByteArrayInputStream stream = new ByteArrayInputStream(decoder.decode(edits));
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

        public LanguageParameter(String pathConfig, String name, String extension, String mainModule, String mainFunction) {
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
