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

import java.io.IOException;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.ISourceLocation;

public class PathConfigsTest {

    private static final URIResolverRegistry reg = URIResolverRegistry.getInstance();
    private static ISourceLocation absoluteProjectDir;
    @Mock PathConfigDiagnostics diagnostics;
    private PathConfigs configs;

    @BeforeClass
    public static void initTests() throws IOException {
        absoluteProjectDir = reg.logicalToPhysical(URIUtil.rootLocation("cwd"));
    }

    @Before
    public void setUp() {
        configs = new PathConfigs(Executors.newCachedThreadPool(), diagnostics);
    }

    private static void assertEquals(String message, ISourceLocation expected, ISourceLocation actual) {
        Assert.assertEquals(message, URIUtil.getChildLocation(expected, ""), URIUtil.getChildLocation(actual, ""));
    }

    @Test
    public void pathConfigForLsp() {
        var pcfg = configs.lookupConfig(absoluteProjectDir);
        assertEquals("Path config root should equal project URI", absoluteProjectDir, pcfg.getProjectRoot());
    }

    @Test
    public void pathConfigForLspModule() {
        var pcfg = configs.lookupConfig(URIUtil.getChildLocation(absoluteProjectDir, "src/main/rascal/library/util/LanguageServer.rsc"));
        assertEquals("Path config root should equal project URI", absoluteProjectDir, pcfg.getProjectRoot());
    }
}
