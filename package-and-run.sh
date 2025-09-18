#!/usr/bin/env bash

set -euo pipefail
set -x

./package.sh

TMPDIR=$(mktemp --directory)
# shellcheck disable=SC2012
VSIX_LATEST=$(ls -t1 **/*.vsix | head -1)
CODE_COMMAND="code --extensions-dir $TMPDIR/ext --user-data-dir $TMPDIR/data"

mkdir -p "$TMPDIR"/{ext,data}

$CODE_COMMAND --install-extension "$VSIX_LATEST" --force
$CODE_COMMAND --new-window "${1:-}" # optionally pass a workspace/folder/file to open
