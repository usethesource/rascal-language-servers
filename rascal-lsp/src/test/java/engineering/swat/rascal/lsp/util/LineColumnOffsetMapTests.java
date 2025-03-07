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
package engineering.swat.rascal.lsp.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.impl.ArrayLineOffsetMap;

public class LineColumnOffsetMapTests {
    @Test
    public void noUnicodeChars() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("1234\n1234");
        assertEquals(2, map.translateColumn(0, 2, false));
    }

    @Test
    public void singleWideChar() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("12🎉45\n1234🎉");
        assertEquals(3, map.translateColumn(0, 3, false));
        assertEquals(4, map.translateColumn(0, 3, true));
        assertEquals(5, map.translateColumn(0, 4, false));
    }


    @Test
    public void doubleChars() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("12🎉4🎉6\n1234");
        assertEquals(6, map.translateColumn(0, 5, false));
        assertEquals(7, map.translateColumn(0, 5, true));
        assertEquals(8, map.translateColumn(0, 6, false));
    }

    @Test
    public void noUnicodeCharsInverse() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("1234\n1234");
        assertEquals(2, map.translateInverseColumn(0, 2, false));
    }

    @Test
    public void singleWideCharInverse() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("12🎉45\n1234🎉");
        assertEquals(3, map.translateInverseColumn(0, 3, false));
        assertEquals(3, map.translateInverseColumn(0, 4, false));
        assertEquals(4, map.translateInverseColumn(0, 5, false));
    }


    @Test
    public void doubleCharsInverse() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("12🎉4🎉6\n1234");
        assertEquals(5, map.translateInverseColumn(0, 6, false));
        assertEquals(5, map.translateInverseColumn(0, 7, true));
        assertEquals(6, map.translateInverseColumn(0, 8, false));
    }

}
