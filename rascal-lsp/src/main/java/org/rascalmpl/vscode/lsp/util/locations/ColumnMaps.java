package org.rascalmpl.vscode.lsp.util.locations;

import java.time.Duration;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.rascalmpl.vscode.lsp.util.locations.impl.ArrayLineOffsetMap;

import io.usethesource.vallang.ISourceLocation;

public class ColumnMaps {
    private final LoadingCache<ISourceLocation, LineColumnOffsetMap> currentEntries;

    public ColumnMaps(Function<ISourceLocation, String> getContents) {
        currentEntries = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .softValues()
            .build(l -> ArrayLineOffsetMap.build(getContents.apply(l)));
    }

    public LineColumnOffsetMap get(ISourceLocation sloc) {
        return currentEntries.get(sloc.top());
    }

    public void clear(ISourceLocation sloc) {
        currentEntries.invalidate(sloc.top());
    }
}
