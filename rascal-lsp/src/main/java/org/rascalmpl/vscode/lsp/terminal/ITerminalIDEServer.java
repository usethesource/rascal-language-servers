/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.values.IRascalValueFactory;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
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
    default CompletableFuture<ShowDocumentResult> edit(ShowDocumentParams edit)  {
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

    @JsonRequest("rascal/receiveUnregisterLanguage")
    default CompletableFuture<Void> receiveUnregisterLanguage(LanguageParameter lang) {
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
    default CompletableFuture<AmountOfWork> jobEnd(JobEndParameter param) {
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

    @JsonNotification("rascal/registerLocations")
    default void registerLocations(RegisterLocationsParameters param) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/registerDiagnostics")
    default void registerDiagnostics(RegisterDiagnosticsParameters param) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/unregisterDiagnostics")
    default void unregisterDiagnostics(UnRegisterDiagnosticsParameters param) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/startDebuggingSession")
    default void startDebuggingSession(int serverPort) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/registerDebugServerPort")
    default void registerDebugServerPort(int processID, int serverPort) {
        throw new UnsupportedOperationException();
    }

    public static class UnRegisterDiagnosticsParameters {
        private String locations;

        public UnRegisterDiagnosticsParameters(IList locs) {
            this.locations = value2string(locs);
        }

        public IList getLocations() {
            return (IList) string2value(locations);
        }
    }
    public static class RegisterDiagnosticsParameters {
        private String messages;

        public RegisterDiagnosticsParameters(IList messages) {
            this.messages = value2string(messages);
        }

        public IList getMessages() {
            return (IList) string2value(messages);
        }
    }

    public static class RegisterLocationsParameters {
        private final String scheme;
        private final String authority;
        private final String mapping;

        public RegisterLocationsParameters(IString scheme, IString authority, IMap mapping) {
            this.scheme = scheme.getValue();
            this.authority = authority.getValue();
            this.mapping = value2string(mapping);
        }

        public IString getScheme() {
            return IRascalValueFactory.getInstance().string(scheme);
        }

        public IString getAuthority() {
            return IRascalValueFactory.getInstance().string(authority);
        }

        public IMap getMapping() {
            return (IMap) string2value(mapping);
        }
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
            return buildLocation(location);
        }

    }

    private static ISourceLocation buildLocation(String location) throws FactTypeUseException {
        try {
            return (ISourceLocation) new StandardTextReader().read(IRascalValueFactory.getInstance(), TypeFactory.getInstance().sourceLocationType(), new StringReader(location));
        } catch (IOException e) {
            throw new RuntimeException("this should never happen:", e);
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
        private final String message;
        private final int inc;

        public JobStepParameter(String name, String message, int inc) {
            this.name = name;
            this.message = message;
            this.inc = inc;
        }

        public String getName() {
            return name;
        }

        public String getMessage() {
            return message;
        }

        public int getInc() {
            return inc;
        }
    }

    public static class JobEndParameter {
        private final String name;
        private final boolean success;

        public JobEndParameter(String name, boolean total) {
            this.name = name;
            this.success = total;
        }

        public String getName() {
            return name;
        }

        public boolean getSuccess() {
            return success;
        }
    }

    public static String value2string(IValue value) {
        final Encoder encoder = Base64.getEncoder();
        ByteArrayOutputStream stream = new ByteArrayOutputStream(512);

        try (IValueOutputStream out = new IValueOutputStream(stream, IRascalValueFactory.getInstance());) {
            out.write(value);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return encoder.encodeToString(stream.toByteArray());
    }

    public static IValue string2value(String string) {
        final Decoder decoder = Base64.getDecoder();

        try (
            ByteArrayInputStream stream = new ByteArrayInputStream(decoder.decode(string));
            IValueInputStream in = new IValueInputStream(stream, IRascalValueFactory.getInstance(), () -> new TypeStore());
        ) {
            return in.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class DocumentEditsParameter {

        private String edits;

        public DocumentEditsParameter(IList edits) {
            this.edits = value2string(edits);
        }

        public IList getEdits() {
            return (IList) string2value(edits);
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

    public static class JobCanceledParameter {
        private String jobName;

        public JobCanceledParameter(String jobName) {
            this.jobName = jobName;
        }

        public String getModule() {
            return jobName;
        }
    }

    public static class BrowseParameter {
        private String uri;
        private String title;
        private int viewColumn;

        public BrowseParameter(String uri, String title, int viewColumn) {
            this.uri = uri;
            this.title = title;
            this.viewColumn = viewColumn;
        }

        public String getUri() {
            return uri;
        }

        public String getTitle() {
            return title;
        }

        public int getViewColumn() {
            return viewColumn;
        }

        @Override
        public String toString() {
            return "BrowseParameter:\n\tbrowseParameter:" + uri + "\n\ttitle: " + title + "\n\tviewColumn: " + viewColumn;
        }
    }

    public static class SourceLocationParameter {
        private String scheme;
        private String authority;
        private String path;
        private @Nullable String query;
        private @Nullable String fragment;

        public SourceLocationParameter(ISourceLocation loc) {
            this.scheme = loc.getScheme();
            this.authority = loc.getAuthority();
            this.path = loc.getPath();
            this.query = loc.hasQuery() ? null : loc.getQuery();
            this.fragment = loc.hasFragment() ? null : loc.getFragment();
        }

        public ISourceLocation getLocation() {
            try {
                return IRascalValueFactory.getInstance().sourceLocation(scheme, authority, path, query, fragment);
            } catch (URISyntaxException e) {
                // this should really never happen
                assert false;
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "SourceLocationParameter [scheme=" + scheme + ", authority=" + authority + ", path=" + path
                + ", query=" + query + ", fragment=" + fragment + "]";
        }
    }
    public static class LanguageParameter {
        private final String pathConfig;
	    private final String name; // name of the language
	    private final String[] extensions; // extension for files in this language
	    private final String mainModule; // main module to locate mainFunction in
	    private final String mainFunction; // main function which contributes the language implementation
        private final @Nullable ParserSpecification precompiledParser;

        public LanguageParameter(String pathConfig, String name, String[] extensions, String mainModule, String mainFunction, @Nullable ParserSpecification precompiledParser) {
            this.pathConfig = pathConfig.toString();
            this.name = name;
            this.extensions = extensions;
            this.mainModule = mainModule;
            this.mainFunction = mainFunction;
            this.precompiledParser = precompiledParser;
        }

        public String getPathConfig() {
            return pathConfig;
        }

        public String getName() {
            return name;
        }

        public String[] getExtensions() {
            return extensions;
        }

        public String getMainFunction() {
            return mainFunction;
        }

        public String getMainModule() {
            return mainModule;
        }

        public @MonotonicNonNull ParserSpecification getPrecompiledParser() {
            return precompiledParser;
        }

        @Override
        public String toString() {
            return "LanguageParameter(pathConfig=" + pathConfig + ", name=" + name + ", extensions=" + extensions
                + ", mainModule=" + mainModule + ", mainFunction=" + mainFunction + ", precompiledParser=" + precompiledParser + ")";
        }

        public static LanguageParameter fromRascalValue(IConstructor language) {
            return new LanguageParameter(
                language.get(0).toString(),
                getString(language, 1),
                getArray(language, 2),
                getString(language, 3),
                getString(language, 4),
                null
            );
        }
        private static String[] getArray(IConstructor language, int position) {
            return ((ISet) language.get(position))
                .stream()
                .map(v -> ((IString)v).getValue())
                .toArray(String[]::new);
        }

        private static String getString(IConstructor language, int position) {
            return ((IString) language.get(position)).getValue();
        }
    }

    public static class ParserSpecification {
        /** absolute path to the file containing the precompiled parsers */
        private final String parserLocation;
        /** terminal to use from the defined parsers */
        private final String nonTerminalName;
        /** is the terminal a `start` terminal, default: true */
        private final @Nullable Boolean nonTerminalIsStart;
        /** allowAmbiguity (default is false) */
        private final @Nullable Boolean allowAmbiguity;


        public ParserSpecification(String parserLocation, String nonTerminalName, @Nullable Boolean nonTerminalIsStart, @Nullable Boolean allowAmbiguity) {
            this.parserLocation = parserLocation;
            this.nonTerminalName = nonTerminalName;
            this.nonTerminalIsStart = nonTerminalIsStart;
            this.allowAmbiguity = allowAmbiguity;
        }

        public ISourceLocation getParserLocation() throws FactTypeUseException {
            return buildLocation(parserLocation);
        }

        public String getNonTerminalName() {
            return nonTerminalName;
        }

        public boolean getNonTerminalIsStart() {
            return nonTerminalIsStart == null || nonTerminalIsStart;
        }

        public boolean getAllowAmbiguity() {
            return allowAmbiguity != null && allowAmbiguity;
        }

        @Override
        public String toString() {
            return "ParserSpecification [parserLocation=" + parserLocation + ", nonTerminalName=" + nonTerminalName
                + ", nonTerminalIsStart=" + nonTerminalIsStart + ", allowAmbiguity=" + allowAmbiguity + "]";
        }

    }
}
