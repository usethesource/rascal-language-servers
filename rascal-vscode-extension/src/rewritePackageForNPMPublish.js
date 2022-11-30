const fs = require("fs");

let packageContents = JSON.parse(fs.readFileSync("package.json"));

// first we remove some stuff
delete packageContents.contributes;
delete packageContents.categories;
delete packageContents.activationEvents;


// then we change some stuff
packageContents["name"] = "@usethesource/rascal-vscode-dsl-runtime";
packageContents["displayName"] = "Rascal Metaprogramming Language - VSCode DSL Runtime library";
packageContents["description"] = "This package is intended for if you want to release your Rascal DSL as a separate VS Code extension, you are responsible for the initial setup, but this package will provide you a LSP server ready for action.";

// make new scripts block with just the things we need to publish to npm
packageContents.scripts = {
  "precompile" : packageContents.scripts["lsp4j:package"],
  "compile" : packageContents.scripts["compile-lib"],
  "prepare": "npm run build",
};

// we add wich files we actually want to add
packageContents["main"] = "lib/lsp/ParameterizedLanguageServer.js";
packageContents["types"] = "lib/lsp/ParameterizedLanguageServer.d.ts";
packageContents["files"] = ["lib/**/*", "assets/**/*"];

fs.writeFileSync("package.json", JSON.stringify(packageContents, null, 2));
