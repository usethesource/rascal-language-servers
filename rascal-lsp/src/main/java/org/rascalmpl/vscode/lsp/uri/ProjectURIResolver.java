package org.rascalmpl.vscode.lsp.uri;

import java.io.IOException;
import java.util.function.Function;

import org.rascalmpl.uri.ILogicalSourceLocationResolver;

import io.usethesource.vallang.ISourceLocation;

public class ProjectURIResolver implements ILogicalSourceLocationResolver {
    private final Function<ISourceLocation,ISourceLocation> resolver;

    public ProjectURIResolver(Function<ISourceLocation,ISourceLocation> resolver) {
        this.resolver = resolver;
    }

    @Override
    public ISourceLocation resolve(ISourceLocation input) throws IOException {
        return resolver.apply(input);
    }

    @Override
    public String scheme() {
        return "project";
    }

    @Override
    public String authority() {
        return "";
    }
}
