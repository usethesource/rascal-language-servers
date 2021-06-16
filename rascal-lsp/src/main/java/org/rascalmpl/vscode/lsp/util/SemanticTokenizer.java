/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.ProductionAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;

public class SemanticTokenizer implements ISemanticTokens {
    private static final Logger logger = LogManager.getLogger(SemanticTokenizer.class);

    @Override
    public SemanticTokens semanticTokensFull(ITree tree) {
        TokenList tokens = new TokenList();
        new TokenCollector(tokens).collect(tree);
        return new SemanticTokens(tokens.getTheList());
    }

    @Override
    public Either<SemanticTokens, SemanticTokensDelta> semanticTokensFullDelta(String previousId, ITree tree) {
       return Either.forLeft(semanticTokensFull(tree));
    }

    @Override
    public SemanticTokens semanticTokensRange(Range range, ITree tree) {
        return semanticTokensFull(tree);
    }

    @Override
    public SemanticTokensWithRegistrationOptions options() {
        SemanticTokensWithRegistrationOptions result = new SemanticTokensWithRegistrationOptions();
        SemanticTokensLegend legend = new SemanticTokensLegend(TokenTypes.getTokenTypes(), TokenTypes.getTokenModifiers());

        result.setFull(true);
        result.setLegend(legend);

        return result;
    }

    @Override
    public SemanticTokensCapabilities capabilities() {
        SemanticTokensClientCapabilitiesRequests requests = new SemanticTokensClientCapabilitiesRequests(true);
        SemanticTokensCapabilities cps = new SemanticTokensCapabilities(
            requests,
            TokenTypes.getTokenTypes(),
            Collections.emptyList(),
            Collections.emptyList());

        cps.setMultilineTokenSupport(true);

        return cps;
    }

    private static class TokenList {
        List<Integer> theList = new ArrayList<>(500);
        int previousLine = 0;
        int previousStart = 0;

        public List<Integer> getTheList() {
            return Collections.unmodifiableList(theList);
        }

        public void addToken(int startLine, int startColumn, int length, String category) {
            // https://microsoft.github.io/language-server-protocol/specifications/specification-3-16/#textDocument_semanticTokens
            theList.add(startLine - previousLine);
            theList.add(startLine == previousLine ? startColumn - previousStart : startColumn);
            theList.add(length);
            theList.add(TokenTypes.tokenTypeForName(category));
            theList.add(0); // no support for modifiers yet
            previousLine = startLine;
            previousStart = startColumn;
        }
    }

    private static class TokenTypes {
        private static final Map<String, Integer> cache = new HashMap<>();

        /**
         * The Rascal legacy token types are translated to
         * textmate token types here
         */
        private static String[][] rascalCategories = new String[][] {
            { TreeAdapter.NORMAL,               "source" },
            { TreeAdapter.TYPE,                 "storage.type"},
            { TreeAdapter.IDENTIFIER,           "variable"},
            { TreeAdapter.VARIABLE,             "variable" },
            { TreeAdapter.CONSTANT,             "constant" },
            { TreeAdapter.COMMENT,              "comment" },
            { TreeAdapter.TODO,                 "comment" },
            { TreeAdapter.QUOTE,                "meta.string" },
            { TreeAdapter.META_AMBIGUITY,       "invalid" },
            { TreeAdapter.META_VARIABLE,        "variable" },
            { TreeAdapter.META_KEYWORD,         "keyword.other" },
            { TreeAdapter.META_SKIPPED,         "invalid" },
            { TreeAdapter.NONTERMINAL_LABEL,    "variable.parameter"},
            { TreeAdapter.RESULT,               "text"},
            { TreeAdapter.STDOUT,               "text"},
            { TreeAdapter.STDERR,               "text"}
        };

