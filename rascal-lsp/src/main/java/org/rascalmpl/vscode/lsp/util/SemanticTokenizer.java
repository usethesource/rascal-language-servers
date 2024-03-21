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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
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

    private static final Logger logger = LogManager.getLogger();

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
        cps.setAugmentsSyntaxTokens(false); // ignore tokens from client side
        return cps;
    }

    private static class TokenList {
        private final List<Integer> theList = new ArrayList<>(500);
        private int previousLine = 0;
        private int previousStart = 0;

        public List<Integer> getTheList() {
            return Collections.unmodifiableList(theList);
        }

        public void addToken(int startLine, int startColumn, int length, @Nullable String category) {
            int tokenCategory = category == null ? -1 : TokenTypes.tokenTypeForName(category);
            if (tokenCategory != -1) {
                // https://microsoft.github.io/language-server-protocol/specifications/specification-3-16/#textDocument_semanticTokens
                theList.add(startLine - previousLine);
                theList.add(startLine == previousLine ? startColumn - previousStart : startColumn);
                theList.add(length);
                theList.add(tokenCategory);
                theList.add(0); // no support for modifiers yet
                previousLine = startLine;
                previousStart = startColumn;
            }
        }
    }

    private static class TokenTypes {
        private static final Map<String, Integer> cache = new HashMap<>();

        /** Rascal extension to LSP categories, these require custom definitions in clients to work properly, so avoid them if possible */
        public static final String AMBIGUITY = "ambiguity";

        private static String[][] backwardsCompatibleTokenTypes = new String[][] {
            /**
             * The Rascal legacy token types are translated to
             * textmate token types here
             */
            //{ TreeAdapter.NORMAL,               null },
            { TreeAdapter.TYPE,                 SemanticTokenTypes.Type},
            { TreeAdapter.IDENTIFIER,           SemanticTokenTypes.Variable},
            { TreeAdapter.VARIABLE,             SemanticTokenTypes.Variable },
            { TreeAdapter.CONSTANT,             SemanticTokenTypes.String }, // it's a choice between number and string, both are wrong
            { TreeAdapter.COMMENT,              SemanticTokenTypes.Comment },
            { TreeAdapter.TODO,                 SemanticTokenTypes.Comment },
            { TreeAdapter.QUOTE,                SemanticTokenTypes.String },
            { TreeAdapter.META_AMBIGUITY,       AMBIGUITY }, // rascal extension
            { TreeAdapter.META_VARIABLE,        SemanticTokenTypes.Variable },
            { TreeAdapter.META_KEYWORD,         SemanticTokenTypes.Keyword },
            { TreeAdapter.META_SKIPPED,         SemanticTokenTypes.String },
            { TreeAdapter.NONTERMINAL_LABEL,    SemanticTokenTypes.Variable },
            { TreeAdapter.RESULT,               SemanticTokenTypes.String},
            { TreeAdapter.STDOUT,               SemanticTokenTypes.String},
            { TreeAdapter.STDERR,               SemanticTokenTypes.String},
            /**
             * For a while, rascal-lsp was supporting these textmate cateogries as TokenTypes.
             * That was an incorrect implementation of the feature, see issue #366.
             *
             * But to remain backwards compatible, we've tried to map most of them
             */
            // The first block is what we minally need to get good highlighting support
            {"entity.name", SemanticTokenTypes.Class },
            {"entity.other.inherited-class", SemanticTokenTypes.Class },
            {"entity.name.section", SemanticTokenTypes.Type },
            {"entity.name.tag", SemanticTokenTypes.Decorator },
            {"entity.other.attribute-name", SemanticTokenTypes.Decorator },
            // {"variable", SemanticTokenTypes.Variable }, // same as LSP
            {"variable.language", SemanticTokenTypes.Variable },
            {"variable.parameter", SemanticTokenTypes.Parameter },
            {"variable.function", SemanticTokenTypes.Function },
            {"constant", SemanticTokenTypes.Number },
            {"constant.numeric", SemanticTokenTypes.Number },
            {"constant.language", SemanticTokenTypes.Keyword },
            {"constant.character.escape", SemanticTokenTypes.String },
            {"storage.type", SemanticTokenTypes.Keyword },
            {"storage.modifier", SemanticTokenTypes.Modifier },
            {"support", SemanticTokenTypes.Keyword },
            //{"keyword", SemanticTokenTypes.Keyword }, // same as LPS
            {"keyword.control", SemanticTokenTypes.Keyword },
            {"keyword.operator", SemanticTokenTypes.Operator },
            {"keyword.declaration", SemanticTokenTypes.Keyword },
            //{"string", SemanticTokenTypes.String },  same LSP
            {"comment", SemanticTokenTypes.Comment },
            //{"invalid", null },
            //{"invalid.deprecated", null },

            // this second block is all additional semantic information which
            // can slightly improve the editing experience, but may also lead
            // a to X-mas tree
            {"comment.block.documentation", SemanticTokenTypes.Comment },
            {"comment.block", SemanticTokenTypes.Comment },
            {"comment.single", SemanticTokenTypes.Comment },
            {"comment", SemanticTokenTypes.Comment },
            {"constant.character.escape", SemanticTokenTypes.String },
            {"constant.language", SemanticTokenTypes.Keyword },
            {"constant.numeric.complex.imaginary", SemanticTokenTypes.Number },
            {"constant.numeric.complex.real", SemanticTokenTypes.Number },
            {"constant.numeric.complex", SemanticTokenTypes.Number },
            {"constant.numeric.float.binary", SemanticTokenTypes.Number },
            {"constant.numeric.float.decimal", SemanticTokenTypes.Number },
            {"constant.numeric.float.hexadecimal", SemanticTokenTypes.Number },
            {"constant.numeric.float.octal", SemanticTokenTypes.Number },
            {"constant.numeric.float.other", SemanticTokenTypes.Number },
            {"constant.numeric.float", SemanticTokenTypes.Number },
            {"constant.numeric.integer.binary", SemanticTokenTypes.Number },
            {"constant.numeric.integer.decimal", SemanticTokenTypes.Number },
            {"constant.numeric.integer.hexadecimal", SemanticTokenTypes.Number },
            {"constant.numeric.integer.octal", SemanticTokenTypes.Number },
            {"constant.numeric.integer.other", SemanticTokenTypes.Number },
            {"constant.numeric.integer", SemanticTokenTypes.Number },
            {"constant.numeric", SemanticTokenTypes.Number },
            {"constant.other.placeholder", SemanticTokenTypes.Number },
            {"constant.other", SemanticTokenTypes.Number },
            {"constant", SemanticTokenTypes.Number },
            {"entity.name.class.forward-decl", SemanticTokenTypes.Class },
            {"entity.name.class", SemanticTokenTypes.Class },
            {"entity.name.constant", SemanticTokenTypes.Property },
            {"entity.name.enum", SemanticTokenTypes.Enum },
            {"entity.name.function.constructor", SemanticTokenTypes.Function },
            {"entity.name.function.destructor", SemanticTokenTypes.Function },
            {"entity.name.function", SemanticTokenTypes.Function },
            //{"entity.name.impl", null },
            {"entity.name.interface", SemanticTokenTypes.Interface },
            {"entity.name.label", SemanticTokenTypes.Variable },
            {"entity.name.namespace", SemanticTokenTypes.Namespace },
            {"entity.name.section", SemanticTokenTypes.Type },
            {"entity.name.struct", SemanticTokenTypes.Struct },
            {"entity.name.tag", SemanticTokenTypes.Decorator },
            {"entity.name.trait", SemanticTokenTypes.TypeParameter },
            {"entity.name.type", SemanticTokenTypes.Type },
            {"entity.name.union", SemanticTokenTypes.Struct },
            {"entity.name", SemanticTokenTypes.Type },
            {"entity.other.attribute-name", SemanticTokenTypes.Decorator },
            {"entity.other.inherited-class", SemanticTokenTypes.Class },
            {"entity", SemanticTokenTypes.Type },
            //{"invalid.deprecated", null },
            //{"invalid.illegal", null },
            //{"invalid", null },
            {"keyword.control.conditional", SemanticTokenTypes.Keyword },
            {"keyword.control.import", SemanticTokenTypes.Keyword },
            {"keyword.control", SemanticTokenTypes.Keyword },
            {"keyword.declaration.class", SemanticTokenTypes.Modifier },
            {"keyword.declaration.enum", SemanticTokenTypes.Modifier },
            {"keyword.declaration.function", SemanticTokenTypes.Modifier },
            {"keyword.declaration.impl", SemanticTokenTypes.Modifier },
            {"keyword.declaration.interface", SemanticTokenTypes.Modifier },
            {"keyword.declaration.struct", SemanticTokenTypes.Modifier },
            {"keyword.declaration.trait", SemanticTokenTypes.Modifier },
            {"keyword.declaration.type", SemanticTokenTypes.Modifier },
            {"keyword.declaration.union", SemanticTokenTypes.Modifier },
            {"keyword.declaration", SemanticTokenTypes.Modifier },
            {"keyword.operator.arithmetic", SemanticTokenTypes.Operator },
            {"keyword.operator.assignment", SemanticTokenTypes.Operator },
            {"keyword.operator.bitwise", SemanticTokenTypes.Operator },
            {"keyword.operator.logical", SemanticTokenTypes.Operator },
            {"keyword.operator.word", SemanticTokenTypes.Operator },
            {"keyword.operator", SemanticTokenTypes.Operator },
            {"keyword.other", SemanticTokenTypes.Keyword },
            {"keyword", SemanticTokenTypes.Keyword },
            // {"markup.bold", null },
            // {"markup.deleted", null },
            // {"markup.heading", null },
            // {"markup.inserted", null },
            // {"markup.italic", null },
            // {"markup.list.numbered", null },
            // {"markup.list.unnumbered", null },
            // {"markup.other", null },
            // {"markup.quote", null },
            // {"markup.raw.block", null },
            // {"markup.raw.inline", null },
            // {"markup.underline.link", null },
            // {"markup.underline", null },
            // {"markup", null },
            // most meta are overlying scopes, but Semantic Tokens are not allowed to be overlapping
            // {"meta.annotation.identifier", SemanticTokenTypes.Decorator },
            // {"meta.annotation.parameters", SemanticTokenTypes.Parameter },
            // {"meta.annotation", SemanticTokenTypes.Decorator },
            // {"meta.block", null },
            // {"meta.braces", null },
            // {"meta.brackets", null },
            // {"meta.class", null },
            // {"meta.enum", null },
            // {"meta.function-call", null },
            // {"meta.function.parameters", null },
            // {"meta.function.return-type", null },
            // {"meta.function", null },
            // {"meta.group", null },
            // {"meta.impl", null },
            // {"meta.interface", null },
            // {"meta.interpolation", null },
            // {"meta.namespace", null },
            // {"meta.paragraph", null },
            // {"meta.parens", null },
            // {"meta.path", null },
            // {"meta.preprocessor", null },
            // {"meta.string", null },
            // {"meta.struct", null },
            // {"meta.tag", null },
            // {"meta.toc-list", null },
            // {"meta.trait", null },
            // {"meta.type", null },
            // {"meta.union", null },
            // {"meta", null },
            // NO support for punctuation
            // {"punctuation.accessor", null },
            // {"punctuation.definition.annotation", null },
            // {"punctuation.definition.comment", null },
            // {"punctuation.definition.keyword", null },
            // {"punctuation.definition.string.begin", null },
            // {"punctuation.definition.string.end", null },
            // {"punctuation.definition.variable", null },
            // {"punctuation.section.block.begin", null },
            // {"punctuation.section.block.end", null },
            // {"punctuation.section.braces.begin", null },
            // {"punctuation.section.braces.end", null },
            // {"punctuation.section.brackets.begin", null },
            // {"punctuation.section.brackets.end", null },
            // {"punctuation.section.group.begin", null },
            // {"punctuation.section.group.end", null },
            // {"punctuation.section.interpolation.begin", null },
            // {"punctuation.section.interpolation.end", null },
            // {"punctuation.section.parens.begin", null },
            // {"punctuation.section.parens.end", null },
            // {"punctuation.separator.continuation", null },
            // {"punctuation.separator", null },
            // {"punctuation.terminator", null },
            //{"source", null },
            {"storage.modifier", SemanticTokenTypes.Modifier },
            {"storage.type.class", SemanticTokenTypes.Keyword },
            {"storage.type.enum", SemanticTokenTypes.Keyword },
            {"storage.type.function", SemanticTokenTypes.Keyword },
            {"storage.type.impl", SemanticTokenTypes.Keyword },
            {"storage.type.interface", SemanticTokenTypes.Keyword },
            {"storage.type.struct", SemanticTokenTypes.Keyword },
            {"storage.type.trait", SemanticTokenTypes.Keyword },
            {"storage.type.union", SemanticTokenTypes.Keyword },
            {"storage.type", SemanticTokenTypes.Keyword },
            {"string.quoted.double", SemanticTokenTypes.String },
            {"string.quoted.other", SemanticTokenTypes.String },
            {"string.quoted.single", SemanticTokenTypes.String },
            {"string.quoted.triple", SemanticTokenTypes.String },
            {"string.regexp", SemanticTokenTypes.Regexp },
            {"string.unquoted", SemanticTokenTypes.String },
            // {"string", SemanticTokenTypes.String }, // same as LSP
            {"support.class", SemanticTokenTypes.Class },
            {"support.constant", SemanticTokenTypes.Keyword },
            {"support.function", SemanticTokenTypes.Function },
            {"support.module", SemanticTokenTypes.Namespace },
            {"support.type", SemanticTokenTypes.Type },
            {"text.html", SemanticTokenTypes.String },
            {"text.xml", SemanticTokenTypes.String },
            {"text", SemanticTokenTypes.String },
            {"variable.annotation", SemanticTokenTypes.Variable },
            {"variable.function", SemanticTokenTypes.Variable },
            {"variable.language", SemanticTokenTypes.Keyword },
            {"variable.other.constant", SemanticTokenTypes.Variable },
            {"variable.other.member", SemanticTokenTypes.Variable },
            {"variable.other.readwrite", SemanticTokenTypes.Variable },
            {"variable.other", SemanticTokenTypes.Variable },
            {"variable.parameter", SemanticTokenTypes.Parameter },
        };

        private static Stream<String> getPublicStaticFieldValues(Class<?> cls) {
            return Arrays.stream(SemanticTokenTypes.class.getFields())
                .filter(f -> f.getType() == String.class && Modifier.isStatic(f.getModifiers()))
                .map(f -> {
                    try {
                        return (String)(f.get(null));
                    } catch (Throwable e) {
                        logger.error("We could not get field {} from {}", f, cls, e);
                        return null;
                    }
                })
                .filter(f -> f != null)
                ;
        }



        private static final String[] rascalExtensions = new String[] {
            AMBIGUITY
        };


        private static final List<String> actualTokenTypes = Stream.concat(
            getPublicStaticFieldValues(SemanticTokenTypes.class), Arrays.stream(rascalExtensions))
            .collect(Collectors.toUnmodifiableList());

        static {

            for (int i = 0; i < actualTokenTypes.size(); i++) {
                cache.put(actualTokenTypes.get(i), i);
            }



            // now map the legacy ones
            for (String[] mapped: backwardsCompatibleTokenTypes) {
                Integer ref = cache.get(mapped[1]);
                if (ref == null) {
                    logger.error("Invalid mapping of backwards compatible tokens: {} to {}", mapped[0], mapped[1]);
                    continue;
                }
                cache.put(mapped[0], ref);
            }
        }


        public static List<String> getTokenTypes() {
            return actualTokenTypes;
        }


        public static List<String> getTokenModifiers() {
            return Collections.emptyList();
        }

        public static int tokenTypeForName(String category) {
            Integer result = cache.get(category);
            return result != null ? result : -1;
        }

    }

    private static class TokenCollector {
        private int line;
        private int column;
        private int startLineCurrentToken;
        private int startColumnCurrentToken;
        private String currentTokenCategory;

        private final boolean showAmb = false;
        private TokenList tokens;

        public TokenCollector(TokenList tokens) {
            this.tokens = tokens;
            line = 0;
            column = 0;
        }

        public void collect(ITree tree) {
            collect(tree, null);
            //check for final token
            if (column > startColumnCurrentToken) {
                tokens.addToken(startLineCurrentToken, startColumnCurrentToken, column - startColumnCurrentToken, currentTokenCategory);
            }
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

            IValue catParameter = arg.asWithKeywordParameters().getParameter("category");

            if (catParameter != null) {
                category = ((IString) catParameter).getValue();
            }

            IConstructor prod = TreeAdapter.getProduction(arg);

            if (category == null && ProductionAdapter.isDefault(prod)) {
                category = ProductionAdapter.getCategory(prod);
            }

            if (category == null && currentCategory == null && (ProductionAdapter.isLiteral(prod) || ProductionAdapter.isCILiteral(prod))) {
                category = SemanticTokenTypes.Keyword;

                // unless this is an operator
                for (IValue child : TreeAdapter.getArgs(arg)) {
                    int c = TreeAdapter.getCharacter((ITree) child);
                    if (c != '-' && !Character.isJavaIdentifierPart(c)) {
                        category = null;
                    }
                }
            }

            // now we go down in the tree to find more tokens and to advance the counters
            for (IValue child : TreeAdapter.getArgs(arg)) {
                //Propagate current category to child unless currently in a syntax nonterminal
                //*AND* the current child is a syntax nonterminal too
                if (TreeAdapter.isAmb((ITree)child)) {
                    collect((ITree) child, TokenTypes.AMBIGUITY);
                }
                else if (!TreeAdapter.isChar((ITree) child) && ProductionAdapter.isSort(prod) &&
                        ProductionAdapter.isSort(TreeAdapter.getProduction((ITree) child))) {
                    collect((ITree) child, null);
                } else {
                    collect((ITree) child, category != null ? category : currentCategory);
                }
            }
        }

        private void collectAmb(ITree arg, @Nullable String currentCategory) {
            if (showAmb) {
                startLineCurrentToken = line;
                startColumnCurrentToken = column;

                collect((ITree) TreeAdapter.getAlternatives(arg).iterator().next(), TokenTypes.AMBIGUITY);

                tokens.addToken(startLineCurrentToken, startColumnCurrentToken, column - startColumnCurrentToken, TokenTypes.AMBIGUITY);
            } else {
                collect((ITree) TreeAdapter.getAlternatives(arg).iterator().next(), currentCategory);
            }
        }

        private void collectChar(ITree ch, @Nullable String currentCategory) {
            int currentChar = TreeAdapter.getCharacter(ch);
            //First check whether the token category has changed
            if (currentCategory == null && currentTokenCategory != null) {
                //character has no semantic category, but there is a running token
                if (column > startColumnCurrentToken) {
                    //add token and set column offset
                    tokens.addToken(startLineCurrentToken, startColumnCurrentToken, column - startColumnCurrentToken, currentTokenCategory);
                    startColumnCurrentToken = column;
                    //startLineCurrentToken remains unchanged
                }
                currentTokenCategory = currentCategory;
            }
            if (currentCategory != null && !currentCategory.equals(currentTokenCategory)) {
                //character has a semantic category that doesn't match the running token
                if (column > startColumnCurrentToken) {
                    //add token and set column offset
                    tokens.addToken(startLineCurrentToken, startColumnCurrentToken, column - startColumnCurrentToken, currentTokenCategory);
                    startColumnCurrentToken = column;
                }
                currentTokenCategory = currentCategory;
                //startLineCurrentToken remains unchanged
            }

            //Token administration done, advance column/line counters
            if (currentChar == '\n') {
                line++;

                // this splits multi-line tokens automatically across the lines
                if (currentTokenCategory != null) {
                    if (column > startColumnCurrentToken) {
                        tokens.addToken(startLineCurrentToken, startColumnCurrentToken, column - startColumnCurrentToken, currentTokenCategory);
                    }
                }
                startColumnCurrentToken = 0;
                startLineCurrentToken = line;
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
