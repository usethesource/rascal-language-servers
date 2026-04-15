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
package org.rascalmpl.vscode.lsp.parametric;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.Before;
import org.junit.Test;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.EvaluatorUtil;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

public class ParametricLanguageRouterTest {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private final ExecutorService exec = NamedThreadPool.cached("parametric-language-router-test");
    private ParametricLanguageRouter router;
    // private PathConfigs pathConfigs;
    // @Mock private PathConfigDiagnostics pathConfigDiagnostics;

    @Before
    public void setUp() {
        router = new ParametricLanguageRouter(exec);
        // pathConfigs = new PathConfigs(exec, pathConfigDiagnostics);
    }

    private void registerPicoLanguage(boolean errorRecovery) {
        var contribs = List.of(
            errorRecovery ? "picoLanguageServerWithRecovery" : "picoLanguageServer",
            errorRecovery ? "picoLanguageServerSlowSummaryWithRecovery" : "picoLanguageServerSlowSummary"
        );

        for (var contrib : contribs) {
            router.registerLanguage(new LanguageParameter(
                "pathConfig()",
                "Pico",
                new String[] {"pico"},
                "demo::lang::pico::LanguageServer",
                contrib,
                null));
        }
    }

    private void registerSmallPicoLanguage() {
        try {
            var root = VF.sourceLocation("cwd", "", "../examples/pico-dsl-lsp");
            var pcfg = new PathConfig(root);
            pcfg.addSourceLoc(URIUtil.getChildLocation(root, "src/main/rascal"));
            pcfg = EvaluatorUtil.addLSPSources(pcfg, false);
            router.registerLanguage(new LanguageParameter(
                pcfg.asConstructor().toString(),
                "SmallPico",
                new String[] {"smallpico"},
                "lang::pico::LanguageServer",
                "picoContributions",
                null));
        } catch (IOException e) {
            fail("Failed to add source location to path config: " + e);
        } catch (URISyntaxException e) {
            fail("Failed to build project root location: " + e);
        }
    }

    private void picoSanityCheck() {
        ISourceLocation sourceFile;
        try {
            sourceFile = VF.sourceLocation("cwd", "", "src/main/rascal/library/demo/lang/pico/examples/fac.pico");
        } catch (URISyntaxException e) {
            fail("Failed to build source location: " + e);
            return;
        }

        var uri = Locations.toUri(sourceFile).toString();
        var doc = new TextDocumentItem(uri, "parametric-rascalmpl", 1, "");
        var docId = new TextDocumentIdentifier(uri);

        router.didOpen(new DidOpenTextDocumentParams(doc));
        try {
            var tokens = router.semanticTokensFull(new SemanticTokensParams(docId)).get(30, TimeUnit.SECONDS);
            assertTrue(tokens.getData().size() > 0);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Sanity check failed: " + e);
        }
        router.didClose(new DidCloseTextDocumentParams(docId));
    }

    @Test
    public void registerPico() {
        registerPicoLanguage(false);
        picoSanityCheck();
    }

    @Test
    public void registerSmallPico() {
        registerSmallPicoLanguage();
    }

    @Test
    public void registerTwoPicos() {
        registerPicoLanguage(false);
        registerSmallPicoLanguage();
    }
}
