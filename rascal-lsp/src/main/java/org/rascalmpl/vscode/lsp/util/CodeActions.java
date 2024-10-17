/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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

import java.util.Arrays;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.parametric.model.RascalADTs;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IWithKeywordParameters;

/**
 * Reusable utilities for code actions and commands (maps between Rascal and LSP world)
 */
public class CodeActions {
    public static CodeAction constructorToCodeAction(IBaseTextDocumentService doc, String dedicatedLanguageName, String languageName, IConstructor codeAction) {
        IWithKeywordParameters<?> kw = codeAction.asWithKeywordParameters();
        IConstructor command = (IConstructor) kw.getParameter(RascalADTs.CodeActionFields.COMMAND);
        IString title = (IString) kw.getParameter(RascalADTs.CodeActionFields.TITLE);
        IList edits = (IList) kw.getParameter(RascalADTs.CodeActionFields.EDITS);
        IConstructor kind = (IConstructor) kw.getParameter(RascalADTs.CodeActionFields.KIND);

        // first deal with the defaults. Must mimick what's in util::LanguageServer with the `data CodeAction` declaration
        if (title == null) {
            if (command != null) {
                title = (IString) command.asWithKeywordParameters().getParameter(RascalADTs.CommandFields.TITLE);
            }

            if (title == null) {
                title = IRascalValueFactory.getInstance().string("");
            }
        }

        CodeAction result = new CodeAction(title.getValue());

        if (command != null) {
            result.setCommand(constructorToCommand(dedicatedLanguageName, languageName, command));
        }

        if (edits != null) {
            result.setEdit(new WorkspaceEdit(DocumentChanges.translateDocumentChanges(doc, edits)));
        }

        result.setKind(constructorToCodeActionKind(kind));

        return result;
    }

    /**
     * Translates `refactor(inline())` to `"refactor.inline"` and `empty()` to `""`, etc.
     * `kind == null` signals absence of the optional parameter. This is factorede into
     * this private function because otherwise every call has to check it.
     */
    private static String constructorToCodeActionKind(@Nullable IConstructor kind) {
        if (kind == null) {
            return CodeActionKind.QuickFix;
        }

        String name = kind.getName();

        if (name.isEmpty()) {
            return "";
        }
        else if (name.length() == 1) {
            return name.toUpperCase();
        }
        else if ("empty".equals(name)) {
            return "";
        }
        else {
            var kw = kind.asWithKeywordParameters();
            for (String kwn : kw.getParameterNames()) {
                String nestedName = constructorToCodeActionKind((IConstructor) kw.getParameter(kwn));
                name = name + (nestedName.isEmpty() ? "" : ("." + nestedName));
            }
        }

        return name;
    }

    public static Command constructorToCommand(String dedicatedLanguageName, String languageName, IConstructor command) {
        IWithKeywordParameters<?> kw = command.asWithKeywordParameters();
        IString possibleTitle = (IString) kw.getParameter(RascalADTs.CommandFields.TITLE);

        return new Command(possibleTitle != null ? possibleTitle.getValue() : command.toString(), getRascalMetaCommandName(dedicatedLanguageName), Arrays.asList(languageName, command.toString()));
    }

    public static String getRascalMetaCommandName(String dedicatedLanguageName) {
        // if we run in dedicated mode, we prefix the commands with our language name
        // to avoid ambiguity with other dedicated languages and the generic rascal plugin
        if (!dedicatedLanguageName.isEmpty()) {
            return BaseWorkspaceService.RASCAL_META_COMMAND + "-" + dedicatedLanguageName;
        }
        return BaseWorkspaceService.RASCAL_META_COMMAND;
    }
}
