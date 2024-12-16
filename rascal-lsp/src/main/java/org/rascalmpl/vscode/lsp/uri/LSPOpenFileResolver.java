package org.rascalmpl.vscode.lsp.uri;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.uri.ISourceLocationInput;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.ISourceLocation;

public class LSPOpenFileResolver implements ISourceLocationInput {

    @Override
    public InputStream getInputStream(ISourceLocation uri) throws IOException {
        var fallbackResolver = FallbackResolver.getInstance();
        uri = stripLspPrefix(uri);
        if (fallbackResolver.isFileManaged(uri)) {
            return new ByteArrayInputStream(fallbackResolver.getDocumentState(uri).getCurrentContent().get().getBytes(StandardCharsets.UTF_16));
        }
        throw new IOException("File does not exist");
    }

    @Override
    public Charset getCharset(ISourceLocation uri) throws IOException {
        return StandardCharsets.UTF_16;
    }

    @Override
    public boolean exists(ISourceLocation uri) {
        return FallbackResolver.getInstance().isFileManaged(stripLspPrefix(uri));
    }

    @Override
    public long lastModified(ISourceLocation uri) throws IOException {
        return FallbackResolver.getInstance().getDocumentState(stripLspPrefix(uri)).getLastModified();
    }

    @Override
    public boolean isDirectory(ISourceLocation uri) {
        return false;
    }

    @Override
    public boolean isFile(ISourceLocation uri) {
        return exists(uri);
    }

    private static ISourceLocation stripLspPrefix(ISourceLocation uri) {
        if (uri.getScheme().startsWith("lsp+")) {
            try {
                return URIUtil.changeScheme(uri, uri.getScheme().substring("lsp+".length()));
            } catch (URISyntaxException e) {
                // fall through
            }
        }
        return uri;
    }

    @Override
    public String[] list(ISourceLocation uri) throws IOException {
        throw new UnsupportedOperationException("Unimplemented method 'list'");
    }

    @Override
    public String scheme() {
        return "lsp";
    }

    @Override
    public boolean supportsHost() {
        return false;
    }
    
}
