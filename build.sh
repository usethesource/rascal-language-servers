#!/usr/bin/env bash

set -euxo pipefail

extra_flags=''

clean="clean"
while getopts 'fd' flag; do
  case "${flag}" in
    f) extra_flags='-Drascal.compile.skip -Drascal.tutor.skip -DskipTests' ;;
    d) clean='' ;;
    *) printf "incorrect param, valid params:
    Use -f to skip rascal-compile and tests
    Use -d to skip cleaning the target folder"
        exit 1 ;;
  esac
done

rm -f rascal-lsp/target/*.jar

(cd rascal-lsp && mvn $clean package -Drascal.monitor.batch $extra_flags )
(cd rascal-vscode-extension && npm run lsp4j:package )

