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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;
import org.junit.Test;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.ISourceLocation;

public class RascalLanguageServicesTest {

    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();

    private void moduleNameTest(ISourceLocation sourcePath, String modPath, String modName) throws URISyntaxException {
        var actualName = RascalLanguageServices.pathToModuleName(URIUtil.getChildLocation(sourcePath, modPath));
        assertEquals(modName, actualName);
    }

    @Test
    public void stdModuleName() throws URISyntaxException {
        moduleNameTest(URIUtil.rootLocation("std"), "IO.rsc", "IO");
        moduleNameTest(URIUtil.rootLocation("std"), "util/Maybe.rsc", "util::Maybe");
    }

    @Test
    public void mvnModuleName() throws URISyntaxException {
        moduleNameTest(VF.sourceLocation("mvn", "org.rascalmpl--rascal--0.42.2", ""), "String.rsc", "String");
        moduleNameTest(VF.sourceLocation("mvn", "org.rascalmpl--typepal--0.16.6", ""), "analysis/typepal/Collector.rsc", "analysis::typepal::Collector");
    }

    @Test
    public void jarModuleName() throws URISyntaxException {
        moduleNameTest(VF.sourceLocation("jar+file", "", "rascal-lsp.jar!/"), "util/LanguageServer.rsc", "util::LanguageServer");
    }

    @Test
    public void stdTplLoc() throws URISyntaxException {
        var src = VF.sourceLocation("std", "", "util/Maybe.rsc");
        var actualTpl = RascalLanguageServices.libraryTplLocation(src);
        assertEquals("jar+file", actualTpl.getScheme());
        assertTrue(actualTpl.getPath().endsWith(".jar!/rascal/util/$Maybe.tpl"));
    }

    @Test
    public void mvnTplLoc() throws URISyntaxException {
        var src = VF.sourceLocation("mvn", "org.rascalmpl--typepal--0.16.6", "analysis/typepal/Collector.rsc");
        var actualTpl = RascalLanguageServices.libraryTplLocation(src);
        assertEquals(VF.sourceLocation("mvn", "org.rascalmpl--typepal--0.16.6", "rascal/analysis/typepal/$Collector.tpl"), actualTpl);
    }

    @Test
    public void jarFileTplLoc() throws URISyntaxException {
        var src = VF.sourceLocation("jar+file", "", "some/path/to/rascal-lsp.jar!/util/LanguageServer.rsc");
        var actualTpl = RascalLanguageServices.libraryTplLocation(src);
        assertEquals(VF.sourceLocation("jar+file", "", "some/path/to/rascal-lsp.jar!/rascal/util/$LanguageServer.tpl"), actualTpl);
    }

}
