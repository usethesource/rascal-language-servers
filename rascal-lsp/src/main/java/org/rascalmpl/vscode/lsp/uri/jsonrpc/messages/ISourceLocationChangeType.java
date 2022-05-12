package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import org.rascalmpl.uri.ISourceLocationWatcher;

public enum ISourceLocationChangeType {
    CREATED(1),
    DELETED(2),
    MODIFIED(3);


    private final int value;

    ISourceLocationChangeType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ISourceLocationChangeType forValue(int value) {
        var allValues = ISourceLocationChangeType.values();
        if (value < 1 || value > allValues.length)
            throw new IllegalArgumentException("Illegal enum value: " + value);
        return allValues[value - 1];
    }

    public static ISourceLocationWatcher.ISourceLocationChangeType translate(
        ISourceLocationChangeType lsp) {
        switch (lsp) {
            case CREATED:
                return ISourceLocationWatcher.ISourceLocationChangeType.CREATED;
            case DELETED:
                return ISourceLocationWatcher.ISourceLocationChangeType.DELETED;
            case MODIFIED:
                return ISourceLocationWatcher.ISourceLocationChangeType.MODIFIED;
            default:
                throw new RuntimeException("Forgotten type: " + lsp);
        }
    }
}
