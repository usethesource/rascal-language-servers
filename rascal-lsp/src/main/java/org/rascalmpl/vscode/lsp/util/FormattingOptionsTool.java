package org.rascalmpl.vscode.lsp.util;

import java.util.Map;

import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;
import org.eclipse.lsp4j.FormattingOptions;

public class FormattingOptionsTool {

    public static IConstructor translate(FormattingOptions options) {
        TypeStore typeStore = new TypeStore();
        TypeFactory tf = TypeFactory.getInstance();
        IRascalValueFactory VF = IRascalValueFactory.getInstance();

        var optionsType = tf.abstractDataType(typeStore, "FormattingOptions");
        var consType = tf.constructor(typeStore, optionsType, "formattingOptions");
        var opts = Map.of(
            "tabSize", VF.integer(options.getTabSize()),
            "insertSpaces", VF.bool(options.isInsertSpaces()),
            "trimTrailingWhitespace", VF.bool(options.isTrimTrailingWhitespace()),
            "insertFinalNewline", VF.bool(options.isInsertFinalNewline()),
            "trimFinalNewlines", VF.bool(options.isTrimFinalNewlines())
        );
        return VF.constructor(consType, new IValue[0], opts);
    }


}
