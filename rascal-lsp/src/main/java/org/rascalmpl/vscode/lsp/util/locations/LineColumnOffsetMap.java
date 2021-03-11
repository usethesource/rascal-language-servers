package org.rascalmpl.vscode.lsp.util.locations;

/**
 * Translate ISourceLocation columns to LSP columns
 *
 * vallang uses UTF-32-bit codepoints, while lsp uses UTF-16, so in cases where a codepoint wouldn't fit inside 16bit char, it takes up two chars. Implementations of this class translate these efficiently.
 */
public interface LineColumnOffsetMap {
    int translateColumn(int line, int column, boolean isEnd);
}
