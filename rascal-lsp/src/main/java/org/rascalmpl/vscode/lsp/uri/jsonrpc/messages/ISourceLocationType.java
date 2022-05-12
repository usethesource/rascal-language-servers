package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;


public enum ISourceLocationType {
    FILE(1),
    DIRECTORY(2);


    private final int value;

    ISourceLocationType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ISourceLocationType forValue(int value) {
        var allValues = ISourceLocationType.values();
        if (value < 1 || value > allValues.length)
            throw new IllegalArgumentException("Illegal enum value: " + value);
        return allValues[value - 1];
    }

    public static org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationType translate(ISourceLocationType lsp) {
        switch (lsp) {
            case DIRECTORY:
                return org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationType.DIRECTORY;
            case FILE:
                return org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationType.FILE;
            default:
                throw new RuntimeException("Forgotten type: " + lsp);
        }
    }
}
