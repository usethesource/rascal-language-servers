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

/**
 * Tools for projects, like path computations. Non-static functions so they can be used in Rascal via `@javaClass` as well.
 */
public class Projects {

    /**
     * Infers the shallowest possible root of the project that `origin` is in.
     */
    public ISourceLocation inferRoot(ISourceLocation origin) {
        origin = origin.top();
        var innerRoot = inferDeepestRoot(origin);
        var outerRoot = inferDeepestRoot(URIUtil.getParentLocation(innerRoot));

        if (!innerRoot.equals(outerRoot) && isSameProject(innerRoot, outerRoot)) {
            // The roots are not equal, but refer to the same project: the inner root is somewhere inside the target folder.
            // In that case, we need the outer root
            return outerRoot;
        }

        // (innerRoot.equals(outerRoot) || !isSameProject(innerRoot, outerRoot))
        // Inner is a nested project within outer; we want the root of the nested project.
        return innerRoot;
    }

    private boolean isSameProject(ISourceLocation root1, ISourceLocation root2) {
        var mf = new RascalManifest();
        return mf.hasManifest(root1) && mf.getProjectName(root1).equals(mf.getProjectName(root2));
    }

    /**
     * Infers the longest project root-like path that `member` is in. Might return a sub-directory of `target/`.
     */
    private ISourceLocation inferDeepestRoot(ISourceLocation origin) {
        var root = origin;
        while (!new RascalManifest().hasManifest(root)) {
            if (root.getPath().equals(URIUtil.URI_PATH_SEPARATOR)) {
                // File system root; cannot recurse further
                break;
            }
            root = URIUtil.getParentLocation(root);
        }
        return root;
    }

}
