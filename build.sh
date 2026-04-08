#!/usr/bin/env bash

set -euo pipefail

extra_flags=''
lint=0

clean="clean"
while getopts 'lfd' flag; do
  case "${flag}" in
    f) extra_flags='-Drascal.compile.skip -Drascal.tutor.skip -DskipTests' ;;
    l) lint=1 ;;
    d) clean='' ;;
    *) printf "incorrect param, valid params:
    Use -f to skip rascal-compile and tests
    Use -d to skip cleaning the target folder
    Use -l to skip linting

"
        exit 1 ;;
  esac
done

rm -f rascal-lsp/target/*.jar

if (( $lint == 1 )); then
   (cd rascal-lsp && mvn -B checkstyle:checkstyle  checkstyle:check )
fi
(cd rascal-lsp && mvn $clean package -Drascal.monitor.batch $extra_flags )
if (( $lint == 1 )); then
   (cd rascal-vscode-extension && npm run lint )
fi
(cd rascal-vscode-extension && npm run lsp4j:package )

