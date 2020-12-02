package org.rascalmpl.vscode.xterm;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public interface XtermConnector {
    public void initialize() throws Exception;

    public void connect(InputStream stdin, OutputStream stdout, Map<String, String> parameters);

    public void disconnect();

    public boolean isLocalEcho();

    public void setTerminalSize(int newWidth, int newHeight);
}
