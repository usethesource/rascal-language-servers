package org.rascalmpl.vscode.lsp.util;

import org.apache.logging.log4j.Logger;
import org.rascalmpl.debug.IRascalMonitor;

import io.usethesource.vallang.ISourceLocation;

public class LoggingMonitor implements IRascalMonitor {
    private final Logger target;

    public LoggingMonitor(Logger target) {
        this.target = target;
    }

    @Override
    public void startJob(String name) {
        target.trace(name);
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        target.warn("{} : {}", src, message);
    }

    @Override
    public void startJob(String name, int totalWork) {
        startJob(name);
    }

    @Override
    public void startJob(String name, int workShare, int totalWork) {
        startJob(name);
    }

    @Override
    public void event(String name) {
        // ignore
    }

    @Override
    public void event(String name, int inc) {
        // ignore
    }

    @Override
    public void event(int inc) {
        // ignore
    }

    @Override
    public int endJob(boolean succeeded) {
        return 0;
    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public void todo(int work) {
        // ignore
    }
}
