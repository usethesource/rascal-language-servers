module lang::rascal::lsp::Outliner

import util::LanguageServer;

list[DocumentSymbol] outlineRascalModule(start[Module] m) {
   children = [];

   top-down-break visit (m) {
     case (Declaration) `<Tags _> <Visibility _> <Type t> <{Variable ","}+ vars>;`:
       children += [symbol(clean("<v.name> <t>"), variable(), v@\loc) | v <- vars];

     case (Declaration) `<Tags _> <Visibility _> anno <Type t> <Type ot>@<Name name>;`:
       children +=  [symbol(clean("<name> <t> <ot>@<name>"), field(), name@\loc)];

     case (Declaration) `<Tags _> <Visibility _> alias <UserType u> = <Type _>;`:
       children += [symbol(clean("<u.name>"), \type(), u@\loc)];

     case (Declaration) `<Tags _> <Visibility _> tag <Kind _> <Name name> on <{Type ","}+ _>;`:
       children += [symbol(clean("<name>"), \key(), name@\loc)];

     case (Declaration) `<Tags _> <Visibility _> data <UserType u> <CommonKeywordParameters kws>;`: {

       kwlist += [symbol(".<k.name> <k.\type>", field(), k@\loc) | kws is present, KeywordFormal k <- kws.keywordFormalList];
       children += [symbol("<u.name>", class(), u@\loc, children=kwlist)];
     }

     case (Declaration) `<Tags _> <Visibility _> data <UserType u> <CommonKeywordParameters kws> = <{Variant "|"}+ variants>;` : {
        kwlist += [symbol(".<k.name> <k.\type>", field(), k@\loc) | kws is present, KeywordFormal k <- kws.keywordFormalList];
        variantlist = [symbol(clean("<v>"), function(), v@\loc) | v <- variants];

        children += [symbol("<u.name>", class(), u@\loc, children=kwlist + variantlist)];
     }

     case FunctionDeclaration func :
        children += [symbol("<func.signature.name> <func.signature.parameters>", \function(), (func.signature)@\loc)];

    //  case (Import) `extend <ImportedModule mm>;` :
    //    imports += ["<mm.name>"()[@\loc=mm@\loc]];

    //  case (Import) `import <ImportedModule mm>;` :
    //    imports += ["<mm.name>"()[@\loc=mm@\loc]];

    //  case (Import) `import <QualifiedName m2> = <LocationLiteral _>;` :
    //    imports += ["<m2>"()[@\loc=m2@\loc]];

    //  case SyntaxDefinition def : {
    //    f = "<def.defined>";
    //    c = grammars[f]?e;
    //    c += ["<p>"()[@label="<prefix><p.syms>"][@\loc=p@\loc]
    //                 | /Prod p := def.production, p is labeled || p is unlabeled,
    //                   str prefix := (p is labeled ? "<p.name>: " : "")
    //                 ];
    //    grammars[f] = c;
    //  }
   }

    return [symbol("<m.header.name>", \module(), m.header@\loc, children=children)];
}

// remove leading backslash
str clean(/\\<rest:.*>/) = clean(rest);

// multi-line becomes single line
str clean(str x:/\n/) = clean(visit(x) { case /\n/ => " " });

// cut-off too long
str clean(str x) = clean(x[..239]) when size(x) > 256;

// done
default str clean(str x) = x;
