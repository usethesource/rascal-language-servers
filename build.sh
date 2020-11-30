#! /bin/sh

set -e
set -x

cd rascal-lsp
mvn package

cd ..
cd rascal-vscode-extension
npm rebuild
npm run lsp4j:package
npm install
cd ..
