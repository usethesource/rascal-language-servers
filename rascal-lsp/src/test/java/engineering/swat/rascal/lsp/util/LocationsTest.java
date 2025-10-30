package engineering.swat.rascal.lsp.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

public class LocationsTest {

    @Test
    public void roundtrip() {
        var VF = IRascalValueFactory.getInstance();
        var fileName = "PNG.bird";
        var contents = "module images::PNG\r\n" + //
                        "\r\n" + //
                        "struct Signature {\r\n" + //
                        "    u8 _ ?(== 0x89)\r\n" + //
                        "    byte[] _[3] ?(== \"PNG\")\r\n" + //
                        "    byte[] _[4] ?(== <0x0d, 0x0a, 0x1a, 0x0a>)\r\n" + //
                        "}\r\n" + //
                        "";
        var columns = new ColumnMaps(f -> contents);
        var in = VF.sourceLocation(fileName, 22, 119, 3, 7, 0, 1); // the structure
        var range = Locations.toRange(in, columns);
        var out = Locations.setRange(in, range, columns);
        assertEquals(in, out);
    }
}
