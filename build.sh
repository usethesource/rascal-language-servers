#!/usr/bin/env bash

set -euo pipefail

extra_flags=''
lint=0
goal='package'

clean="clean"
while getopts 'lfdi' flag; do
  case "${flag}" in
    f) extra_flags='-Drascal.compile.skip -Drascal.tutor.skip -DskipTests -Drascal.package.skip' ;;
    l) lint=1 ;;
    d) clean='' ;;
    i) goal='install' ;;
    *) printf "incorrect param, valid params:
    Use -f to skip rascal-compile and tests
    Use -d to skip cleaning the target folder
    Use -l to trigger linting
    Use -i to install rascal-lsp into the local .m2 repository

"
        exit 1 ;;
  esac
done

rm -f rascal-lsp/target/*.jar

if (( $lint == 1 )); then
   (cd rascal-lsp && mvn -B checkstyle:checkstyle  checkstyle:check )
fi
(cd rascal-lsp && mvn $clean $goal -Drascal.monitor.batch $extra_flags )
if (( $lint == 1 )); then
   (cd rascal-vscode-extension && npm run lint )
fi
(cd rascal-vscode-extension && npm run lsp4j:package )

