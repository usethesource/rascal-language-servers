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
package org.rascalmpl.vscode.lsp.rascal;

import static org.rascalmpl.vscode.lsp.util.EvaluatorUtil.makeFutureEvaluator;
import static org.rascalmpl.vscode.lsp.util.EvaluatorUtil.runEvaluator;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.library.util.ParseErrorRecovery;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.types.RascalTypeFactory;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.RascalLSPMonitor;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.util.EvaluatorUtil;
import org.rascalmpl.vscode.lsp.util.EvaluatorUtil.LSPContext;
import org.rascalmpl.vscode.lsp.util.RascalServices;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.io.StandardTextReader;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class RascalLanguageServices {
    private static final IValueFactory VF = IRascalValueFactory.getInstance();
    private static final ParseErrorRecovery RECOVERY = new ParseErrorRecovery(IRascalValueFactory.getInstance());

    private static final Logger logger = LogManager.getLogger(RascalLanguageServices.class);

    private final CompletableFuture<Evaluator> documentSymbolEvaluator;
    private final CompletableFuture<Evaluator> semanticEvaluator;
    private final CompletableFuture<Evaluator> compilerEvaluator;

    private final CompletableFuture<TypeStore> actionStore;

    private final TypeFactory tf = TypeFactory.getInstance();
    private final TypeStore store = new TypeStore();
    private final Type getPathConfigType = tf.functionType(tf.abstractDataType(store, "PathConfig"), tf.tupleType(tf.sourceLocationType()), tf.tupleEmpty());
    private final IConstructor startModuleConstructor = VF.constructor(RascalValueFactory.Symbol_Start, VF.constructor(RascalValueFactory.Symbol_Sort, VF.string("Module")));
    private final Type startModuleType = RascalTypeFactory.getInstance().nonTerminalType(startModuleConstructor);
    private final Type getParseTreeType = tf.functionType(startModuleType, tf.tupleType(tf.sourceLocationType()), tf.tupleEmpty());

    private final ExecutorService exec;

    private final IBaseLanguageClient client;
    private final RascalTextDocumentService rascalTextDocumentService;
    private final BaseWorkspaceService workspaceService;

    public RascalLanguageServices(RascalTextDocumentService docService, BaseWorkspaceService workspaceService, IBaseLanguageClient client, ExecutorService exec) {
        this.client = client;
        this.exec = exec;

        var monitor = new RascalLSPMonitor(client, logger);

        var pcfg = EvaluatorUtil.addLSPSources(new PathConfig(URIUtil.rootLocation("cwd")), true);
        var compilerPcfg = EvaluatorUtil.addRascalCompilerSources(pcfg);

        var context = new LSPContext(exec, docService, workspaceService, client);

        documentSymbolEvaluator = makeFutureEvaluator(context, "Rascal document symbols", monitor, pcfg,  "lang::rascal::lsp::DocumentSymbols");
        semanticEvaluator = makeFutureEvaluator(context, "Rascal semantics", monitor, compilerPcfg, "lang::rascalcore::check::Summary", "lang::rascal::lsp::refactor::Rename", "lang::rascal::lsp::Actions");
        compilerEvaluator = makeFutureEvaluator(context, "Rascal compiler", monitor, compilerPcfg, "lang::rascal::lsp::IDECheckerWrapper");
        actionStore = semanticEvaluator.thenApply(e -> ((ModuleEnvironment) e.getModule("lang::rascal::lsp::Actions")).getStore());
        rascalTextDocumentService = docService;
        this.workspaceService = workspaceService;
    }

    public InterruptibleFuture<@Nullable IConstructor> getSummary(ISourceLocation occ, PathConfig pcfg) {
        try {
            IString moduleName = VF.string(pcfg.getModuleName(occ));
            return runEvaluator("Rascal makeSummary", semanticEvaluator, eval -> {
                IConstructor result = (IConstructor) eval.call("makeSummary", moduleName, pcfg.asConstructor());
                return result != null && result.asWithKeywordParameters().hasParameters() ? result : null;
            }, null, exec, false, client);
        } catch (IOException e) {
            logger.error("Error looking up module name from source location {}", occ, e);
            return new InterruptibleFuture<>(CompletableFuture.completedFuture(null), () -> {
            });
        }
    }

    public InterruptibleFuture<Map<ISourceLocation, ISet>> compileFolder(ISourceLocation folder, PathConfig pcfg,
        Executor exec) {
        return runEvaluator("Rascal checkAll", compilerEvaluator,
            e -> {
                var config = e.call("getRascalCoreCompilerConfig", pcfg.asConstructor());
                return translateCheckResults((ISet) e.call("checkAll", folder, config));
            },
            Collections.emptyMap(), exec, false, client);
    }

    private static Map<ISourceLocation, ISet> translateCheckResults(ISet messages) {
        logger.trace("Translating messages: {}", messages);
        return messages.stream()
            .filter(IConstructor.class::isInstance)
            .map(IConstructor.class::cast)
            .collect(Collectors.toMap(
                c -> (ISourceLocation) c.get("src"),
                c -> (ISet) c.get("messages")));
    }

    IFunction makePathConfigGetter(Evaluator e) {
        return e.getFunctionValueFactory().function(getPathConfigType, (t, u) -> {
            return rascalTextDocumentService.getFileFacts().getPathConfig((ISourceLocation) t[0]).asConstructor();
        });
    }

    IFunction makeParseTreeGetter(Evaluator e) {
        return e.getFunctionValueFactory().function(getParseTreeType, (t, u) -> {
            ISourceLocation resolvedLocation = Locations.toClientLocation((ISourceLocation) t[0]);
            try {
                var tree = rascalTextDocumentService.getFile(resolvedLocation).getLastTreeWithoutErrors();
                if (tree != null) {
                    return tree.get();
                }
            } catch (ResponseErrorException e1) {
                // File is not open in the IDE
            }
            // Parse the source file
            try (var reader = URIResolverRegistry.getInstance().getCharacterReader(resolvedLocation)) {
                return RascalServices.parseRascalModule(resolvedLocation, Prelude.consumeInputStream(reader).toCharArray());
            } catch (IOException e1) {
                throw RuntimeExceptionFactory.io("Could not open " + t[0] + " for reading");
            } catch (ParseError pe) {
                throw RuntimeExceptionFactory.parseError(pe.getLocation());
            }
        });
    }

    public InterruptibleFuture<Map<ISourceLocation, ISet>> compileFile(ISourceLocation file, PathConfig pcfg,
        Executor exec) {
        logger.debug("Running Rascal check for: {} with {}", file, pcfg);
        var workspaceFolders = workspaceService.workspaceFolders().stream().map(f -> Locations.toLoc(f.getUri())).collect(VF.setWriter());

        return runEvaluator("Rascal check", compilerEvaluator,
            e -> translateCheckResults((ISet) e.call("checkFile", file, workspaceFolders, makeParseTreeGetter(e), makePathConfigGetter(e))),
            buildEmptyResult(VF.list(file)), exec, false, client);
    }

    private Map<ISourceLocation, ISet> buildEmptyResult(IList files) {
        return files.stream()
            .map(ISourceLocation.class::cast)
            .collect(Collectors.toMap(k -> k, k -> VF.set()));
    }

    private ISourceLocation getFileLoc(ITree moduleTree) {
        try {
            if (TreeAdapter.isTop(moduleTree)) {
                moduleTree = TreeAdapter.getStartTop(moduleTree);
            }
            ISourceLocation loc = TreeAdapter.getLocation(moduleTree);
            if (loc != null) {
                return loc.top();
            }
            return null;
        } catch (Exception t) {
            logger.trace("Failure to get file loc from tree: {}", moduleTree, t);
            return null;
        }
    }


    public InterruptibleFuture<IList> getDocumentSymbols(IConstructor module) {
        ISourceLocation loc = getFileLoc((ITree) module);
        if (loc == null) {
            return new InterruptibleFuture<>(CompletableFuture.completedFuture(VF.list()), () -> {
            });
        }

        return runEvaluator("Rascal Document Symbols", documentSymbolEvaluator, eval -> (IList) eval.call("documentRascalSymbols", module),
            VF.list(), exec, false, client);
    }


    public InterruptibleFuture<ITuple> getRename(ISourceLocation cursorLoc, IList focus, Set<ISourceLocation> workspaceFolders, Function<ISourceLocation, PathConfig> getPathConfig, String newName) {
        return runEvaluator("Rascal rename", semanticEvaluator, eval -> {
            try {
                IFunction rascalGetPathConfig = eval.getFunctionValueFactory().function(getPathConfigType, (t, u) -> getPathConfig.apply((ISourceLocation) t[0]).asConstructor());
                return (ITuple) eval.call("rascalRenameSymbol", cursorLoc, focus, VF.string(newName), workspaceFolders.stream().collect(VF.setWriter()), rascalGetPathConfig);
            } catch (Throw e) {
                if (e.getException() instanceof IConstructor) {
                    var exception = (IConstructor)e.getException();
                    if (exception.getType().getAbstractDataType().getName().equals("RenameException")) {
                        // instead of the generic exception handler, we deal with these ourselfs
                        // and report an LSP error, such that the IDE shows them in a user friendly way
                        String message;
                        if (exception.has("message")) {
                            message = ((IString)exception.get("message")).getValue();
                        }
                        else {
                            message = "Rename failed: " + exception.getName();
                        }
                        throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, message, null));
                    }
                }
                throw e;
            }
        }, VF.tuple(VF.list(), VF.map()), exec, false, client);
    }

    private ISourceLocation sourceLocationFromUri(String uri) {
        try {
            return URIUtil.createFromURI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<ITuple> getModuleRenames(List<FileRename> fileRenames, Set<ISourceLocation> workspaceFolders, Function<ISourceLocation, PathConfig> getPathConfig, Map<ISourceLocation, TextDocumentState> documents) {
        var emptyResult = VF.tuple(VF.list(), VF.map());
        if (fileRenames.isEmpty()) {
            return CompletableFuture.completedFuture(emptyResult);
        }

        return CompletableFuture.supplyAsync(() -> fileRenames.stream()
                .map(r -> VF.tuple(sourceLocationFromUri(r.getOldUri()), sourceLocationFromUri(r.getNewUri())))
                .collect(VF.listWriter())
            , exec)
            .thenCompose(renames -> {
                return runEvaluator("Rascal module rename", semanticEvaluator, eval -> {
                    IFunction rascalGetPathConfig = eval.getFunctionValueFactory().function(getPathConfigType, (t, u) -> getPathConfig.apply((ISourceLocation) t[0]).asConstructor());
                    try {
                        return (ITuple) eval.call("rascalRenameModule", renames, workspaceFolders.stream().collect(VF.setWriter()), rascalGetPathConfig);
                    } catch (Throw e) {
                        throw new RuntimeException(e.getMessage());
                    }
                }, emptyResult, exec, false, client).get();
            });
    }


    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        return CompletableFuture.supplyAsync(() -> RascalServices.parseRascalModule(loc, input.toCharArray()), exec);
    }

    public List<CodeLensSuggestion> locateCodeLenses(ITree tree) {
        tree = TreeAdapter.getStartTop(tree);
        ITree module = TreeAdapter.getArg(TreeAdapter.getArg(tree, "header"), "name");
        String moduleName = TreeAdapter.yield(module);
        List<CodeLensSuggestion> result = new ArrayList<>(2);
        result.add(new CodeLensSuggestion(module, "Import in new Rascal terminal", "rascalmpl.importModule", moduleName));

        ITree body = TreeAdapter.getArg(tree, "body");
        ITree toplevels = TreeAdapter.getArg(body, "toplevels");
        for (IValue topLevel : TreeAdapter.getListASTArgs(toplevels)) {
            try {
                ITree decl = TreeAdapter.getArg((ITree) topLevel, "declaration");
                if ("function".equals(TreeAdapter.getConstructorName(decl))) {
                    ITree signature = TreeAdapter.getArg(TreeAdapter.getArg(decl, "functionDeclaration"), "signature");
                    ITree name = TreeAdapter.getArg(signature, "name");
                    if ("main".equals(TreeAdapter.yield(name))) {
                        result.add(new CodeLensSuggestion(name, "Run in new Rascal terminal", "rascalmpl.runMain", moduleName));
                    }
                }
            } catch (Exception e) {
                // We must have encountered an error tree. This exception can be more specific once a Rascal version with a more robust version of "TreeAdapter.getArg()" is released.
                logger.warn("Failed to process top-level tree, it probably contains a parse error: {}", topLevel, e);
            }
        }
        return result;
    }

    public CompletableFuture<IList> parseCodeActions(String command) {
        return actionStore.thenApply(commandStore -> {
            try {
                var TF = TypeFactory.getInstance();
                return (IList) new StandardTextReader().read(VF, commandStore, TF.listType(commandStore.lookupAbstractDataType("CodeAction")), new StringReader(command));
            } catch (FactTypeUseException | IOException e) {
                // this should never happen as long as the Rascal code
                // for creating errors is type-correct. So it _might_ happen
                // when running the interpreter on broken code.
                throw new IllegalArgumentException("The command could not be parsed", e);
            }
        });
    }

    public InterruptibleFuture<IValue> executeCommand(String command) {
        logger.debug("executeCommand({}...) (full command value in TRACE level)", () -> command.substring(0, Math.min(10, command.length())));
        logger.trace("Full command: {}", command);
        var defaultMap = VF.mapWriter();
        defaultMap.put(VF.string("result"), VF.bool(false));

        return InterruptibleFuture.flatten(parseCommand(command).thenApply(cons ->
            EvaluatorUtil.<IValue>runEvaluator(
                "executeCommand",
                semanticEvaluator,
                ev -> ev.call("evaluateRascalCommand", cons),
                defaultMap.done(),
                exec,
                true,
                client
            )
        ), exec);
    }

    private CompletableFuture<IConstructor> parseCommand(String command) {
        return actionStore.thenApply(commandStore -> {
            try {
                return (IConstructor) new StandardTextReader().read(VF, commandStore, commandStore.lookupAbstractDataType("Command"), new StringReader(command));
            }
            catch (FactTypeUseException | IOException e) {
                logger.catching(e);
                throw new IllegalArgumentException("The command could not be parsed", e);
            }
        });
    }

    public static final class CodeLensSuggestion {
        private final ISourceLocation line;
        private final String commandName;
        private final List<Object> arguments;
        private final String shortName;

        public CodeLensSuggestion(ITree line, String shortName, String commandName, Object... arguments) {
            this.line = TreeAdapter.getLocation(line);
            this.arguments = Arrays.asList(arguments);
            this.commandName = commandName;
            this.shortName = shortName;
        }


        public List<Object> getArguments() {
            return arguments;
        }

        public ISourceLocation getLine() {
            return line;
        }

        public String getCommandName() {
            return commandName;
        }


        public String getShortName() {
            return shortName;
        }
    }

    public InterruptibleFuture<IList> codeActions(IList focus, PathConfig pcfg) {
        return runEvaluator("Rascal codeActions", semanticEvaluator, eval -> {
            Map<String,IValue> kws = Map.of("pcfg", pcfg.asConstructor());
            return (IList) eval.call("rascalCodeActions", "lang::rascal::lsp::Actions", kws, focus);
        },
        VF.list(), exec, false, client);
    }
}
