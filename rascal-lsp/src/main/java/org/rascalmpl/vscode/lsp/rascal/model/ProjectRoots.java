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

import org.rascalmpl.interpreter.utils.RascalManifest;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.ISourceLocation;

public class ProjectRoots {

    /**
     * Infers the shallowest possible root of the project that `origin` is in.
     */
    public ISourceLocation inferProjectRoot(ISourceLocation origin) {
        var innerRoot = inferDeepestProjectRoot(origin);
        var outerRoot = inferDeepestProjectRoot(URIUtil.getParentLocation(innerRoot));

        while (!innerRoot.equals(outerRoot) && isSameProject(innerRoot, outerRoot)) {
            innerRoot = outerRoot;
            outerRoot = inferDeepestProjectRoot(URIUtil.getParentLocation(innerRoot));
        }

        return isSameProject(innerRoot, outerRoot)
            ? outerRoot // Inner root is for the same project, but might be inside the target folder
            : innerRoot // Inner root is for a nested project
            ;
    }

    private boolean isSameProject(ISourceLocation root1, ISourceLocation root2) {
        var mf = new RascalManifest();
        return mf.hasManifest(root1) && mf.getProjectName(root1).equals(mf.getProjectName(root2));
    }

    /**
     * Infers the longest project root-like path that `member` is in. Might return a sub-directory of `target/`.
     */
    private ISourceLocation inferDeepestProjectRoot(ISourceLocation origin) {
        var manifest = new RascalManifest();
        var root = origin;

        while (!(manifest.hasManifest(root) || URIUtil.getParentLocation(root).equals(root))) {
            root = URIUtil.getParentLocation(root);
        }
        return root;
    }

}
