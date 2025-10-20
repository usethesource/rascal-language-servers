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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.Test;

public class StringByteUtilsTests {

    private Random r = new Random();

    private static boolean isValid(int c) {
        var tp = Character.getType(c);
        return Character.isValidCodePoint(c)
            && tp != Character.PRIVATE_USE
            && tp != Character.SURROGATE
            && tp != Character.UNASSIGNED
            ;

    }

    private String generate() {
        var length = r.nextDouble() > 0.8 ? r.nextInt(10000) : r.nextInt(40);
        var codepoints = IntStream.range(0, length)
            .map(_ignored -> {
                if (r.nextDouble() < 0.8) {
                    return r.nextInt(255);
                }
                int codepoint = Character.MAX_CODE_POINT + 1;
                while (!isValid(codepoint)) {
                    codepoint = r.nextInt(Character.MAX_CODE_POINT);
                }
                return codepoint;
            })
            .toArray();
        return new String(codepoints, 0, codepoints.length);
    }

    private static final Charset[] CS = new Charset[] { StandardCharsets.UTF_8, StandardCharsets.UTF_16 };

    @Test
    public void bytesAreTheSame() throws IOException {
        for (int i = 0; i < 1000; i++) {
            var s = generate();
            for (var cs: CS) {
                var expected = s.getBytes(cs);
                var actual = StringByteUtils.streamingBytes(s, cs).readAllBytes();
                assertArrayEquals("String of size " + s.length() + " with encoding " + cs, expected, actual);
            }
        }
    }

    @Test
    public void byteCountsAreTheSame() {
        for (int i = 0; i < 1000; i++) {
            var s = generate();
            for (var cs: CS) {
                var expected = s.getBytes(cs).length;
                var actual = StringByteUtils.byteCount(s, cs);
                assertEquals("String of size " + s.length() + " with encoding " + cs, expected, actual);
            }
        }
    }
}
