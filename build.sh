#!/usr/bin/env bash

set -euxo pipefail

extra_flags=''

while getopts 'f' flag; do
  case "${flag}" in
    f) extra_flags='-Drascal.compile.skip' ;;
    *) printf "Use -f to skip rascal-compile"
        exit 1 ;;
  esac
done


(cd rascal-lsp && mvn clean package $extra_flags )
(cd rascal-vscode-extension && npm run lsp4j:package )

