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
