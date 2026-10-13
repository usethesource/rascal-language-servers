#!/bin/bash

# Sets the version of Rascal LSP in the test project(s) to the first argument of this script. If no argument is given, it uses the current version of Rascal LSP from the POM.

# Find the current version of Rascal LSP
LSP_VERSION=${1:-$( ( cd rascal-lsp && mvn help:evaluate -Dexpression=project.version -q -DforceStdout ) )}

# Set the version of Rascal LSP in the test project
( cd rascal-vscode-extension/test-workspace/test-project && mvn versions:use-dep-version -q -Dincludes=org.rascalmpl:rascal-lsp -DdepVersion=$LSP_VERSION -DprocessProperties )
