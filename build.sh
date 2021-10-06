#! /bin/sh

set -e
set -x

cd rascal-lsp; 
mvn clean package
cd ..

cd rascal-vscode-extension
npm run lsp4j:package
cd ..

