#!/usr/bin/env bash

set -euxo pipefail

(cd rascal-vscode-extension && npx vsce package )
