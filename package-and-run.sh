#!/usr/bin/env bash

set -euox pipefail

./package.sh

TMPDIR=$(mktemp --directory)
VSIX_LATEST=$(ls -t1 **/*.vsix | head -1)
CODE_COMMAND="code --extensions-dir ${TMPDIR}/extensions --user-data-dir ${TMPDIR}/data"

${CODE_COMMAND} --install-extension "${VSIX_LATEST}" --force
${CODE_COMMAND} --new-window "$1"
