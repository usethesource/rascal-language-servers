#!/bin/sh

cat << ENDCAT

1. Go to https://dev.azure.com/rascalmpl
2. Create a personal access token there, from the Config menu on the top right
   * Name should be "usethesource"
   * "All Accessible Organizations"
   * "Full Access" should be on
   * Copy the token
3. run ./build.sh and ./package.sh
4. cd rascal-vscode-extension
5. vsce login usethesource
   * Paste the token
6. vsce publish

ENDCAT
