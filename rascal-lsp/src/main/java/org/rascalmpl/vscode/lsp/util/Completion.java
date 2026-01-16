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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.parametric.model.RascalADTs.CompletionFields;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class Completion {

    private static final Logger logger = LogManager.getLogger(Completion.class);

    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private static final TypeFactory TF = TypeFactory.getInstance();
    private static final TypeStore store = new TypeStore();

    private final IConstructor invoked;
    private final Function<IString, IConstructor> character;

    public Completion() {
        final var completionTriggerAdt = TF.abstractDataType(store, "CompletionTrigger");
        this.invoked = VF.constructor(TF.constructor(store, completionTriggerAdt, CompletionFields.INVOKED));
        this.character = c -> VF.constructor(TF.constructor(store, completionTriggerAdt, CompletionFields.CHARACTER, TF.stringType(), CompletionFields.TRIGGER), c);
    }

    public List<CompletionItem> toLSP(final IBaseTextDocumentService docService, IList items, String dedicatedLanguageName, String languageName, int editLine, LineColumnOffsetMap offsets) {
        return items.stream()
            .map(IConstructor.class::cast)
            .map(c -> {
                var kws = c.asWithKeywordParameters();
                var ci = new CompletionItem();

                var edit = (IConstructor) c.get(CompletionFields.EDIT);
                ci.setTextEdit(Either.forRight(editToLSP(edit, editLine, offsets)));
                ci.setInsertTextFormat(isSnippet(edit) ? InsertTextFormat.Snippet : InsertTextFormat.PlainText);
                ci.setInsertTextMode(InsertTextMode.AsIs);
                ci.setLabel(((IString) c.get(CompletionFields.LABEL)).getValue());
                ci.setKind(itemKindToLSP((IConstructor) c.get(CompletionFields.KIND)));
                var label = new CompletionItemLabelDetails();
                label.setDetail(getKwParamString(kws, CompletionFields.LABEL_DETAIL, ""));
                label.setDescription(getKwParamString(kws, CompletionFields.LABEL_DESCRIPTION, ""));
                ci.setLabelDetails(label);
                ci.setDetail(getKwParamString(kws, CompletionFields.DETAIL, ""));
                ci.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, getKwParamString(kws, CompletionFields.DOCUMENTATION, "")));
                ci.setSortText(getKwParamString(kws, CompletionFields.SORT_TEXT, ""));
                ci.setFilterText(getKwParamString(kws, CompletionFields.FILTER_TEXT, ""));
                ci.setTags(getKwParamBool(kws, CompletionFields.DEPRECATED, false) ? List.of(CompletionItemTag.Deprecated) : Collections.emptyList());
                ci.setPreselect(getKwParamBool(kws, CompletionFields.PRESELECT, false));
                ci.setCommitCharacters(getKwParamList(kws, CompletionFields.COMMIT_CHARACTERS, VF.list()).stream().map(ch -> ((IString) ch).getValue()).collect(Collectors.toList()));
                ci.setAdditionalTextEdits(DocumentChanges.translateTextEdits(getKwParamList(kws, CompletionFields.ADDITIONAL_CHANGES, VF.list()), docService.getColumnMaps()));
                var command = getCommand(kws, dedicatedLanguageName, languageName);
                if (command != null) {
                    ci.setCommand(command);
                }

                return ci;
            })
            .collect(Collectors.toList());
    }

    private @Nullable Command getCommand(IWithKeywordParameters<? extends IConstructor> kws, String dedicatedLanguageName, String languageName) {
        var command = (IConstructor) kws.getParameter(CompletionFields.COMMAND);
        if (command == null) {
            return null;
        }
        return CodeActions.constructorToCommand(dedicatedLanguageName, languageName, command);
    }

    private InsertReplaceEdit editToLSP(IConstructor edit, int currentLine, LineColumnOffsetMap offsets) {
        var startColumn = ((IInteger) edit.get(CompletionFields.START_COLUMN)).intValue();
        return new InsertReplaceEdit(
            ((IString) edit.get(CompletionFields.NEW_TEXT)).getValue(),
            // Build dummy source locations to convert here. Since we only require line/column information, this is fine.
            Locations.toRange(VF.sourceLocation("unknown", 0, 0, currentLine, currentLine, startColumn, ((IInteger) edit.get(CompletionFields.INSERT_END_COLUMN)).intValue()), offsets),
            Locations.toRange(VF.sourceLocation("unknown", 0, 0, currentLine, currentLine, startColumn, ((IInteger) edit.get(CompletionFields.REPLACE_END_COLUMN)).intValue()), offsets)
        );
    }

    private boolean isSnippet(IConstructor edit) {
        return edit.asWithKeywordParameters().hasParameter(CompletionFields.SNIPPET)
            && ((IBool) edit.asWithKeywordParameters().getParameter(CompletionFields.SNIPPET)).getValue();
    }

    private CompletionItemKind itemKindToLSP(IConstructor kind) {
        var docSymKind = DocumentSymbols.symbolKindToLSP(kind);
        try {
            return CompletionItemKind.valueOf(docSymKind.name());
        } catch (IllegalArgumentException e) {
            logger.warn("Completion has item kind {}, but only values from DocumentSymbolKind are supported.", docSymKind.name());
            return CompletionItemKind.Text;
        }
    }

    private String getKwParamString(IWithKeywordParameters<? extends IConstructor> c, String label, String defaultVal) {
        var param = c.getParameter(label);
        return param instanceof IString ? ((IString) param).getValue() : defaultVal;
    }

    private boolean getKwParamBool(IWithKeywordParameters<? extends IConstructor> c, String label, boolean defaultVal) {
        var param = c.getParameter(label);
        return param instanceof IBool ? ((IBool) param).getValue() : defaultVal;
    }

    private IList getKwParamList(IWithKeywordParameters<? extends IConstructor> c, String label, IList defaultVal) {
        var param = c.getParameter(label);
        return param instanceof IList ? (IList) param : defaultVal;
    }

    public IConstructor triggerKindToRascal(@Nullable CompletionContext context) {
        if (context == null) {
            return invoked;
        }

        switch (context.getTriggerKind()) {
            case Invoked: return invoked;
            case TriggerCharacter: {
                var triggerChar = context.getTriggerCharacter();
                if (triggerChar == null) {
                    throw new IllegalArgumentException("No character given for completion trigger.");
                }
                return character.apply(VF.string(triggerChar));
            }
            default: throw new IllegalArgumentException("Unsupported completion trigger kind: " + context.getTriggerKind());
        }
    }

}
