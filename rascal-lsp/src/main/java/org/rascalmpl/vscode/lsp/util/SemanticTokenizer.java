package org.rascalmpl.vscode.lsp.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.TokenFormat;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.ProductionAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.values.parsetrees.visitors.TreeVisitor;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IString;

public class SemanticTokenizer implements ISemanticTokens {

    @Override
    public SemanticTokens semanticTokensFull(SemanticTokensParams params, ITree tree) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Either<SemanticTokens, SemanticTokensDelta> semanticTokensFullDelta(SemanticTokensDeltaParams params,
            ITree tree) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SemanticTokens semanticTokensRange(SemanticTokensRangeParams params, ITree tree) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SemanticTokensCapabilities semanticTokenCapabilities() {
        // TODO Auto-generated method stub
        return null;
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

        private static String[] types = new String[] { "Normal", "Type", "Identifier", "Variable", "Constant", "Comment",
                "Todo", "Quote", "MetaAmbiguity", "MetaVariable", "MetaKeyword", "MetaSkipped", "NonterminalLabel",
                "Result", "StdOut", "StdErr" };

        static {
            for (int i = 0; i < types.length; i++) {
                cache.put(types[i], i);
            }
        }

        public static int tokenTypeForName(String category) {
            Integer result = cache.get(category);

            return result != null ? result : 0;
        }

        // TODO: register Token Types with font attributes, with the following defaults:

        // map.put(TreeAdapter.NORMAL, new TextAttribute(null, null, SWT.NONE));
        // map.put(TreeAdapter.NONTERMINAL_LABEL, new TextAttribute(new
        // Color(Display.getDefault(), 0x80, 0x80, 0x80), null, SWT.ITALIC));
        // map.put(TreeAdapter.META_KEYWORD, new TextAttribute(new
        // Color(Display.getDefault(), 123, 0, 82), null, SWT.BOLD));
        // map.put(TreeAdapter.META_VARIABLE, new TextAttribute(new
        // Color(Display.getDefault(), 0x29,0x5F,0x94), null, SWT.ITALIC));
        // map.put(TreeAdapter.META_AMBIGUITY, new TextAttribute(new
        // Color(Display.getDefault(), 186, 29, 29), null, SWT.BOLD));
        // map.put(TreeAdapter.META_SKIPPED, new TextAttribute(null, new
        // Color(Display.getDefault(), 255, 255, 255), SWT.ITALIC)); //82, 141, 115
        // map.put(TreeAdapter.TODO,new TextAttribute(new Color(Display.getDefault(),
        // 123, 157, 198), null, SWT.BOLD));
        // map.put(TreeAdapter.COMMENT,new TextAttribute(new Color(Display.getDefault(),
        // 82, 141, 115), null, SWT.ITALIC));
        // map.put(TreeAdapter.CONSTANT,new TextAttribute(new
        // Color(Display.getDefault(), 139, 0, 139), null, SWT.NONE));
        // map.put(TreeAdapter.VARIABLE,new TextAttribute(new
        // Color(Display.getDefault(), 0x55,0xaa,0x55), null, SWT.NONE));
        // map.put(TreeAdapter.IDENTIFIER,new TextAttribute(new
        // Color(Display.getDefault(), 0x2C,0x57,0x7C), null, SWT.NONE));
        // map.put(TreeAdapter.QUOTE,new TextAttribute(new Color(Display.getDefault(),
        // 255, 69, 0), new Color(Display.getDefault(), 32,178,170), SWT.NONE));
        // map.put(TreeAdapter.TYPE,new TextAttribute(new Color(Display.getDefault(),
        // 0xAB,0x25,0x25), null, SWT.NONE));
        // map.put(TreeAdapter.RESULT, new TextAttribute(new Color(Display.getDefault(),
        // 0x74,0x8B,0x00), new Color(Display.getDefault(), 0xEC, 0xEC, 0xEC),
        // SWT.ITALIC));
        // map.put(TreeAdapter.STDOUT, new TextAttribute(new Color(Display.getDefault(),
        // 0xB3,0xB3,0xB3), null, SWT.ITALIC));
        // map.put(TreeAdapter.STDERR, new TextAttribute(new Color(Display.getDefault(),
        // 0xAF,0x00,0x00), null, SWT.NONE));
    }

    private static class TokenCollector extends TreeVisitor<RuntimeException> {
        private int location;
        private final boolean showAmb = false;
        private TokenList tokens;

        public TokenCollector(TokenList tokens) {
            super();
            this.tokens = tokens;
            location = 0;
        }

        public ITree visitTreeAmb(ITree arg) {
            if (showAmb) {
                int offset = location;
                ISourceLocation ambLoc = TreeAdapter.getLocation(arg);
                int length = ambLoc != null ? ambLoc.getLength() : TreeAdapter.yield(arg).length();

                location += length;
                tokens.addToken(ambLoc.getBeginLine(), ambLoc.getBeginColumn(), ambLoc.getLength(), "MetaAmbiguity");
            } else {
                TreeAdapter.getAlternatives(arg).iterator().next().accept(this);
            }
            return arg;

        }

        public ITree visitTreeAppl(ITree arg) {
            IValue catAnno = arg.asWithKeywordParameters().getParameter("category");
            String category = null;

            if (catAnno != null) {
                category = ((IString) catAnno).getValue();
            }

            IConstructor prod = TreeAdapter.getProduction(arg);
            if (category == null && ProductionAdapter.isDefault(prod)) {
                category = ProductionAdapter.getCategory(prod);
            }

            // It's not so nice to link the sort name to the token color constant ...
            if (TreeAdapter.NONTERMINAL_LABEL.equals(ProductionAdapter.getSortName(prod))) {
                category = TreeAdapter.NONTERMINAL_LABEL;
            }

            // short cut, if we have source locations and a category we found a long token
            ISourceLocation loc = TreeAdapter.getLocation(arg);

            // Always sync location with locs because of concrete syntax stuff in Rascal.
            if (loc != null) {
                location = loc.getOffset();
            }

            if (category != null && loc != null) {
                tokens.addToken(loc.getBeginLine(), loc.getBeginColumn(), loc.getLength(), category);
                location += loc.getLength();
                return arg;
            }

            // now we go down in the tree to find more tokens

            for (IValue child : TreeAdapter.getArgs(arg)) {
                child.accept(this);
            }

            if (ProductionAdapter.isDefault(prod) && (TreeAdapter.isLiteral(arg) || TreeAdapter.isCILiteral(arg))) {
                if (category == null) {
                    category = TreeAdapter.META_KEYWORD;

                    for (IValue child : TreeAdapter.getArgs(arg)) {
                        int c = TreeAdapter.getCharacter((ITree) child);
                        if (c != '-' && !Character.isJavaIdentifierPart(c)) {
                            category = null;
                        }
                    }

                    if (category == null) {
                        category = TreeAdapter.NORMAL;
                    }
                }
            }

            if (category != null && loc != null) {
                tokens.addToken(loc.getBeginLine(), loc.getBeginColumn(), loc.getLength(), category);
            }

            return arg;
        }

        public ITree visitTreeChar(ITree arg) {
            ++location;

            return arg;
        }

        public ITree visitTreeCycle(ITree arg) {
            return arg;
        }
    }
}
