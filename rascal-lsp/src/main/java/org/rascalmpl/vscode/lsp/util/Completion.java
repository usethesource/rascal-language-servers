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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class Completion {
    private static final String KIND = "kind";
    private static final String EDIT = "edit";
    private static final String LABEL = "label";
    private static final String LABEL_DETAIL = "labelDetail";
    private static final String LABEL_DESCRIPTION = "labelDescription";
    private static final String DETAILS = "details";
    private static final String DOCUMENTATION = "documentation";
    private static final String SORT_TEXT = "sortText";
    private static final String FILTER_TEXT = "filterText";
    private static final String DEPRECATED = "deprecated";
    private static final String PRESELECT = "preselect";
    private static final String COMMIT_CHARACTERS = "commitCharacters";
    private static final String ADDITIONAL_CHANGES = "additionalChanges";
    private static final String COMMAND = "command";

    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();

    private final IConstructor invoked;
    private final Function<IString, IConstructor> character;

    private TypeStore store;

    public Completion() {
        this.store = new TypeStore();

        final var TF = TypeFactory.getInstance();

        final var completionTriggerAdt = TF.abstractDataType(store, "CompletionTrigger");
        this.invoked = VF.constructor(TF.constructor(store, completionTriggerAdt, "invoked"));
        this.character = c -> VF.constructor(TF.constructor(store, completionTriggerAdt, "character", TF.tupleType(TF.stringType())), c);
    }

    public List<CompletionItem> toLSP(final IBaseTextDocumentService docService, IList items, String dedicatedLanguageName, String languageName) {
        return items.stream()
            .map(IConstructor.class::cast)
            .map(c -> {
                var ci = new CompletionItem();
                ci.setKind(itemKindToLSP((IConstructor) c.get(KIND)));
                ci.setTextEdit(editToLSP((IConstructor) c.get(EDIT)));
                ci.setLabel(((IString) c.get(LABEL)).getValue());

                var details = new CompletionItemLabelDetails();
                details.setDetail(getKwParamString(c, LABEL_DETAIL));
                details.setDescription(getKwParamString(c, LABEL_DESCRIPTION));
                ci.setLabelDetails(details);

                var kws = c.asWithKeywordParameters();

                ci.setDetail(getKwParamString(c, DETAILS));
                ci.setDocumentation(getKwParamString(c, DOCUMENTATION)); // TODO Do we support MD here?
                ci.setSortText(getKwParamString(c, SORT_TEXT));
                ci.setFilterText(getKwParamString(c, FILTER_TEXT));
                ci.setTags(getKwParamBool(c, DEPRECATED) ? List.of(CompletionItemTag.Deprecated) : Collections.emptyList());
                ci.setPreselect(getKwParamBool(c, PRESELECT));
                ci.setCommitCharacters(getKwParamList(c, COMMIT_CHARACTERS)
                    .map(IString.class::cast)
                    .map(IString::getValue)
                    .collect(Collectors.toList()));

                var wsEdit = DocumentChanges.translateDocumentChanges(docService, (IList) kws.getParameter(ADDITIONAL_CHANGES));
                ci.setAdditionalTextEdits(wsEdit.getDocumentChanges()
                    .stream()
                    .map(e -> (TextDocumentEdit) e.get())
                    .flatMap(t -> t.getEdits().stream())
                    .collect(Collectors.toList()));

                ci.setCommand(CodeActions.constructorToCommand(dedicatedLanguageName, languageName, (IConstructor) kws.getParameter(COMMAND)));

                return ci;
            })
            .collect(Collectors.toList());
    }

    private Either<TextEdit, InsertReplaceEdit> editToLSP(final TypeStore store, IConstructor iConstructor) {
        var completionEditAdt = Objects.requireNonNull(
            store.lookupAbstractDataType("CompletionEdit"),
            "No `data CompletionEdit` defined in environment.");
        var completionEditCons = Objects.requireNonNull(
            store.lookupConstructor(completionEditAdt, "completionEdit", TF.tupleType(TF.integerType(), TF.integerType(), TF.integerType(), TF.stringType())),
            "No constructor `data CompletionEdit = completionEdit(int, int, int, str)` defined in environment.");
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'editToLSP'");
    }

    private CompletionItemKind itemKindToLSP(IConstructor iConstructor) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'itemKindToLSP'");
    }

    private String getKwParamString(IConstructor c, String label) {
        var param = c.asWithKeywordParameters().getParameter(label);
        if (param instanceof IString) {
            return ((IString) param).getValue();
        }

        throw new IllegalArgumentException(String.format("Constructor has no keyword argument '%s' of type `str`", label));
    }

    private boolean getKwParamBool(IConstructor c, String label) {
        var param = c.asWithKeywordParameters().getParameter(label);
        if (param instanceof IBool) {
            return ((IBool) param).getValue();
        }

        throw new IllegalArgumentException(String.format("Constructor has no keyword argument '%s' of type `bool`", label));
    }

    private Stream<? extends IValue> getKwParamList(IConstructor c, String label) {
        var param = c.asWithKeywordParameters().getParameter(label);
        if (param instanceof IList) {
            return ((IList) param).stream();
        }

        throw new IllegalArgumentException(String.format("Constructor has no keyword argument '%s' of type `bool`", label));
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

    public static CompletableFuture<Boolean> isTriggered(IConstructor kind, CompletableFuture<IList> triggerChars) {
        if (kind.getName() == "invoked") {
            return CompletableFuture.completedFuture(true);
        }

        if (kind.getName() == "character" && kind.has("trigger")) {
            var trigger = kind.get("trigger");
            return triggerChars.thenApply(chars -> chars.contains(trigger));
        }

        return CompletableFuture.completedFuture(false);
    }
}
