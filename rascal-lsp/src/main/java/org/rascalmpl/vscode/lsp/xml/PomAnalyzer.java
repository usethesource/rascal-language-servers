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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger logger = LogManager.getLogger(PomAnalyzer.class);

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

    private static final String ORG_RASCALMPL = "org.rascalmpl";
    private static final String RASCAL = "rascal";
    private static final String RASCAL_LSP = "rascal-lsp";

    public IBool hasRascalDependency(ISourceLocation pomLoc) {
        return hasDependency(pomLoc, ORG_RASCALMPL, RASCAL);
    }

    public IBool hasRascalLspDependency(ISourceLocation pomLoc) {
        return hasDependency(pomLoc, ORG_RASCALMPL, RASCAL_LSP);
    }

    public static Artifact getRascalDependencyFromPom(ISourceLocation pomLoc) throws IOException {
        return getDependency(pomLoc, ORG_RASCALMPL, RASCAL);
    }

    public static Artifact getRascalLspDependencyFromPom(ISourceLocation pomLoc) throws IOException {
        return getDependency(pomLoc, ORG_RASCALMPL, RASCAL_LSP);
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
    private static final ComparableVersion MINIMAL_RASCAL_RELEASE_VERSION = new ComparableVersion("0.43.0");
    private static final ComparableVersion MINIMAL_RASCAL_LSP_RELEASE_VERSION = new ComparableVersion("2.23.0");
    private static final ComparableVersion ZERO_ZERO_ZERO = new ComparableVersion("0.0.0");
    private static final String NOT_SPECIFIED = "Not specified";
    private static final String VERSION_UNKNOWN = "???";

    private static final ComparableVersion getMinimalRascalVersion() {
        var currentRascalVersion = RascalManifest.getRascalVersionNumber();
        if (currentRascalVersion.equals(NOT_SPECIFIED)) {
            return ZERO_ZERO_ZERO;
        }
        return min(MINIMAL_RASCAL_RELEASE_VERSION, new ComparableVersion(currentRascalVersion));
    }

    private static final ComparableVersion getMinimalRascalLspVersion() {
        var currentRascalLspVersion = currentRascalLspVersion();
        if (currentRascalLspVersion == null) {
            return ZERO_ZERO_ZERO;
        }
        return min(MINIMAL_RASCAL_LSP_RELEASE_VERSION, new ComparableVersion(currentRascalLspVersion));
    }

    private static ComparableVersion min(ComparableVersion lhs, ComparableVersion rhs) {
        return lhs.compareTo(rhs) < 0 ? lhs : rhs;
    }

    // These declarations mirror the data definitions in the `lang::rascal::lsp::Actions` module
    private static final Type Command_updateRascalDependency = tf.constructor(Messages.ts, Messages.Command, "updateRascalDependency", tf.sourceLocationType(), "pomLoc", tf.stringType(), "version");
    private static final Type Command_updateRascalLspDependency = tf.constructor(Messages.ts, Messages.Command, "updateRascalLspDependency", tf.sourceLocationType(), "pomLoc", tf.stringType(), "version");

    private static IConstructor makeUpdateDependencyMessage(String dependency, Type commandType, ISourceLocation pomXml, String oldVersion, String newVersion) {
        var errorLocation = vf.sourceLocation(pomXml, 0, 0, 2, 2, 0, 8);
        var error = Messages.error(dependency + " version in pom.xml (" + oldVersion + ") is too old. Update the dependency or use the Quick Fix.", errorLocation);
        var codeAction = vf.constructor(commandType, new IValue[] { errorLocation, vf.string(newVersion) }, Map.of("title", vf.string("Update " + dependency + " dependency in pom.xml")));
        var fix = vf.constructor(Messages.CodeAction_action, new IValue[]{}, Map.of("command", codeAction));
        return Messages.addFix(error, fix);
    }

    public static ISet verifyRascalAndLspVersions(ISourceLocation pomXml) {
        var messagesWriter = vf.setWriter();

        try {
            var rascalDependencyVersion = PomAnalyzer.getRascalDependencyFromPom(pomXml).getCoordinate().getVersion();
            var rascalVersion = new ComparableVersion(rascalDependencyVersion);
            var rascalIsNewEnough = rascalVersion.compareTo(getMinimalRascalVersion()) >= 0;

            if (!rascalIsNewEnough) {
                logger.debug("Rascal dependency ({}) is outdated (expected >= {})", rascalDependencyVersion, getMinimalRascalVersion());
                var currentRascalVersion = RascalManifest.getRascalVersionNumber();
                if (currentRascalVersion.equals(NOT_SPECIFIED)) {
                    currentRascalVersion = VERSION_UNKNOWN;
                }
                messagesWriter.append(makeUpdateDependencyMessage("Rascal", Command_updateRascalDependency, pomXml, rascalDependencyVersion, RascalManifest.getRascalVersionNumber()));
            }
        } catch (IOException e) {
            // No rascal dependency in pom.xml. Diagnostics are generated elsewhere
        }

        try {
            var rascalLspDependencyVersion = PomAnalyzer.getRascalLspDependencyFromPom(pomXml).getCoordinate().getVersion();
            var rascalLspVersion = new ComparableVersion(rascalLspDependencyVersion);
            var rascalLspIsNewEnough = rascalLspVersion.compareTo(getMinimalRascalLspVersion()) >= 0;

            if (!rascalLspIsNewEnough) {
                logger.debug("Rascal-lsp dependency ({}) outdated (expected >= {})", rascalLspDependencyVersion, getMinimalRascalLspVersion());
                var currentRascalLspVersion = PomAnalyzer.currentRascalLspVersion();
                if (currentRascalLspVersion == null) {
                    currentRascalLspVersion = VERSION_UNKNOWN;
                }
                messagesWriter.append(makeUpdateDependencyMessage("Rascal-lsp", Command_updateRascalLspDependency, pomXml, rascalLspDependencyVersion, currentRascalLspVersion));
            }
        } catch (IOException e) {
            // No rascal-lsp dependency in pom.xml. Diagnostics are generated elsewhere (if needed)
        }
        return messagesWriter.done();
    }
}