        /**
         * These should be _all_ textmate token categories that exist
         * See <https://www.sublimetext.com/docs/3/scope_naming.html>.
         */
        private static String[] textmateCategories = new String[] {
            // The first block is what we minally need to get good
            // highlighting support
            "entity.name",
            "entity.other.inherited-class",
            "entity.name.section",
            "entity.name.tag",
            "entity.other.attribute-name",
            "variable",
            "variable.language",
            "variable.parameter",
            "variable.function",
            "constant",
            "constant.numeric",
            "constant.language",
            "constant.character.escape",
            "storage.type",
            "storage.modifier",
            "support",
            "keyword",
            "keyword.control",
            "keyword.operator",
            "keyword.declaration",
            "string",
            "comment",
            "invalid",
            "invalid.deprecated",

            // this second block is all additional semantic information which
            // can slightly improve the editing experience, but may also lead
            // a to X-mas tree
            "comment.block.documentation",
            "comment.block",
            "comment.single",
            "comment",
            "constant.character.escape",
            "constant.language",
            "constant.numeric.complex.imaginary",
            "constant.numeric.complex.real",
            "constant.numeric.complex",
            "constant.numeric.float.binary",
            "constant.numeric.float.decimal",
            "constant.numeric.float.hexadecimal",
            "constant.numeric.float.octal",
            "constant.numeric.float.other",
            "constant.numeric.float",
            "constant.numeric.integer.binary",
            "constant.numeric.integer.decimal",
            "constant.numeric.integer.hexadecimal",
            "constant.numeric.integer.octal",
            "constant.numeric.integer.other",
            "constant.numeric.integer",
            "constant.numeric",
            "constant.other.placeholder",
            "constant.other",
            "constant",
            "entity.name.class.forward-decl",
            "entity.name.class",
            "entity.name.constant",
            "entity.name.enum",
            "entity.name.function.constructor",
            "entity.name.function.destructor",
            "entity.name.function",
            "entity.name.impl",
            "entity.name.interface",
            "entity.name.label",
            "entity.name.namespace",
            "entity.name.section",
            "entity.name.struct",
            "entity.name.tag",
            "entity.name.trait",
            "entity.name.type",
            "entity.name.union",
            "entity.name",
            "entity.other.attribute-name",
            "entity.other.inherited-class",
            "entity",
            "invalid.deprecated",
            "invalid.illegal",
            "invalid",
            "keyword.control.conditional",
            "keyword.control.import",
            "keyword.control",
            "keyword.declaration.class",
            "keyword.declaration.enum",
            "keyword.declaration.function",
            "keyword.declaration.impl",
            "keyword.declaration.interface",
            "keyword.declaration.struct",
            "keyword.declaration.trait",
            "keyword.declaration.type",
            "keyword.declaration.union",
            "keyword.declaration",
            "keyword.operator.arithmetic",
            "keyword.operator.assignment",
            "keyword.operator.bitwise",
            "keyword.operator.logical",
            "keyword.operator.word",
            "keyword.operator",
            "keyword.other",
            "keyword",
            "markup.bold",
            "markup.deleted",
            "markup.heading",
            "markup.inserted",
            "markup.italic",
            "markup.list.numbered",
            "markup.list.unnumbered",
            "markup.other",
            "markup.quote",
            "markup.raw.block",
            "markup.raw.inline",
            "markup.underline.link",
            "markup.underline",
            "markup",
            "meta.annotation.identifier",
            "meta.annotation.parameters",
            "meta.annotation",
            "meta.block",
            "meta.braces",
            "meta.brackets",
            "meta.class",
            "meta.enum",
            "meta.function-call",
            "meta.function.parameters",
            "meta.function.return-type",
            "meta.function",
            "meta.group",
            "meta.impl",
            "meta.interface",
            "meta.interpolation",
            "meta.namespace",
            "meta.paragraph",
            "meta.parens",
            "meta.path",
            "meta.preprocessor",
            "meta.string",
            "meta.struct",
            "meta.tag",
            "meta.toc-list",
            "meta.trait",
            "meta.type",
            "meta.union",
            "meta",
            "punctuation.accessor",
            "punctuation.definition.annotation",
            "punctuation.definition.comment",
            "punctuation.definition.keyword",
            "punctuation.definition.string.begin",
            "punctuation.definition.string.end",
            "punctuation.definition.variable",
            "punctuation.section.block.begin",
            "punctuation.section.block.end",
            "punctuation.section.braces.begin",
            "punctuation.section.braces.end",
            "punctuation.section.brackets.begin",
            "punctuation.section.brackets.end",
            "punctuation.section.group.begin",
            "punctuation.section.group.end",
            "punctuation.section.interpolation.begin",
            "punctuation.section.interpolation.end",
            "punctuation.section.parens.begin",
            "punctuation.section.parens.end",
            "punctuation.separator.continuation",
            "punctuation.separator",
            "punctuation.terminator",
            "source",
            "storage.modifier",
            "storage.type.class",
            "storage.type.enum",
            "storage.type.function",
            "storage.type.impl",
            "storage.type.interface",
            "storage.type.struct",
            "storage.type.trait",
            "storage.type.union",
            "storage.type",
            "storage.type",
            "string.quoted.double",
            "string.quoted.other",
            "string.quoted.single",
            "string.quoted.triple",
            "string.regexp",
            "string.unquoted",
            "string",
            "support.class",
            "support.constant",
            "support.function",
            "support.module",
            "support.type",
            "text.html",
            "text.xml",
            "text",
            "variable.annotation",
            "variable.function",
            "variable.language",
            "variable.other.constant",
            "variable.other.member",
            "variable.other.readwrite",
            "variable.other",
            "variable.parameter",
        };

