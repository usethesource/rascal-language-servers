/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import io.usethesource.vallang.ISourceLocation;

/**
 * Keep track of changing path configs for every project root
 * It does assume the watch is correctly implemented.
 */
public class PathConfigs {
    private static final Logger logger = LogManager.getLogger(PathConfigs.class);
    private final Map<ISourceLocation, PathConfig> currentPathConfigs = new ConcurrentHashMap<>();
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
            URIResolverRegistry reg = URIResolverRegistry.getInstance();
            ISourceLocation manifest = URIUtil.getChildLocation(projectRoot, "META-INF/RASCAL.MF");
            if (reg.exists(manifest)) {
                reg.watch(manifest, false, changedManifest -> {
                    currentPathConfigs.replace(projectRoot, actualBuild(projectRoot));
                });
            }
            return actualBuild(projectRoot);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not build pathconfig", e);
        }
    }

    private static PathConfig actualBuild(ISourceLocation projectRoot) {
        try {
            return PathConfig.fromSourceProjectRascalManifest(projectRoot, RascalConfigMode.COMPILER);
        }
        catch (IOException e) {
            logger.error("Could not figure out path config for: {}, falling back to default", projectRoot, e);
            return new PathConfig();
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