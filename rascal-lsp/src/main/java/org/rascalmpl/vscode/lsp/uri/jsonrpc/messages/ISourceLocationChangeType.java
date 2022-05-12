package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

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

    public static org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType translate(
        ISourceLocationChangeType lsp) {
        switch (lsp) {
            case CREATED:
                return org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType.CREATED;
            case DELETED:
                return org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType.DELETED;
            case MODIFIED:
                return org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType.MODIFIED;
            default:
                throw new RuntimeException("Forgotten type: " + lsp);
        }
    }
}
