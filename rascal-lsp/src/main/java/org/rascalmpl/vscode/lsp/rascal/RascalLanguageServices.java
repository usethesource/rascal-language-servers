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
package org.rascalmpl.vscode.lsp.rascal;

import static org.rascalmpl.vscode.lsp.util.EvaluatorUtil.makeFutureEvaluator;
import static org.rascalmpl.vscode.lsp.util.EvaluatorUtil.runEvaluator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.library.lang.rascal.syntax.RascalParser;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.parser.Parser;
import org.rascalmpl.parser.gtd.result.action.IActionExecutor;
import org.rascalmpl.parser.gtd.result.out.DefaultNodeFlattener;
import org.rascalmpl.parser.uptr.UPTRNodeFactory;
import org.rascalmpl.parser.uptr.action.NoActionExecutor;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.util.RascalServices;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class RascalLanguageServices {
    private static final IValueFactory VF = IRascalValueFactory.getInstance();

    private static final Logger logger = LogManager.getLogger(RascalLanguageServices.class);

    private final CompletableFuture<Evaluator> outlineEvaluator;
    private final CompletableFuture<Evaluator> summaryEvaluator;
    private final CompletableFuture<Evaluator> compilerEvaluator;

    private final ExecutorService exec;

    public RascalLanguageServices(RascalTextDocumentService docService, BaseWorkspaceService workspaceService, IBaseLanguageClient client, ExecutorService exec) {
        this.exec = exec;

        outlineEvaluator = makeFutureEvaluator(exec, docService, workspaceService, client, "Rascal outline", null, true, "lang::rascal::lsp::Outline");
        summaryEvaluator = makeFutureEvaluator(exec, docService, workspaceService, client, "Rascal summary", null, true, "lang::rascalcore::check::Summary");
        compilerEvaluator = makeFutureEvaluator(exec, docService, workspaceService, client, "Rascal compiler", null, true, "lang::rascalcore::check::Checker");
    }

    public InterruptibleFuture<@Nullable IConstructor> getSummary(ISourceLocation occ, PathConfig pcfg) {
        try {
            IString moduleName = VF.string(pcfg.getModuleName(occ));
            return runEvaluator("Rascal makeSummary", summaryEvaluator, eval -> {
                IConstructor result = (IConstructor) eval.call("makeSummary", moduleName, pcfg.asConstructor());
                return result != null && result.asWithKeywordParameters().hasParameters() ? result : null;
            }, null, exec, false);
        } catch (IOException e) {
            logger.error("Error looking up module name from source location {}", occ, e);
            return new InterruptibleFuture<>(CompletableFuture.completedFuture(null), () -> {
            });
        }
    }

    public InterruptibleFuture<Map<ISourceLocation, ISet>> compileFolder(ISourceLocation folder, PathConfig pcfg,
        Executor exec) {
        return runEvaluator("Rascal checkAll", compilerEvaluator,
            e -> translateCheckResults((IList) e.call("checkAll", folder, pcfg.asConstructor())),
            Collections.emptyMap(), exec, false);
    }

    private static Map<ISourceLocation, ISet> translateCheckResults(IList messages) {
        return messages.stream()
            .filter(IConstructor.class::isInstance)
            .map(IConstructor.class::cast)
            .collect(Collectors.toMap(
                c -> (ISourceLocation) c.get("src"),
                c -> (ISet) c.get("messages")));
    }

    public InterruptibleFuture<Map<ISourceLocation, ISet>> compileFile(ISourceLocation file, PathConfig pcfg,
        Executor exec) {
        return compileFileList(VF.list(file), pcfg, exec);
    }

    private Map<ISourceLocation, ISet> buildEmptyResult(IList files) {
        return files.stream()
            .map(ISourceLocation.class::cast)
            .collect(Collectors.toMap(k -> k, k -> VF.set()));
    }

    public InterruptibleFuture<Map<ISourceLocation, ISet>> compileFileList(IList files, PathConfig pcfg,
        Executor exec) {
        logger.debug("Running rascal check for: {} with: {}", files, pcfg);
        return runEvaluator("Rascal check", compilerEvaluator,
            e -> translateCheckResults((IList) e.call("check", files, pcfg.asConstructor())),
            buildEmptyResult(files), exec, false);
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


    public InterruptibleFuture<IList> getOutline(IConstructor module) {
        ISourceLocation loc = getFileLoc((ITree) module);
        if (loc == null) {
            return new InterruptibleFuture<>(CompletableFuture.completedFuture(VF.list()), () -> {
            });
        }

        return runEvaluator("Rascal outline", outlineEvaluator, eval -> (IList) eval.call("outlineRascalModule", module),
            VF.list(), exec, false);
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

        for (IValue topLevel : TreeAdapter
            .getListASTArgs(TreeAdapter.getArg(TreeAdapter.getArg(tree, "body"), "toplevels"))) {
            ITree decl = TreeAdapter.getArg((ITree) topLevel, "declaration");
            if ("function".equals(TreeAdapter.getConstructorName(decl))) {
                ITree signature = TreeAdapter.getArg(TreeAdapter.getArg(decl, "functionDeclaration"), "signature");
                ITree name = TreeAdapter.getArg(signature, "name");
                if ("main".equals(TreeAdapter.yield(name))) {
                    result.add(new CodeLensSuggestion(name, "Run in new Rascal terminal", "rascalmpl.runMain", moduleName));
                }
            }
        }
        return result;
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
}
