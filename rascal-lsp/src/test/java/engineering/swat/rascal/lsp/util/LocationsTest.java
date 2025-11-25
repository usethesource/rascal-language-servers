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
import static org.junit.Assert.fail;

import java.net.URISyntaxException;
import org.junit.Test;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

public class LocationsTest {

    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private String fileName = "PNG.bird";

    private static ColumnMaps columns(String contents) {
        return new ColumnMaps(f -> contents);
    }

    @Test
    public void roundtripWindowsEmptyLine() {
        var contents = "module images::PNG\r\n" + //
                        "\r\n" + //
                        "struct Signature {\r\n" + //
                        "    u8 _ ?(== 0x89)\r\n" + //
                        "    byte[] _[3] ?(== \"PNG\")\r\n" + //
                        "    byte[] _[4] ?(== <0x0d, 0x0a, 0x1a, 0x0a>)\r\n" + //
                        "}\r\n" + //
                        "";
        var columns = columns(contents);
        var in = VF.sourceLocation(fileName, 22, 119, 3, 7, 0, 1); // the structure
        var out = Locations.setRange(in, Locations.toRange(in, columns), columns);
        assertEquals(in, out);
    }

    @Test
    public void roundtripWindows() {
        var contents = "module images::PNG\r\n" + //
                        "struct Signature {\r\n" + //
                        "    u8 _ ?(== 0x89)\r\n" + //
                        "    byte[] _[3] ?(== \"PNG\")\r\n" + //
                        "    byte[] _[4] ?(== <0x0d, 0x0a, 0x1a, 0x0a>)\r\n" + //
                        "}\r\n" + //
                        "";
        var columns = columns(contents);
        var in = VF.sourceLocation(fileName, 20, 119, 2, 6, 0, 1); // the structure
        var out = Locations.setRange(in, Locations.toRange(in, columns), columns);
        assertEquals(in, out);
    }

    @Test
    public void roundtripUnixEmptyLine() {
        var contents = "module images::PNG\n" + //
                        "\n" + //
                        "struct Signature {\n" + //
                        "    u8 _ ?(== 0x89)\n" + //
                        "    byte[] _[3] ?(== \"PNG\")\n" + //
                        "    byte[] _[4] ?(== <0x0d, 0x0a, 0x1a, 0x0a>)\n" + //
                        "}\n" + //
                        "";
        var columns = columns(contents);
        var in = VF.sourceLocation(fileName, 20, 115, 3, 7, 0, 1); // the structure
        var out = Locations.setRange(in, Locations.toRange(in, columns), columns);
        assertEquals(in, out);
    }

    @Test
    public void roundtripUnix() {
        var contents = "module images::PNG\n" + //
                        "struct Signature {\n" + //
                        "    u8 _ ?(== 0x89)\n" + //
                        "    byte[] _[3] ?(== \"PNG\")\n" + //
                        "    byte[] _[4] ?(== <0x0d, 0x0a, 0x1a, 0x0a>)\n" + //
                        "}\n" + //
                        "";
        var columns = columns(contents);
        var in = VF.sourceLocation(fileName, 19, 115, 2, 6, 0, 1); // the structure
        var out = Locations.setRange(in, Locations.toRange(in, columns), columns);
        assertEquals(in, out);
    }

    private void roundtripUri(String uri) {
        var loc = Locations.toLoc(uri);
        var uri2 = Locations.toUri(loc);
        assertEquals(uri, uri2);
    }

    @Test
    public void roundtripAbsoluteUris() {
        roundtripUri("file:///foo/bar");
        roundtripUri("file:///foo/bar%20baz.txt");
        roundtripUri("unknown:///foo/bar");
        roundtripUri("untitled://foo/bar");
        roundtripUri("untitled:///foo/bar");
        roundtripUri("memory:///foo/bar");
    }

    @Test
    public void illegalUris() {
        for (var uri : new String[] {"file:///foo/bar baz.txt"}) {
            try {
                var loc = Locations.toLoc(uri);
                fail("Mapped invalid URI " + uri + " to " + loc + ", but should have failed");
            } catch (RuntimeException e) {
                if (!(e.getCause() instanceof URISyntaxException)) {
                    fail("Expected excepted caused by URISyntaxException, but got " + e.getCause());
                }
            }
        }
    }

    @Test
    public void roundtripOpaqueUris() {
        // New, unsaved file from VS Code
        roundtripUri("untitled:Untitled-1");
        roundtripUri("untitled:foo/bar/Untitled-1"); // separators in scheme-specific part
        roundtripUri("untitled:Untitled%201"); // encoded characters
        roundtripUri("untitled:Untitled-1#header1"); // with fragment

        // Examples from Java documentation (https://docs.oracle.com/javase/8/docs/api/java/net/URI.html)
        roundtripUri("mailto:java-net@java.sun.com");
        roundtripUri("news:comp.lang.java");
        roundtripUri("urn:isbn:096139210x");
    }
}
