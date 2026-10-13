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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.util.Sets;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
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

    private static @Nullable Artifact getDependency(ISourceLocation pomLoc, String groupId, String artifactId) {
        try {
            var mavenParser = new MavenParser(Path.of(pomLoc.getURI()));
            var rootProject = mavenParser.parseProject();
            var resolvedDependencies = rootProject.resolveDependencies(Scope.COMPILE, mavenParser);
            return resolvedDependencies.stream()
                .filter(a -> a.getCoordinate().getGroupId().equals(groupId) && a.getCoordinate().getArtifactId().equals(artifactId))
                .findFirst().orElse(null);
        } catch (ModelResolutionError e) {
            return null;
        }
    }

    private IBool hasDependency(ISourceLocation pomLoc, String groupId, String artifactId) {
        return vf.bool(getDependency(pomLoc, groupId, artifactId) != null);
    }

    private static final String RASCAL_ORGANIZATION = "org.rascalmpl";
    private static final String RASCAL = "rascal";
    private static final String RASCAL_LSP = "rascal-lsp";

    public IBool hasRascalDependency(ISourceLocation pomLoc) {
        return hasDependency(pomLoc, RASCAL_ORGANIZATION, RASCAL);
    }

    public IBool hasRascalLspDependency(ISourceLocation pomLoc) {
        return hasDependency(pomLoc, RASCAL_ORGANIZATION, RASCAL_LSP);
    }

    public static @Nullable Artifact getRascalDependencyFromPom(ISourceLocation pomLoc) {
        return getDependency(pomLoc, RASCAL_ORGANIZATION, RASCAL);
    }

    public static @Nullable Artifact getRascalLspDependencyFromPom(ISourceLocation pomLoc) {
        return getDependency(pomLoc, RASCAL_ORGANIZATION, RASCAL_LSP);
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

    private static CompletableFuture<IConstructor> makeUpdateDependencyMessage(String dependencyLabel, String dependency, Type commandType, ISourceLocation pomXml, RascalLanguageServices services, String oldVersion, String newVersion) {
        return services.getDependencyVersionLocation(pomXml, RASCAL_ORGANIZATION, dependency)
            .thenApply(errorLocation -> {
                var addFix = errorLocation != null;
                // The back-up location is set on line 2 (and not line 1) to ensure that when clicking the Quick Fix hover, the mouse does not go out of the editor area and make the hover disappear
                errorLocation = Objects.requireNonNullElse(errorLocation, vf.sourceLocation(pomXml, 0, 0, 2, 2, 0, 8));

                var error = Messages.error(dependencyLabel + " version in pom.xml (" + oldVersion + ") is too old. Update the dependency" + (addFix ? " or use the Quick Fix." : "."), errorLocation);
                if (addFix) {
                    var codeAction = vf.constructor(commandType, new IValue[] { errorLocation, vf.string(newVersion) }, Map.of("title", vf.string("Update " + dependencyLabel + " dependency in pom.xml")));
                    var fix = vf.constructor(Messages.CodeAction_action, new IValue[]{}, Map.of("command", codeAction));
                    return Messages.addFix(error, fix);
                }
                return error;
            });
    }

    public static CompletableFuture<Set<IConstructor>> verifyRascalAndLspVersions(ISourceLocation pomXml, RascalLanguageServices services, Executor exec) {
        var messages = new HashSet<CompletableFuture<IConstructor>>();

        var rascalDependency = PomAnalyzer.getRascalDependencyFromPom(pomXml);
        if (rascalDependency != null) {
            var rascalDependencyVersion = rascalDependency.getCoordinate().getVersion();
            var rascalVersion = new ComparableVersion(rascalDependencyVersion);
            var rascalIsNewEnough = rascalVersion.compareTo(getMinimalRascalVersion()) >= 0;

            if (!rascalIsNewEnough) {
                logger.debug("Rascal dependency ({}) is outdated (expected >= {})", rascalDependencyVersion, getMinimalRascalVersion());
                var currentRascalVersion = RascalManifest.getRascalVersionNumber();
                if (currentRascalVersion.equals(NOT_SPECIFIED)) {
                    currentRascalVersion = VERSION_UNKNOWN;
                }
                messages.add(makeUpdateDependencyMessage("Rascal", RASCAL, Command_updateRascalDependency, pomXml, services, rascalDependencyVersion, currentRascalVersion));
            }
        } // else: No rascal dependency in pom.xml. Diagnostics are generated elsewhere

        var rascalLspDependency = PomAnalyzer.getRascalLspDependencyFromPom(pomXml);
        if (rascalLspDependency != null) {
            var rascalLspDependencyVersion = rascalLspDependency.getCoordinate().getVersion();
            var rascalLspVersion = new ComparableVersion(rascalLspDependencyVersion);
            var rascalLspIsNewEnough = rascalLspVersion.compareTo(getMinimalRascalLspVersion()) >= 0;

            if (!rascalLspIsNewEnough) {
                logger.debug("Rascal-lsp dependency ({}) outdated (expected >= {})", rascalLspDependencyVersion, getMinimalRascalLspVersion());
                var currentRascalLspVersion = PomAnalyzer.currentRascalLspVersion();
                if (currentRascalLspVersion == null) {
                    currentRascalLspVersion = VERSION_UNKNOWN;
                }
                messages.add(makeUpdateDependencyMessage("Rascal-lsp", RASCAL_LSP, Command_updateRascalLspDependency, pomXml, services, rascalLspDependencyVersion, currentRascalLspVersion));
            }
        } // else: No rascal-lsp dependency in pom.xml. Diagnostics are generated elsewhere (if needed)

        return CompletableFutureUtils.reduce(messages, CompletableFutureUtils.completedFuture(new HashSet<IConstructor>(), exec), Collections::singleton, Sets::union);
    }
}
