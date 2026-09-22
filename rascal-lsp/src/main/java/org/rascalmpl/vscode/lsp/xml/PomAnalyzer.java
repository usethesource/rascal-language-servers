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
package org.rascalmpl.vscode.lsp.xml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

import org.rascalmpl.util.maven.Artifact;
import org.rascalmpl.util.maven.MavenParser;
import org.rascalmpl.util.maven.ModelResolutionError;
import org.rascalmpl.util.maven.Scope;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValueFactory;

public class PomAnalyzer {
    private IValueFactory vf;
    
    public PomAnalyzer(IValueFactory vf) {
        this.vf = vf;
    }
    
    IBool hasDependency(ISourceLocation pomLoc, String groupId, String artifactId) {
        try {
            var mavenParser = new MavenParser(Path.of(pomLoc.getURI()));
            var rootProject = mavenParser.parseProject();
            var resolvedDependencies = rootProject.resolveDependencies(Scope.COMPILE, mavenParser);
            return vf.bool(resolvedDependencies.stream()
                .map(Artifact::getCoordinate)
                .anyMatch(a -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId)));
        } catch (ModelResolutionError e) {
            return vf.bool(false);
        }
    }
    
    public IBool hasRascalDependency(ISourceLocation pomLoc) {
        return hasDependency(pomLoc, "org.rascalmpl", "rascal");
    }
    
    public IBool hasRascalLspDependency(ISourceLocation pomLoc) {
        return hasDependency(pomLoc, "org.rascalmpl", "rascal-lsp");
    }
    
    public IString getCurrentRascalLspVersion() {
        var pkg = PomAnalyzer.class.getPackage();
        if (pkg != null) {
            var specificationVersion = pkg.getSpecificationVersion();
            if (specificationVersion != null) {
                return vf.string(specificationVersion);
            }
        }
        
        try (InputStream prop = PomAnalyzer.class.getClassLoader().getResourceAsStream("project.properties")) {
            if (prop != null) {
                Properties properties = new Properties();
                properties.load(prop);
                var version = properties.getProperty("rascal.lsp.version");
                if (version != null) {
                    return vf.string(version);
                }
            }
        } catch (IOException e) {
            // Fall through
        }

        var fallbackVersion = "2.22.5";
        return vf.string(fallbackVersion);
    }
}
