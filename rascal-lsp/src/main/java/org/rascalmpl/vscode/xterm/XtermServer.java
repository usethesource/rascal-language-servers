package org.rascalmpl.eclipse.views.xterm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;

import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.repl.REPLPipedInputStream;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerSocketProcessor;
import org.simpleframework.http.socket.Frame;
import org.simpleframework.http.socket.FrameChannel;
import org.simpleframework.http.socket.FrameListener;
import org.simpleframework.http.socket.Reason;
import org.simpleframework.http.socket.Session;
import org.simpleframework.http.socket.service.DirectRouter;
import org.simpleframework.http.socket.service.Router;
import org.simpleframework.http.socket.service.RouterContainer;
import org.simpleframework.http.socket.service.Service;
import org.simpleframework.transport.connect.SocketConnection;

import io.usethesource.vallang.ISourceLocation;

public class XtermServer {
    private static final int FILE_BUFFER_SIZE = 8 * 1024;
    
    private final int port;
    private final SocketConnection connection;

    public XtermServer(int port) throws IOException {
        this.port = port;
        XtermService xtermService = new XtermService();
        Router router = new DirectRouter(xtermService);
        RouterContainer routerContainer = new RouterContainer(new XtermContainer(), router, 1);
        ContainerSocketProcessor server = new ContainerSocketProcessor(routerContainer, 1);

        connection = new SocketConnection(server);
        connection.connect(new InetSocketAddress(port));
    }
    
    public int getPort() {
        return port;
    }
    
    /** 
     * Handles new terminal connections
     */
    private class XtermService implements Service {
        @Override
        public void connect(Session session) {
            try {
                REPLPipedInputStream input = new REPLPipedInputStream();
                OutputStream output = new SocketOutputStream(session.getChannel());
                session.getChannel().register(new XtermFrameListener(input));
                
                System.err.println("Session connection parameters: " + session.getRequest().getQuery());
                
                
                // TODO: here we can inject different kinds of connectors
                // TODO: receive project config parameters from session URL
                new RascalXtermConnector().connect(input, output, session.getRequest().getQuery());
            } catch (IOException e) {
                Activator.log("failed to connect xterm server", e);
            }
        }
        
    }
    
    /**
     * This handles input frames on the websocket and queues the input
     * on a QueuedInputStream.
     */
    private class XtermFrameListener implements FrameListener {
        private final REPLPipedInputStream inputStream;
        
        public XtermFrameListener(REPLPipedInputStream input) {
            this.inputStream = input;
        }

        @Override
        public void onFrame(Session session, Frame frame) {
        	if (frame.getType().isPing() || frame.getType().isPong()) {
        		return;
        	}
            System.err.println("received frame: " + frame.getText());
            System.err.println("type = " + frame.getType());

            switch (frame.getType()) {
            case TEXT:
            case BINARY:
                inputStream.write(frame.getBinary());
                break;
            case CONTINUATION:
                // TODO: check if this is the right response
                inputStream.write(frame.getBinary());
                break;
            case CLOSE:
                stop();
                break;
            case PONG:
            case PING:
                // do nothing
            }
        }

        @Override
        public void onClose(Session arg0, Reason arg1) {
            System.err.println("closed the websocket: " + arg0);
            stop();
        }

        @Override
        public void onError(Session arg0, Exception e) {
            Activator.log("error in XTerm server: " + e.getMessage(), e);
        }
        
        public void stop() {
            inputStream.close();
        }
        
    }

    /**
     * This handles the HTTP requests to load the HTML5 files into the client.
     */
    private static class XtermContainer implements Container {
        
        @Override
        public void handle(Request request, Response response) {
            try {
                ISourceLocation file = URIUtil.getChildLocation(URIUtil.correctLocation("plugin", "rascal_eclipse", "org/rascalmpl/eclipse/views/xterm/html5"), request.getAddress().getPath().getPath());
                if (!URIResolverRegistry.getInstance().exists(file)) {
                    file = URIUtil.getChildLocation(URIUtil.changePath(file, "target/classes/org/rascalmpl/eclipse/views/xterm/html5"), request.getAddress().getPath().getPath());
                }

                response.setContentType(getMimeType(request.getAddress().getPath().getExtension()));
                copy(URIResolverRegistry.getInstance().getInputStream(file), response.getOutputStream());
                response.close();

            } catch (IOException | URISyntaxException e) {
                Activator.log("XTerm server request failed", e);
            } 
        }
        
        private String getMimeType(String ext){
            switch(ext){
            case "css":     return "text/css";
            case "ico":     return "image/x-icon";
            case "svg":     return "image/svg+xml";
            case "htm":     
            case "html":    return "text/html";
            case "jpg":     
            case "jpeg":    return "image/jpeg";
            case "png":     return "image/png";
            case "js":      return "text/javascript";
            case "map":     return "application/json";
            case "txt":     
            default:        return "text/plain"; 
            }
        }
        
        private static void copy(InputStream from, OutputStream to) throws IOException {
            final byte[] buffer = new byte[FILE_BUFFER_SIZE];
            int read;
            while ((read = from.read(buffer, 0, buffer.length)) != -1) {
                to.write(buffer, 0, read);
            }
        }
    }

    public void stop() {
        try {
            connection.close();
        } catch (IOException e) {
            Activator.log("failed to close XTerm connection", e);
        }
    }
    
    /**
     * This wraps a websocket framechannel as an OutputStream 
     */
    private class SocketOutputStream extends OutputStream {
        private final FrameChannel channel;
        
        public SocketOutputStream(FrameChannel frameChannel) {
            this.channel = frameChannel;
        }
        
        @Override
        public void write(int b) throws IOException {
            channel.send(new byte[] { (byte)b });
        }
        
        @Override
        public void write(byte[] b) throws IOException {
            channel.send(b);
        }
        
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (off == 0 && b.length == len) {
                channel.send(b);
            }
            else {
                // TODO: can we not avoid this copying?
                byte[] buf = new byte[len];
                System.arraycopy(b, off, buf, 0, len);
                channel.send(buf);
            }
        }
    }
}
