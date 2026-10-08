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
import java.util.Map;
import java.util.Properties;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.interpreter.utils.RascalManifest;
import org.rascalmpl.library.Messages;
import org.rascalmpl.util.maven.Artifact;
import org.rascalmpl.util.maven.MavenParser;
import org.rascalmpl.util.maven.ModelResolutionError;
import org.rascalmpl.util.maven.Scope;
import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;

public class PomAnalyzer {
    private static final IValueFactory vf = IRascalValueFactory.getInstance();
    private static final TypeFactory tf = TypeFactory.getInstance();

    private static Artifact getDependency(ISourceLocation pomLoc, String groupId, String artifactId) throws IOException {
        try {
            var mavenParser = new MavenParser(Path.of(pomLoc.getURI()));
            var rootProject = mavenParser.parseProject();
            var resolvedDependencies = rootProject.resolveDependencies(Scope.COMPILE, mavenParser);
            return resolvedDependencies.stream()
                .filter(a -> a.getCoordinate().getGroupId().equals(groupId) && a.getCoordinate().getArtifactId().equals(artifactId))
                .findFirst().orElseThrow(() -> new IOException("Did not find dependency " + groupId + ":" + artifactId + " in pom.xml at " + pomLoc));
        } catch (ModelResolutionError | IOException e) {
            throw new IOException(e);
        }
    }

    private IBool hasDependency(ISourceLocation pomLoc, String groupId, String artifactId) {
        try {
            getDependency(pomLoc, groupId, artifactId);
            return vf.bool(true);
        } catch (IOException e) {
            return vf.bool(false);
        }
    }

    public IBool hasRascalDependency(ISourceLocation pomLoc) {
        return hasDependency(pomLoc, "org.rascalmpl", "rascal");
    }

    public IBool hasRascalLspDependency(ISourceLocation pomLoc) {
        return hasDependency(pomLoc, "org.rascalmpl", "rascal-lsp");
    }

    public static Artifact getRascalDependencyFromPom(ISourceLocation pomLoc) throws IOException {
        return getDependency(pomLoc, "org.rascalmpl", "rascal");
    }

    public static Artifact getRascalLspDependencyFromPom(ISourceLocation pomLoc) throws IOException {
        return getDependency(pomLoc, "org.rascalmpl", "rascal-lsp");
    }

    public IString getCurrentRascalLspVersion() {
        var version = currentRascalLspVersion();
        if (version != null) {
            return vf.string(version);
        } else {
            throw RuntimeExceptionFactory.io("Could not detect current version of `rascal-lsp`");
        }
    }

    public static @Nullable String currentRascalLspVersion() {
        var pkg = PomAnalyzer.class.getPackage();
        if (pkg != null) {
            var specificationVersion = pkg.getSpecificationVersion();
            if (specificationVersion != null) {
                return specificationVersion;
            }
        }

        try (InputStream prop = PomAnalyzer.class.getClassLoader().getResourceAsStream("project.properties")) {
            if (prop != null) {
                Properties properties = new Properties();
                properties.load(prop);
                var version = properties.getProperty("rascal.lsp.version");
                if (version != null) {
                    return version;
                }
            }
        } catch (IOException e) {
            // Fall through
        }

        return null;
    }

    // These versions are the first released versions after finishing the "pom-leading" project,
    // in which the cut of the tight coupling between `rascal` and `rascal-lsp` was established.
    private static final ComparableVersion MINIMAL_RASCAL_VERSION = new ComparableVersion("0.43.0");
    private static final ComparableVersion MINIMAL_RASCAL_LSP_VERSION = new ComparableVersion("2.23.0");

    // These declarations mirror the data definitions in the `lang::rascal::lsp::Actions` module
    private static final Type Command_updateRascalDependency = tf.constructor(Messages.ts, Messages.Command, "updateRascalDependency", tf.sourceLocationType(), "pomLoc", tf.stringType(), "version");
    private static final Type Command_updateRascalLspDependency = tf.constructor(Messages.ts, Messages.Command, "updateRascalLspDependency", tf.sourceLocationType(), "pomLoc", tf.stringType(), "version");

    private static IConstructor makeUpdateDependencyMessage(String dependency, Type commandType, ISourceLocation pomXml, String oldVersion, String newVersion) {
        var errorLocation = vf.sourceLocation(pomXml, 0, 0, 2, 2, 0, 8);
        var error = Messages.error(dependency + " version in pom.xml (" + oldVersion.toString() + ") is too old. Update the dependency or use the Quick Fix.", errorLocation);
        var codeAction = vf.constructor(commandType, new IValue[] { errorLocation, vf.string(newVersion) }, Map.of("title", vf.string("Update " + dependency + " dependency in pom.xml")));
        var fix = vf.constructor(Messages.CodeAction_action, new IValue[]{}, Map.of("command", codeAction));
        return Messages.addFix(error, fix);
    }

    public static ISet verifyRascalAndLspVersions(ISourceLocation pomXml) throws IOException {
        var rascalDependencyVersion = PomAnalyzer.getRascalDependencyFromPom(pomXml).getCoordinate().getVersion();
        var rascalLspDependencyVersion = PomAnalyzer.getRascalLspDependencyFromPom(pomXml).getCoordinate().getVersion();
        var rascalVersion = new ComparableVersion(rascalDependencyVersion);
        var rascalLspVersion = new ComparableVersion(rascalLspDependencyVersion);

        var rascalIsNewEnough = rascalVersion.compareTo(MINIMAL_RASCAL_VERSION) > 0;
        var rascalLspIsNewEnough = rascalLspVersion.compareTo(MINIMAL_RASCAL_LSP_VERSION) > 0;

        var messagesWriter = vf.setWriter();
        if (!rascalIsNewEnough) {
            messagesWriter.append(makeUpdateDependencyMessage("Rascal", Command_updateRascalDependency, pomXml, rascalDependencyVersion, RascalManifest.getRascalVersionNumber()));
        }
        if (!rascalLspIsNewEnough) {
            var currentRascalLspVersion = PomAnalyzer.currentRascalLspVersion();
            if (currentRascalLspVersion == null) {
                currentRascalLspVersion = "???";
            }
            messagesWriter.append(makeUpdateDependencyMessage("Rascal-lsp", Command_updateRascalLspDependency, pomXml, rascalLspDependencyVersion, currentRascalLspVersion));
        }
        return messagesWriter.done();
    }
}
