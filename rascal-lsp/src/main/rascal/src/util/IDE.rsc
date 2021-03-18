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
extend util::Content;
extend ParseTree;

data Language
    = language(PathConfig pcfg, str name, str extension, str mainModule, str mainFunction);

alias Parser = Tree (str input, loc origin);
alias Summarizer = Summary (Tree input);
alias Outliner = Outline (Tree input);
alias Annotater = Tree (Tree input);
alias Completer = list[Completion] (Tree input, str prefix, int requestOffset);

@synopsis{Each kind of contributions contibutes a language processing feature to an IDE}
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
    set[loc]     references    = {}  // this nodes points to these references
);

@synopsis{A model encodes all IDE-relevant information about a single source file.}
data Summary = summary(loc src
    rel[loc, Message] messages = {},
    rel[loc, str]     documentation = {},
    rel[loc, loc]     references = {}
);

data Completion = completion(str newText, str proposal=newText);

@synopsis{produces a summarizer from an annotator by collecting all relevant information from a source Tree}
Contribution summarizer(Annotater annotater) = summarizer(Summary (Tree input) {
    messages = {};
    documentation = {};
    references = {};

    visit(annotater(input)) {
        case Tree t: if (t.src?) {
            messages      += {<t.src, m> | m <- t.messages};
            documentation += {<t.src, d> | d <- t.documentation};
            references    += {<t.src, r> | r <- t.references};
        }
    }

    return summary(input.src,
        messages=messages,
        documentation=documentation,
        references=references
    );
});

@synopsis{Produces a parser contribution from a reified grammar}
Contribution parserFor(type[Tree] grammar) = parser(Tree (str input, loc src) {
    return parse(grammar, input, src);
});

@synopsis{Outline encodes a sorted and hierarchical outline of a source file}
data Outline
    = group(str label, list[Outline] members)
    | item(str label, loc src)
    ;

data CompletionProposal = sourceProposal(str newText, str proposal=newText);
    
data Command 
    = action(str label, void (Tree tree, loc selection) action)
    | interaction(str label, Content (Tree tree, loc selection) server)
    | action(str label, void (str selStr, loc selLoc) handler) 
    | toggle(str label, bool() state, void(Tree tree, loc selection) action)
    | edit(str label, str (Tree tree, loc selection) edit)
    | group(str label, list[Command] members)
    | menu(str label, list[Command] members)
    | popup(str label, list[Command] members)
    ;

@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
java void registerLanguage(Language lang);
