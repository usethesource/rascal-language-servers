#! /bin/sh

set -e
set -x

cd rascal-vscode-extension
vsce package
cd ..

