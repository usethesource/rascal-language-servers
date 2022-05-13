package org.rascalmpl.vscode.lsp.uri;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Function;
import org.rascalmpl.uri.ISourceLocationInputOutput;
import org.rascalmpl.uri.ISourceLocationWatcher;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeVFS;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeUriResolverClient;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeUriResolverServer;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.IOResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.WriteFileRequest;
import io.usethesource.vallang.ISourceLocation;

public class FallbackResolver implements ISourceLocationInputOutput, ISourceLocationWatcher {

    private static VSCodeUriResolverServer getServer() throws IOException {
        var result = VSCodeVFS.INSTANCE.getServer();
        if (result == null) {
            throw new IOException("Missing VFS file server");
        }
        return result;
    }

    private static VSCodeUriResolverClient getClient() throws IOException {
        var result = VSCodeVFS.INSTANCE.getClient();
        if (result == null) {
            throw new IOException("Missing VFS file client");
        }
        return result;
    }

    private static <T extends IOResult> T call(Function<VSCodeUriResolverServer, CompletableFuture<T>> target) throws IOException {
        try {
            var result = target.apply(getServer()).join();
            if (result.getErrorCode() != 0) {
                throw new IOException("" + result.getErrorCode() + ": " + result.getErrorMessage());
            }
            return result;
        }
        catch (CompletionException ce) {
            throw new IOException(ce.getCause());
        }
    }

    private static ISourceLocationRequest param(ISourceLocation uri) {
        return new ISourceLocationRequest(uri);
    }

    @Override
    public InputStream getInputStream(ISourceLocation uri) throws IOException {
        var fileBody = call(s -> s.readFile(param(uri))).getContents();

        // TODO: do the decoding in a stream, to avoid the extra intermediate
        // byte array
        return Base64.getDecoder().wrap(
            new ByteArrayInputStream(
                fileBody.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Override
    public boolean exists(ISourceLocation uri) {
        try {
            return call(s -> s.exists(param(uri))).getResult();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public long lastModified(ISourceLocation uri) throws IOException {
        return call(s -> s.lastModified(param(uri))).getTimestamp();
    }

    @Override
    public long created(ISourceLocation uri) throws IOException {
        return call(s -> s.created(param(uri))).getTimestamp();
    }

    @Override
    public boolean isDirectory(ISourceLocation uri) {
        try {
            return call(s -> s.isDirectory(param(uri))).getResult();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean isFile(ISourceLocation uri) {
        try {
            return call(s -> s.isFile(param(uri))).getResult();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String[] list(ISourceLocation uri) throws IOException {
        return call(s -> s.list(param(uri))).getEntries();
    }

    @Override
    public String scheme() {
        throw new UnsupportedOperationException("Scheme not supported on fallback resolver");
    }

    @Override
    public boolean supportsHost() {
        return false;
    }

    @Override
    public OutputStream getOutputStream(ISourceLocation uri, boolean append) throws IOException {
        // we have to collect all bytes into memory (already encoded as base64)
        // when done with the outputstream, we can write push the base64
        // to the vscode side
        return new OutputStream() {
            private boolean closed = false;
            private Encoder encoder = Base64.getEncoder();
            private StringBuilder result = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                result.append(encoder.encodeToString(new byte[]{(byte)(b & 0xff)}));
            }

            @Override
            public void write(byte[] b) throws IOException {
                result.append(encoder.encodeToString(b));
            }

            @Override
            @SuppressWarnings("deprecation") // using deprecated string constructor
            public void write(byte[] b, int off, int len) throws IOException {
                // since the encoder does not support byte array ranges, we have
                // to inline the `encodeToString` logic
                ByteBuffer encoded = encoder.encode(ByteBuffer.wrap(b, off, len));
                result.append(new String(encoded.array(), 0, 0, encoded.limit()));
            }

            @Override
            public void close() throws IOException {
                if (closed) {
                    return;
                }
                closed = true;
                call(s -> s.writeFile(new WriteFileRequest(uri, result.toString(), append)));
            }
        };
    }

    @Override
    public void mkDirectory(ISourceLocation uri) throws IOException {
        call(s -> s.mkDirectory(param(uri)));
    }

    @Override
    public void remove(ISourceLocation uri) throws IOException {
        call(s -> s.remove(param(uri)));
    }

    @Override
    public void setLastModified(ISourceLocation uri, long timestamp) throws IOException {
        throw new IOException("setLastModified not supported by vscode");
    }

    @Override
    public void watch(ISourceLocation root, Consumer<ISourceLocationChanged> watcher) throws IOException {
        getClient().addWatcher(root, watcher, getServer());
    }

    @Override
    public void unwatch(ISourceLocation root, Consumer<ISourceLocationChanged> watcher) throws IOException {
        getClient().removeWatcher(root, watcher, getServer());

    }

}
