#!/usr/bin/env bash

set -euxo pipefail

cd rascal-vscode-extension

TEMP1=$(mktemp)
TEMP2=$(mktemp)
cp package.json $TEMP1
cp package-lock.json $TEMP2

VERSION=$(node -p "require('./package.json').version")
PREFIX=$(echo $VERSION | cut -d "-" -f 1)
SUFFIX=$(git log --pretty=format:'%h' -n 1)
npx vsce package $PREFIX-$SUFFIX

cp $TEMP1 package.json
cp $TEMP2 package-lock.json

cd -