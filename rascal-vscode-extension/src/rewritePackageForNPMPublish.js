/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
  "compile" : packageContents.scripts["compile-lib"],
  "prepare": "npm run compile",
};

// we add wich files we actually want to add
packageContents["main"] = "lib/lsp/library.js";
packageContents["types"] = "lib/lsp/library.d.ts";
packageContents["files"] = ["lib/**/*", "assets/**/*"];

fs.writeFileSync("package.json", JSON.stringify(packageContents, null, 2));
