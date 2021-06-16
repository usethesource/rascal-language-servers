package org.rascalmpl.vscode.lsp.uri;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.function.Function;

import org.rascalmpl.uri.ILogicalSourceLocationResolver;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.ISourceLocation;

public class TargetURIResolver implements ILogicalSourceLocationResolver {
    private final Function<ISourceLocation,ISourceLocation> resolver;

    public TargetURIResolver(Function<ISourceLocation,ISourceLocation> resolver) {
        this.resolver = resolver;
    }

    @Override
    public ISourceLocation resolve(ISourceLocation input) throws IOException {
        try {
            URIResolverRegistry reg = URIResolverRegistry.getInstance();
            ISourceLocation projectLoc = URIUtil.changeScheme(input, "project");
            ISourceLocation resolved = resolver.apply(projectLoc);
            ISourceLocation target;

            target = URIUtil.getChildLocation(resolved, "bin");
            if (reg.exists(target)) {
                return URIUtil.getChildLocation(target, input.getPath());
            }

            target = URIUtil.getChildLocation(resolved, "target/classes");
            if (reg.exists(target)) {
                return URIUtil.getChildLocation(target, input.getPath());
            }

            return input;
        }
        catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @Override
    public String scheme() {
        return "target";
    }

    @Override
    public String authority() {
        return "";
    }
}
