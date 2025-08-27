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
package org.rascalmpl.vscode.lsp.uri;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Internal class used for converting to bytes without allocating a full byte array in between.
 */
/*package*/ class StringByteUtils {
    private StringByteUtils() {}

    private static final int BUFFER_SIZE = 8 * 1024;

    private static int estimateBufferSize(String s, CharsetEncoder enc) {
        var size = s.length() * enc.averageBytesPerChar();
        if (size >= BUFFER_SIZE) {
            return BUFFER_SIZE;
        }
        else if (size < 16) {
            return 16;
        }
        else {
            return (int)Math.ceil(size);
        }
    }

    static InputStream streamingBytes(String s, Charset cs) {
        if (s.isEmpty()) {
            return InputStream.nullInputStream();
        }
        var enc = cs.newEncoder();
        var cb = CharBuffer.wrap(s);
        var bb = ByteBuffer.allocate(estimateBufferSize(s, enc));
        bb.limit(0); // mark the buffer as having no remaining bytes to start with
        return new InputStream() {
            private boolean checkAvailable() {
                if (!bb.hasRemaining()) {
                    bb.clear();
                    enc.encode(cb, bb, true);
                    bb.flip();
                    return bb.hasRemaining();
                }
                return true;

            }
            @Override
            public int read() throws IOException {
                if (!checkAvailable()) {
                    return -1;
                }
                return bb.get() & 0xFF;

            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) {
                    return 0;
                }
                if (!checkAvailable()) {
                    return -1;
                }
                Objects.checkFromIndexSize(off, len, b.length);
                int remaining = len;
                while (remaining > 0 && checkAvailable()) {
                    int chunk = Math.min(remaining, bb.remaining());
                    bb.get(b, off, chunk);
                    off += chunk;
                    remaining -= chunk;
                }
                return len - remaining;

            }
        };
    }

    /** avoid allocating the full byte array just to count them */
    static int byteCount(String s, Charset cs) {
        if (s.isEmpty()) {
            return 0;
        }
        if (cs.equals(StandardCharsets.UTF_8)) {
            return bytesUTF8(s);
        }
        else if (cs.equals(StandardCharsets.UTF_16)) {
            return bytesUTF16(s);
        }
        var enc = cs.newEncoder();
        var cb = CharBuffer.wrap(s);
        var bb = ByteBuffer.allocate(estimateBufferSize(s, enc));
        int size = 0;
        var state = CoderResult.OVERFLOW;
        while (state == CoderResult.OVERFLOW) {
            bb.clear();
            state = enc.encode(cb, bb, true);
            size += bb.position();
        }
        return size;
    }

    private static int bytesUTF8(String s) {
        return s.codePoints()
            .map((int c) -> {
                // utf8 encodes:
                // - first 7 bits in 1 byte
                // - first 11bits in 2 bytes
                // - first 16bits in 3 bytes
                // - the rest (24bit) in 4 bytes
                // so we just chop of bits untill we have no more bytes left
                c >>>= 7;
                if (c == 0) {
                    return 1;
                }
                c >>>= 4;
                if (c == 0) {
                    return 2;
                }
                c >>>= 5;
                if (c == 0) {
                    return 3;
                }
                return 4;
            })
            .sum();
    }

    private static int bytesUTF16(String s) {
        return 2 /* BOM */ + s.codePoints()
            .map(c -> Character.isBmpCodePoint(c) ? 2 : 4)
            .sum();
    }

}
