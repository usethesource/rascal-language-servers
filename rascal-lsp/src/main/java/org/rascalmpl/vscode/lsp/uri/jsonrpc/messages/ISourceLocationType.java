/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import org.rascalmpl.uri.ISourceLocationWatcher;

/**
 * @see ISourceLocationWatcher.ISourceLocationType this code is mirroring this type for serialization purposes
 */
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
        if (value < 1 || value > allValues.length) {
            throw new IllegalArgumentException("Illegal enum value: " + value);
        }
        return allValues[value - 1];
    }

    public static ISourceLocationWatcher.ISourceLocationType translate(ISourceLocationType lsp) {
        switch (lsp) {
            case DIRECTORY:
                return ISourceLocationWatcher.ISourceLocationType.DIRECTORY;
            case FILE:
                return ISourceLocationWatcher.ISourceLocationType.FILE;
            default:
                throw new RuntimeException("Forgotten type: " + lsp);
        }
    }
}
