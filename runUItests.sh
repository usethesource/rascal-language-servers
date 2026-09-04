#!/bin/sh

# This documents (and performs) necessary commands for local UI testing.
# Experts might run these manually on demand. For example, repeatedly
# running the final npx extest command for debugging purposes is normal.

set -e;
set -x;

# Find the current Rascal LSP version in the POM, and restore it when this script exits
RESTORE_LSP_VERSION=$( ( cd rascal-vscode-extension/test-workspace/test-project && mvn dependency:tree -Dincludes=org.rascalmpl:rascal-lsp | grep rascal-lsp | cut -d ':' -f 4 ) )
restore_versions() {
    cd .. && ./update-test-dependencies.sh "$RESTORE_LSP_VERSION"
}
trap "restore_versions" EXIT SIGINT SIGTERM SIGHUP

# Bootstrap LSP version
./update-test-dependencies.sh


# cleanup to avoid contamination with previous runs
UITESTS=/tmp/vscode-uitests
rm -rf $UITESTS || true

# compiling the TS code as well as the test TS code at least once is required before execution
# this assumes you have run `npm ci` at least once since a large update
cd rascal-vscode-extension
npm run compile:tests

# test what was compiled
VSCODE_VERSION=$(grep '"vscode":' package.json | awk -F^ '{ print $2 }' | awk -F\" '{ print $1 }')
echo "Running tests with VSCode version $VSCODE_VERSION"
RASCAL_LSP_DEV_DEPLOY=true npx extest setup-and-run out/test/vscode-suite/*.test.js \
    --code_version "${VSCODE_VERSION}" \
    --storage $UITESTS \
    --extensions_dir $UITESTS/extensions_dir
