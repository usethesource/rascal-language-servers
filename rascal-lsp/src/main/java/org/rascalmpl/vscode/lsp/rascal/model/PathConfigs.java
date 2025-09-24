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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import io.usethesource.vallang.ISourceLocation;

/**
 * Keep track of changing path configs for every project root
 * It does assume the watch is correctly implemented.
 */
public class PathConfigs {
    private static final Logger logger = LogManager.getLogger(PathConfigs.class);
    private static final URIResolverRegistry reg = URIResolverRegistry.getInstance();
    private final Map<ISourceLocation, PathConfig> currentPathConfigs = new ConcurrentHashMap<>();
    private final PathConfigUpdater updater = new PathConfigUpdater(currentPathConfigs);
    private final LoadingCache<ISourceLocation, ISourceLocation> translatedRoots =
        Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(20))
            .build(PathConfigs::inferProjectRoot);


    public PathConfig lookupConfig(ISourceLocation forFile) {
        ISourceLocation projectRoot = translatedRoots.get(forFile);
        return currentPathConfigs.computeIfAbsent(projectRoot, this::buildPathConfig);

    }

    private static long safeLastModified(ISourceLocation uri) {
        try {
            return reg.lastModified(uri);
        } catch (IOException e) {
            logger.debug("Cannot get last modified time of {}", uri, e);
            return Long.MAX_VALUE;
        }
    }

    private PathConfig buildPathConfig(ISourceLocation projectRoot) {
        try {
            logger.debug("Building pcfg from: {}", projectRoot);
            ISourceLocation manifest = URIUtil.getChildLocation(projectRoot, "META-INF/RASCAL.MF");
            if (reg.exists(manifest)) {
                updater.watchFile(projectRoot, manifest);
            }
            var configSources = registerMavenWatches(reg, projectRoot);
            configSources.add(manifest);

            long newestConfig = configSources.stream()
                .mapToLong(PathConfigs::safeLastModified)
                .max()
                .getAsLong(); // safe, since the set has at least one element

            var result = updater.actualBuild(projectRoot, newestConfig, null);
            logger.debug("New path config: {}", result);
            return result;
        }
        catch (IOException e) {
            throw new RuntimeException("Could not build pathconfig", e);
        }
    }

    private static class PathConfigUpdater extends Thread {
        private final Map<ISourceLocation, PathConfig> currentPathConfigs;

        public PathConfigUpdater(Map<ISourceLocation, PathConfig> currentPathConfigs) {
            super("Path Config updater");
            setDaemon(true);
            this.currentPathConfigs = currentPathConfigs;
        }

        // we detect changes to roots, and keep track of the last changed time
        // the thread will clear them if the time is longer than the timeout
        private final Map<ISourceLocation, Long> changedRoots = new ConcurrentHashMap<>();
        public void watchFile(ISourceLocation projectRoot, ISourceLocation sourceFile) throws IOException {
            if (!isAlive() && !isInterrupted()) {
                start();
            }
            reg.watch(sourceFile, false, ignored ->
                changedRoots.put(projectRoot, safeLastModified(sourceFile))
            );
        }

        private static final long UPDATE_DELAY = TimeUnit.SECONDS.toNanos(5);

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException e) {
                    // inside of a thread run, we have to stop in case of an interrupt.
                    return;
                }

                List<ISourceLocation> stabilizedRoots = changedRoots.entrySet().stream()
                    .filter(e -> FileTime.from(Instant.now()).to(TimeUnit.NANOSECONDS) - e.getValue() >= UPDATE_DELAY)
                    .map(Entry::getKey)
                    .collect(Collectors.toList());

                try {
                    for (var root : stabilizedRoots) {
                        // right before we calculate the path config,
                        // we clear it from the list, as the path config calculation
                        // can take some time
                        final var changed = changedRoots.remove(root);
                        // pass the last modified time stamp that we just removed
                        currentPathConfigs.compute(root, (k, prevPcfg) -> actualBuild(k, changed, prevPcfg));
                    }
                } catch (Exception e) {
                    logger.error("Unexpected error while building PathConfigs", e) ;
                }
            }
        }

        private PathConfig actualBuild(ISourceLocation projectRoot, long newestConfig, @Nullable PathConfig prevPcfg) {
            final var newPcfg = PathConfig.fromSourceProjectRascalManifest(projectRoot, RascalConfigMode.COMPILER, true);
            // Did the path config change?
            if (newPcfg.equals(prevPcfg)) {
                try {
                    cleanOutdatedTPLs(newPcfg.getBin(), newestConfig);
                } catch (IOException e) {
                    logger.debug("Cannot clean outdated TPLs in {}. The directory might have been removed in the meantime.", newPcfg.getBin());
                }
            }
            return newPcfg;
        }

        /**
         * Over-approximation of outdated TPLs, which serves to clean the workspace after an update.
         * @param newPcfg The new path config of the project.
         * @throws IOException When listing the entries of the directory fails, e.g. the directory does not exist or the URI scheme is unsupported.
         */
        private void cleanOutdatedTPLs(ISourceLocation dir, long olderThan) throws IOException {
            if (!reg.isDirectory(dir)) {
                return;
            }
            for (var l : reg.list(dir)) {
                if (reg.isDirectory(dir)) {
                    cleanOutdatedTPLs(l, olderThan);
                } else {
                    try {
                        if (reg.isFile(l) && "tpl".equals(URIUtil.getExtension(l)) && safeLastModified(l) < olderThan) {
                            logger.debug("Deleting outdated TPL {}", l);
                            reg.remove(l, false);
                        }
                    } catch (IOException e) {
                        logger.debug("Cannot remove TPL at {}", l, e);
                    }
                }
            }
        }

    }

    private Set<ISourceLocation> registerMavenWatches(URIResolverRegistry reg, ISourceLocation projectRoot) throws IOException {
        final Set<ISourceLocation> poms = new HashSet<>();
        var mainPom = URIUtil.getChildLocation(projectRoot, "pom.xml");
        if (reg.exists(mainPom)) {
            updater.watchFile(projectRoot, mainPom);
            poms.add(mainPom);
            if (hasParentSection(reg, mainPom)) {
                var parentPom = URIUtil.getChildLocation(URIUtil.getParentLocation(projectRoot), "pom.xml");
                if (!mainPom.equals(parentPom) && reg.exists(parentPom)) {
                    updater.watchFile(projectRoot, parentPom);
                    poms.add(parentPom);
                }
            }
        }
        return poms;
    }

    private static final Pattern detectParent = Pattern.compile("<\\s*parent\\s*>");

    private static boolean hasParentSection(URIResolverRegistry reg, ISourceLocation mainPom) {
        try (var pom = new BufferedReader(reg.getCharacterReader(mainPom))) {
            String line;
            while ((line = pom.readLine()) != null) {
                if (detectParent.matcher(line).matches()) {
                    return true;
                }
            }
            return false;
        }
        catch (IOException ignored) {
            return false;
        }
    }

    private static ISourceLocation inferProjectRoot(ISourceLocation member) {
        ISourceLocation current = member;
        URIResolverRegistry reg = URIResolverRegistry.getInstance();
        if (!reg.isDirectory(current)) {
            current = URIUtil.getParentLocation(current);
        }

        while (current != null && reg.exists(current) && reg.isDirectory(current)) {
            if (reg.exists(URIUtil.getChildLocation(current, "META-INF/RASCAL.MF"))) {
                return current;
            }

            if (URIUtil.getParentLocation(current).equals(current)) {
                // we went all the way up to the root
                return reg.isDirectory(member) ? member : URIUtil.getParentLocation(member);
            }

            current = URIUtil.getParentLocation(current);
        }

        return current;
    }



}
