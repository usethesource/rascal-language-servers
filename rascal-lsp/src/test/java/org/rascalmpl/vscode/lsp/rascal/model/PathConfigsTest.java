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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.ISourceLocation;

public class PathConfigsTest {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private static final URIResolverRegistry reg = URIResolverRegistry.getInstance();

    private final Projects projects = new Projects();

    /**
     * Test {@link Projects::inferRoot} for a specific module within a project.
     * @param project The project that contains the module. This is the expected value for the inferred root.
     * @param modulePath The relative path of the module within the project. Does not need to actually exist.
     * @param projectExists Whether the project actually exists. WARNING: If it does not exist, root inference probably returns the root of the file system of the project.
     */
    private void checkRoot(ISourceLocation project, String modulePath, boolean projectExists, boolean moduleExists) {
        assertFalse("Cannot check for existing module in non-existent project", !projectExists && moduleExists);
        assertTrue("Project should exist", !projectExists || reg.exists(project));
        var m = URIUtil.getChildLocation(project, modulePath);
        assertTrue("Module should exist", !moduleExists || reg.exists(m));
        var root = projects.inferRoot(m);
        assertEquals("Inferred root should equal project URI", project, root);
    }

    private void checkRoot(ISourceLocation project, String modulePath) {
        checkRoot(project, modulePath, true, true);
    }

    @Mock PathConfigDiagnostics diagnostics;
    private PathConfigs configs;
    private static ISourceLocation absoluteProjectDir;

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
    public void lspRoot() {
        checkRoot(absoluteProjectDir, "src/main/rascal/library/util/LanguageServer.rsc");
    }

    @Test
    public void lspTargetRoot() {
        checkRoot(absoluteProjectDir, "target/classes/library/util/LanguageServer.rsc");
    }

    @Test
    public void nestedRoot() {
        checkRoot(URIUtil.getChildLocation(absoluteProjectDir, "src/test/resources/project-a"), "src/Module.rsc", true, false);
    }

    @Test
    public void projectRoot() throws URISyntaxException {
        checkRoot(VF.sourceLocation("project", "rascal-lsp", ""), "src/main/rascal/library/util/LanguageServer.rsc", false, false);
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
