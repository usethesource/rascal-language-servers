# !/bin/sh

# This documents (and performs) necessary commands for local UI testing.
# Experts might run these manually on demand. For example, repeatedly
# running the final npx extest command for debugging purposes is normal.

set -e;
set -x;

cd rascal-vscode-extension

UITESTS=/tmp/vscode-uitests

# cleanup to avoid contamination with previous runs

rm -rf $UITESTS || true

# compiling the TS code as well as the test TS code at least once is required before execution
# this assumes you have run `npm ci` at least once since a large update
npm run compile-tests

# test what was compiled

exec npx extest setup-and-run out/test/vscode-suite/*.test.js \
    --code_version 1.98.0 \
    --storage $UITESTS \
    --extensions_dir $UITESTS/extensions_dir
