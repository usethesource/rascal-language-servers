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
package org.rascalmpl.vscode.lsp.rascal.model;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.Before;
import org.junit.Test;
import org.rascalmpl.library.Messages;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public class PathConfigDiagnosticsTest {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();

    private static final String PROJECT_A = "project-a";
    private static final String PROJECT_B = "project-b";
    private static final String POM_A = "project-a/pom.xml";
    private static final String POM_B = "project-a/pom.xml";

    private static final String MSG1 = "Hello World 1!";
    private static final String MSG2 = "Hello World 2!";

    private LanguageClient mockedClient;
    private PathConfigDiagnostics sut;
    private ColumnMaps columnMaps;

    private final ISourceLocation projectA;
    private final ISourceLocation projectB;

    private final String pomAUrl;
    private final String pomBUrl;

    public PathConfigDiagnosticsTest() {
        projectA = resourceLocation(PROJECT_A);
        projectB = resourceLocation(PROJECT_B);

        pomAUrl = resourceLocation(POM_A).getURI().toString();
        pomBUrl = resourceLocation(POM_A).getURI().toString();
    }

    @Before
    public void before() throws URISyntaxException {
        mockedClient = mock(LanguageClient.class);
        columnMaps = new ColumnMaps(this::getContents);
        sut = new PathConfigDiagnostics(mockedClient, columnMaps);
    }

    private URI resourceUri(String res) {
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            return classLoader.getResource(res).toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private ISourceLocation resourceLocation(String res) {
        return VF.sourceLocation(resourceUri(res));
    }

    private ISourceLocation resourceLocation(String res, int line, int col) {
        ISourceLocation loc = resourceLocation(res);
        int offset = columnMaps.get(loc).calculateInverseOffsetLength(line, col, line, col).getLeft();
        return VF.sourceLocation(loc, offset, 1, line, line, col, col);
    }

    private String getContents(ISourceLocation file) {
        try (Reader src = URIResolverRegistry.getInstance().getCharacterReader(file)) {
            return Prelude.consumeInputStream(src);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private IConstructor msg(String file, String msg, int line, int col) {
        return Messages.info(msg, resourceLocation(file, line, col));
    }

    private Diagnostic diag(int line, int col, String msg) {
        var diagnostic = new Diagnostic(new Range(new Position(line, col), new Position(line, col)), msg);
        diagnostic.setSeverity(DiagnosticSeverity.Information);
        return diagnostic;
    }

    private PublishDiagnosticsParams params(String url, Diagnostic... diags) {
        return new PublishDiagnosticsParams(url, Arrays.asList(diags));
    }

    @Test
    public void testPublishFresh() {
        sut.publishDiagnostics(projectA, VF.list(msg(POM_A, MSG1, 4, 7)));
        verify(mockedClient).publishDiagnostics(params(pomAUrl, diag(3, 7, MSG1)));
    }

    @Test
    public void testPublishMultiProject() {
        sut.publishDiagnostics(projectA, VF.list(msg(POM_A, MSG1, 4, 7)));
        // Expect the diagnostics for project A
        verify(mockedClient).publishDiagnostics(params(pomAUrl, diag(3, 7, MSG1)));

        sut.publishDiagnostics(projectB, VF.list(msg(POM_A, MSG2, 5, 7)));
        // Expect the diagnostics for both projects
        verify(mockedClient).publishDiagnostics(params(pomAUrl, diag(3, 7, MSG1), diag(4, 7, MSG2)));
    }

    @Test
    public void testDeleteOursButRetainTheirs() {
        sut.publishDiagnostics(projectA, VF.list(msg(POM_A, MSG1, 4, 7)));
        verify(mockedClient).publishDiagnostics(params(pomAUrl, diag(3, 7, MSG1)));

        sut.publishDiagnostics(projectB, VF.list(msg(POM_A, MSG2, 5, 7)));
        verify(mockedClient).publishDiagnostics(params(pomAUrl, diag(3, 7, MSG1), diag(4, 7, MSG2)));

        reset(mockedClient);
        sut.publishDiagnostics(projectA, VF.list());
        verify(mockedClient).publishDiagnostics(params(pomAUrl, diag(4, 7, MSG2)));
    }

    @Test
    public void testClearDiagnostics() {
        sut.publishDiagnostics(projectA, VF.list(msg(POM_A, MSG1, 4, 7)));
        sut.publishDiagnostics(projectB, VF.list(msg(POM_A, MSG2, 5, 7)));

        reset(mockedClient);
        sut.clearDiagnostics(projectA);
        verify(mockedClient).publishDiagnostics(params(pomAUrl, diag(4, 7, MSG2)));
    }

    @Test
    public void testDontPublishUnchangedDiags() {
        sut.publishDiagnostics(projectA, VF.list(msg(POM_A, MSG1, 4, 7)));
        reset(mockedClient);
        sut.publishDiagnostics(projectA, VF.list(msg(POM_A, MSG1, 4, 7)));
        verify(mockedClient, never()).publishDiagnostics(any());
    }

    @Test
    public void testMultiFileDiags() {
        sut.publishDiagnostics(projectA, VF.list(msg(POM_A, MSG1, 4, 7)));
        sut.publishDiagnostics(projectA, VF.list(msg(POM_B, MSG2, 5, 7)));
        reset(mockedClient);
        sut.publishDiagnostics(projectA, VF.list(msg(POM_B, MSG1, 5, 7)));
        verify(mockedClient).publishDiagnostics(params(pomBUrl, diag(4, 7, MSG1)));
    }

}
