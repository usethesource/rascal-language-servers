package org.rascalmpl.eclipse.views.xterm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jline.Terminal;

public class XtermTerminal implements Terminal {

    public XtermTerminal() {
    }

    @Override
    public void disableInterruptCharacter() {
    	// TODO: add ctrl+c disable to bridge
    	// this is done every time we readline
    }

    @Override
    public void enableInterruptCharacter() {
    	// TODO: add ctrl+c enable to bridge

    }

    @Override
    public int getHeight() {
        return 20;
    }

    @Override
    public String getOutputEncoding() {
        return "UTF-8";
    }

    @Override
    public int getWidth() {
        return 80;
    }

    @Override
    public boolean hasWeirdWrap() {
    	// TODO: figure out which it is, in unix this maps to:
    	// getBooleanCapability("auto_right_margin") && getBooleanCapability("eat_newline_glitch");
        return false;
    }

    @Override
    public void init() throws Exception {

    }

    @Override
    public boolean isAnsiSupported() {
        return true;
    }

    @Override
    public boolean isEchoEnabled() {
        return false;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void reset() throws Exception {
    	throw new RuntimeException("Not supported in xterm.js bridge yet");
    }

    @Override
    public void restore() throws Exception {
    	throw new RuntimeException("Not supported in xterm.js bridge yet");
    }

    @Override
    public void setEchoEnabled(boolean arg0) {
    	throw new RuntimeException("Enabling echo not supported in xterm.js bridge");
    }

    @Override
    public InputStream wrapInIfNeeded(InputStream arg0) throws IOException {
        return arg0;
    }

    @Override
    public OutputStream wrapOutIfNeeded(OutputStream arg0) {
        return arg0;
    }
}