        static {
            // the cache translates Rascal token types to textmate token indexes
            for (int i = 0; i < rascalCategories.length; i++) {
                cache.put(rascalCategories[i][0], indexOf(rascalCategories[i][1], textmateCategories));
            }

            // and the cache also translates the normal textmate token types to token indexes
            for (int i = 0; i < textmateCategories.length; i++) {
                cache.put(textmateCategories[i], i);
            }
        }

        public static List<String> getTokenTypes() {
            return Arrays.asList(textmateCategories);
        }

        private static Integer indexOf(String needle, String[] haystack) {
            for (int i = 0; i < haystack.length; i++) {
                if (needle.equals(haystack[i])) {
                    return i;
                }
            }

            throw new RuntimeException("this should not happen: " + needle + " not found?!?");
        }

        public static List<String> getTokenModifiers() {
            return Collections.emptyList();
        }

        public static int tokenTypeForName(String category) {
            Integer result = cache.get(category);

            return result != null ? result : 0;
        }
    }

    private static class TokenCollector {
        private int line;
        private int column;
        private int startLineCurrentToken;
        private int startColumnCurrentToken;

        private final boolean showAmb = false;
        private TokenList tokens;

        public TokenCollector(TokenList tokens) {
            super();
            this.tokens = tokens;
            line = 0;
            column = 0;
        }

        public void collect(ITree tree) {
            collect(tree, null);
        }

        private void collect(ITree tree, @Nullable String currentCategory) {
            if (tree.isAppl()) {
                collectAppl(tree, currentCategory);
            }
            else if (tree.isAmb()) {
                collectAmb(tree, currentCategory);
            }
            else if (tree.isChar()) {
                collectChar(tree, currentCategory);
            }
        }

        @SuppressWarnings("java:S3776") // parsing tends to be complex
        private void collectAppl(ITree arg, @Nullable String currentCategory) {
            String category = null;

            if (currentCategory == null) {
                IValue catAnno = arg.asWithKeywordParameters().getParameter("category");

                if (catAnno != null) {
                    category = ((IString) catAnno).getValue();
                }

                IConstructor prod = TreeAdapter.getProduction(arg);

                if (category == null && ProductionAdapter.isDefault(prod)) {
                    category = ProductionAdapter.getCategory(prod);
                }

                if (category == null && (ProductionAdapter.isLiteral(prod) || ProductionAdapter.isCILiteral(prod))) {
                    category = "keyword.other";

                    // unless this is an operator
                    for (IValue child : TreeAdapter.getArgs(arg)) {
                        int c = TreeAdapter.getCharacter((ITree) child);
                        if (c != '-' && !Character.isJavaIdentifierPart(c)) {
                            category = null;
                        }
                    }
                }
            }

            if (category != null && currentCategory == null) {
                startLineCurrentToken = line;
                startColumnCurrentToken = column;
            }

            // now we go down in the tree to find more tokens and to advance the counters
            for (IValue child : TreeAdapter.getArgs(arg)) {
                collect((ITree) child, currentCategory != null ? currentCategory : category);
            }

            if (category != null && currentCategory == null) {
                tokens.addToken(startLineCurrentToken, startColumnCurrentToken, column - startColumnCurrentToken, category);
            }
        }

        private void collectAmb(ITree arg, @Nullable String currentCategory) {
            if (showAmb) {
                startLineCurrentToken = line;
                startColumnCurrentToken = column;

                collect((ITree) TreeAdapter.getAlternatives(arg).iterator().next(), "MetaAmbiguity");

                tokens.addToken(startLineCurrentToken, startColumnCurrentToken, column - startColumnCurrentToken, "MetaAmbiguity");
            } else {
                collect((ITree) TreeAdapter.getAlternatives(arg).iterator().next(), currentCategory);
            }
        }

        private void collectChar(ITree ch, @Nullable String currentCategory) {
            int currentChar = TreeAdapter.getCharacter(ch);
            if (currentChar == '\n') {
                line++;

                // this splits multi-line tokens automatically across the lines
                if (currentCategory != null) {
                    tokens.addToken(startLineCurrentToken, startColumnCurrentToken, column - startColumnCurrentToken, currentCategory);
                    startColumnCurrentToken = 0;
                    startLineCurrentToken = line;
                }
                column = 0;

            }
            else if (Character.isSupplementaryCodePoint(currentChar)) {
                column += 2; // lsp counts 16-bit chars instead of 32bit codepoints
            }
            else {
                column++;
            }
        }
    }
}
