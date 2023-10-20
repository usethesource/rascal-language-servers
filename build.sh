#!/usr/bin/env bash

set -euxo pipefail

extra_flags=''

clean="clean"
while getopts 'fd' flag; do
  case "${flag}" in
    f) extra_flags='-Drascal.compile.skip' ;;
    d) clean='' ;;
    *) printf "incorrect param, valid params:
    Use -f to skip rascal-compile
    Use -d to skip cleaning the target folder"
        exit 1 ;;
  esac
done

rm -f rascal-lsp/target/*.jar

(cd rascal-lsp && mvn $clean package $extra_flags )
(cd rascal-vscode-extension && npm run lsp4j:package )

