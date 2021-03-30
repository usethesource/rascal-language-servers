#! /bin/sh

set -e
set -x

cd rascal-vscode-extension
npx vsce package
cd ..

