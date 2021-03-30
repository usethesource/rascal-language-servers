@bootstrapParser
module lang::rascal::lsp::Outline

import String;
import ParseTree;
import lang::rascal::\syntax::Rascal;
import util::LanguageServer;

list[DocumentSymbol] outlineRascalModule(start[Module] \mod) {
    m= \mod.top;
    children = [];

    top-down-break visit (m) {
        case (Declaration) `<Tags _> <Visibility _> <Type t> <{Variable ","}+ vars>;`:
            children += [symbol(clean("<v.name>"), variable(), v@\loc, detail="variable <t> <v>") | v <- vars];

        case (Declaration) `<Tags _> <Visibility _> anno <Type t> <Type ot>@<Name name>;`:
            children +=  [symbol(clean("<name>"), field(), t@\loc, detail="anno <t> <ot>")];

        case (Declaration) `<Tags _> <Visibility _> alias <UserType u> = <Type al>;`:
            children += [symbol(clean("<u.name>"), struct(), u@\loc, detail="<u> = <al>")];

        case (Declaration) `<Tags _> <Visibility _> tag <Kind k> <Name name> on <{Type ","}+ ts>;`:
            children += [symbol(clean("<name>"), \key(), name@\loc, detail="tag <k> <name> on <ts>")];

        case (Declaration) `<Tags _> <Visibility _> data <UserType u> <CommonKeywordParameters kws>;`: {
            kwlist = [symbol(".<k.name>", property(), k@\loc, detail="<k.\type>") | kws is present, KeywordFormal k <- kws.keywordFormalList];
            children += [symbol("<u.name>", struct(), u@\loc, detail="data <u> <kws>", children=kwlist)];
        }

        case (Declaration) `<Tags _> <Visibility _> data <UserType u> <CommonKeywordParameters kws> = <{Variant "|"}+ variants>;` : {
            kwlist = [symbol(".<k.name>", property(), k@\loc, detail="<k.\type>") | kws is present, KeywordFormal k <- kws.keywordFormalList];
            variantlist = [symbol(clean("<v>"), \constructor(), v@\loc) | v <- variants];

            children += [symbol("<u.name>", struct(), u@\loc, detail="data <u> <kws>", children=kwlist + variantlist)];
        }

        case FunctionDeclaration func :
            children += [symbol("<func.signature.name><func.signature.parameters>", \function(), (func.signature)@\loc, detail="<func.signature.\type>")];

/*
        case (Import) `extend <ImportedModule mm>;` :
            children += [symbol("<mm.name>", \module(), mm@\loc, detail="extend <mm>")];

        case (Import) `import <ImportedModule mm>;` :
            children += [symbol("<mm.name>", \module(), mm@\loc, detail="import <mm>")];

        case (Import) `import <QualifiedName m2> = <LocationLiteral ll>;` :
            children += [symbol("<m2>", \module(), m2@\loc, detail="import <m2>=<ll>")];
*/

        case SyntaxDefinition def : {
            rs = [symbol(clean("<prefix> <p.syms>"), \function(), p@\loc)
                | /Prod p := def.production, p is labeled || p is unlabeled,
                str prefix := (p is labeled ? "<p.name>: " : "")
            ];
            children += [symbol(clean("<def.defined>"), \function(), def@\loc, children=rs)];
        }
    }

    return [symbol(clean("<m.header.name>"), \module(), m.header@\loc, children=children)];
}

// remove leading backslash
str clean(/\\<rest:.*>/) = clean(rest);

str clean("false") = "\\false"; // vscode doesn't like a falsy name

// multi-line becomes single line
str clean(str x:/\n/) = clean(visit(x) { case /\n/ => " " });

// cut-off too long
str clean(str x) = clean(x[..239]) when size(x) > 256;

// done
default str clean(str x) = x;
