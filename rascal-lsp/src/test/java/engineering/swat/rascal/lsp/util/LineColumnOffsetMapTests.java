package engineering.swat.rascal.lsp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.impl.ArrayLineOffsetMap;

public class LineColumnOffsetMapTests {
    @Test
    void noUnicodeChars() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("1234\n1234");
        assertEquals(2, map.translateColumn(0, 2, false));
    }

    @Test
    void singleWideChar() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("12🎉45\n1234🎉");
        assertEquals(3, map.translateColumn(0, 3, false));
        assertEquals(4, map.translateColumn(0, 3, true));
        assertEquals(5, map.translateColumn(0, 4, false));
    }


    @Test
    void doubleChars() {
        LineColumnOffsetMap map = ArrayLineOffsetMap.build("12🎉4🎉6\n1234");
        assertEquals(6, map.translateColumn(0, 5, false));
        assertEquals(7, map.translateColumn(0, 5, true));
        assertEquals(8, map.translateColumn(0, 6, false));
    }

}
