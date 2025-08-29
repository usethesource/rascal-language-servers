package org.rascalmpl.vscode.lsp.util;

import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CallHierarchyItem;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class CallHierarchy {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private static final TypeFactory TF = TypeFactory.getInstance();
    private static final TypeStore store = new TypeStore();

    private static final Type directionAdt = TF.abstractDataType(store, "CallDirection");

    public static final IConstructor INCOMING = VF.constructor(TF.constructor(store, directionAdt, "incoming"));
    public static final IConstructor OUTGOING = VF.constructor(TF.constructor(store, directionAdt, "outgoing"));

    private static final Type callHierarchyItemAdt = TF.abstractDataType(store, "CallHierarchyItem");
    private static final Type callHierarchyItemCons = TF.constructor(store, callHierarchyItemAdt, "callHierarchyItem",
        TF.stringType(), DocumentSymbols.symbolKindAdt, TF.sourceLocationType(), TF.sourceLocationType());

    private static final String NAME = "name";
    private static final String KIND = "kind";
    private static final String DEFINITION = "src";
    private static final String SELECTION = "selection";
    private static final String TAGS = "tags";
    private static final String DETAIL = "detail";
    private static final String DATA = "data";

    private CallHierarchy() { /* hide constructor */}

    public static CallHierarchyItem toLSP(IConstructor cons, ColumnMaps columns) {
        var name = cons.get(NAME).toString();
        var kind = DocumentSymbols.symbolKindToLSP((IConstructor) cons.get(KIND));
        var def = (ISourceLocation) cons.get(DEFINITION);
        var definitionRange = Locations.toRange(def, columns);
        var selection = (ISourceLocation) cons.get(SELECTION);
        var selectionRange = Locations.toRange(selection, columns);

        var ci = new CallHierarchyItem(name, kind, def.top().getURI().toString(), definitionRange, selectionRange);
        var kws = cons.asWithKeywordParameters();
        ci.setTags(DocumentSymbols.symbolTagsToLSP((ISet) kws.getParameter(TAGS)));
        ci.setDetail(kws.getParameter(DETAIL).toString());
        ci.setData(kws.getParameter(DATA));

        return ci;
    }

    public static IConstructor toRascal(CallHierarchyItem ci, ColumnMaps columns) {
        return VF.constructor(callHierarchyItemCons, List.of(
            VF.string(ci.getName()),
            VF.constructor(TF.constructor(store, DocumentSymbols.symbolKindAdt, ci.getKind().name())),
            Locations.toSourceLocation(Locations.toLoc(ci.getUri()), ci.getRange(), columns),
            Locations.toSourceLocation(Locations.toLoc(ci.getUri()), ci.getSelectionRange(), columns)
        ).toArray(new IValue[0]), Map.of(
            TAGS, DocumentSymbols.symbolTagsToRascal(ci.getTags()),
            DETAIL, VF.string(ci.getDetail()),
            DATA, (IValue) ci.getData()
        ));
    }
}
