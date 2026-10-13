#!/bin/bash

# Sets the version of Rascal and Rascal LSP in the test project(s) to the first argument of this script. If no argument is given, it uses the current version of Rascal LSP from the POM.

# Find the current version of Rascal
RASCAL_VERSION=${1:-$( ( sh find-dependency-version.sh rascal-lsp rascal ) )}
( cd rascal-vscode-extension/test-workspace/test-project && mvn versions:use-dep-version -q -Dincludes=org.rascalmpl:rascal -DdepVersion=$RASCAL_VERSION -DprocessProperties )

# Find the current version of Rascal LSP
LSP_VERSION=${2:-$( ( cd rascal-lsp && mvn help:evaluate -Dexpression=project.version -q -DforceStdout ) )}
( cd rascal-vscode-extension/test-workspace/test-project && mvn versions:use-dep-version -q -Dincludes=org.rascalmpl:rascal-lsp -DdepVersion=$LSP_VERSION -DprocessProperties )
