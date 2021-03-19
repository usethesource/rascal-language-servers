@license{
  Copyright (c) 2021 NWO-I Centrum Wiskunde & Informatica
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
@contributor{Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI}
module util::IDE

extend util::Reflective;
// extend Content;
extend ParseTree;

data Language
    = language(PathConfig pcfg, str name, str extension, str mainModule, str mainFunction);

alias Parser        = Tree (str input, loc origin);
alias Summarizer    = Summary (Tree input);
alias Outliner      = list[DocumentSymbol] (Tree input);
alias Annotater     = Tree (Tree input);
alias Completer     = list[Completion] (Tree input, str prefix, int requestOffset);

@synopsis{Each kind of contribution contibutes the implementation of one (or several) IDE features.}
data Contribution
    = parser(Parser parser)
    | summarizer(Summarizer summarizer)
    | outliner(Outliner outliner)
    | completer(Completer completer)
    | command(Command command)
    ;

@synopsis{Annotations that an annotator may provide on a parse tree node}
data Tree(
    set[Message] messages      = {}, // error messages associated with a tree node
    set[str]     documentation = {}, // documentation strings associated with a tree node
    set[loc]     references    = {}, // this nodes points to these references
    str          category      = ""  // semantic highlighting category
);

@synopsis{A model encodes all IDE-relevant information about a single source file.}
data Summary = summary(loc src
    rel[loc, Message] messages = {},
    rel[loc, str]     documentation = {},
    rel[loc, loc]     references = {},
    lrel[loc, str]    categories = []
);

data Completion = completion(str newText, str proposal=newText);

@synopsis{produces a summarizer from an annotator by collecting all relevant information from a source Tree}
Contribution summarizer(Annotater annotater) = summarizer(Summary (Tree input) {
    messages = {};
    documentation = {};
    references = {};
    categories = [];

    visit(annotater(input)) {
        case Tree t: if (t.src?) {
            messages      += {<t.src, m> | m <- t.messages};
            documentation += {<t.src, d> | d <- t.documentation};
            references    += {<t.src, r> | r <- t.references};
            categories    += [<t.src, t.category> | t.category?];
        }
    }

    return summary(input.src,
        messages=messages,
        documentation=documentation,
        references=references
    );
});

// THERE is a bug in the interpreter that lets this function fail
// @synopsis{Produces a parser contribution from a reified grammar}
// Contribution parserFor(type[Tree] grammar) = parser(Tree (str input, loc src) {
//     return parse(grammar, input, src);
// });

@synopsis{DocumentSymbol encodes a sorted and hierarchical outline of a source file}
data DocumentSymbol 
    = symbol(
        str name, 
        DocumentSymbolKind kind, 
        loc range, 
        loc selection=range, 
        str detail=name, 
        list[DocumentSymbol] children=[]
    );

data DocumentSymbolKind 
	= \file()
	| \module()
	| \namespace()
	| \package()
	| \class()
	| \method()
	| \property()
	| \field()
	| \constructor()
	| \enum()
	| \interface()
	| \function()
	| \variable()
	| \constant()
	| \string()
	| \number()
	| \boolean()
	| \array()
	| \object()
	| \key()
	| \null()
	| \enumMember()
	| \struct()
	| \event()
	| \operator()
	| \typeParameter()
    ;

data DocumentSymbolTag
    = \deprecated()
    ;

data CompletionProposal = sourceProposal(str newText, str proposal=newText);
    
data Command 
    = action(str label, void (Tree tree, loc selection) action)
    // | interaction(str label, Content (Tree tree, loc selection) server)
    | action(str label, void (str selStr, loc selLoc) handler) 
    | toggle(str label, bool() state, void(Tree tree, loc selection) action)
    | edit(str label, str (Tree tree, loc selection) edit)
    | group(str label, list[Command] members)
    | menu(str label, list[Command] members)
    | popup(str label, list[Command] members)
    ;

@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
java void registerLanguage(Language lang);
