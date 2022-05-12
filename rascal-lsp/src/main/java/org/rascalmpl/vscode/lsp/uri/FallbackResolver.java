package org.rascalmpl.vscode.lsp.uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import org.rascalmpl.uri.ISourceLocationInputOutput;
import org.rascalmpl.uri.ISourceLocationWatcher;
import io.usethesource.vallang.ISourceLocation;

public class FallbackResolver implements ISourceLocationInputOutput, ISourceLocationWatcher {

    @Override
    public InputStream getInputStream(ISourceLocation uri) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean exists(ISourceLocation uri) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long lastModified(ISourceLocation uri) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isDirectory(ISourceLocation uri) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isFile(ISourceLocation uri) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String[] list(ISourceLocation uri) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String scheme() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean supportsHost() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public OutputStream getOutputStream(ISourceLocation uri, boolean append) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void mkDirectory(ISourceLocation uri) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void remove(ISourceLocation uri) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void setLastModified(ISourceLocation uri, long timestamp) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void watch(ISourceLocation root, Consumer<ISourceLocationChanged> watcher) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void unwatch(ISourceLocation root, Consumer<ISourceLocationChanged> watcher) throws IOException {
        // TODO Auto-generated method stub

    }

}
