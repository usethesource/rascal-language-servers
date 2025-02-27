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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private PathConfig buildPathConfig(ISourceLocation projectRoot) {
        try {
            logger.debug("Building pcfg from: {}", projectRoot);
            URIResolverRegistry reg = URIResolverRegistry.getInstance();
            ISourceLocation manifest = URIUtil.getChildLocation(projectRoot, "META-INF/RASCAL.MF");
            if (reg.exists(manifest)) {
                updater.watchFile(projectRoot, manifest);
            }
            registerMavenWatches(reg, projectRoot);

            var result = actualBuild(projectRoot);
            logger.debug("new pcfg: {}", result);
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
            URIResolverRegistry.getInstance().watch(sourceFile, false, ignored ->
                changedRoots.put(projectRoot, System.nanoTime())
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
                    .filter(e -> System.nanoTime() - e.getValue() >= UPDATE_DELAY)
                    .map(Entry::getKey)
                    .collect(Collectors.toList());

                try {
                    for (var root : stabilizedRoots) {
                        // right before we calculate the path config,
                        // we clear it from the list, as the path config calculation
                        // can take some time
                        changedRoots.remove(root);
                        currentPathConfigs.replace(root, actualBuild(root));
                    }
                } catch (Exception e) {
                    logger.error("Unexpected error while building PathConfigs", e) ;
                }
            }
        }

    }

    private void registerMavenWatches(URIResolverRegistry reg, ISourceLocation projectRoot) throws IOException {
        var mainPom = URIUtil.getChildLocation(projectRoot, "pom.xml");
        if (reg.exists(mainPom)) {
            updater.watchFile(projectRoot, mainPom);
            if (hasParentSection(reg, mainPom)) {
                var parentPom = URIUtil.getChildLocation(URIUtil.getParentLocation(projectRoot), "pom.xml");
                if (!mainPom.equals(parentPom) && reg.exists(parentPom)) {
                    updater.watchFile(projectRoot, parentPom);
                }
            }
        }
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

    private static PathConfig actualBuild(ISourceLocation projectRoot) {
        return PathConfig.fromSourceProjectRascalManifest(projectRoot, RascalConfigMode.COMPILER);
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
