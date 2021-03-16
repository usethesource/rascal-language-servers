module util::IDE

extend util::Reflective;
extend ParseTree;

data Language
  = language(PathConfig pcfg, str name, str extension, str mainModule, str mainFunction);

data Contribution
  = parser(Tree (str input, loc origin) parseFunction)
  // TODO; add all the existing Eclipse contributions here
  ;

@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
java void registerLanguage(Language lang);

