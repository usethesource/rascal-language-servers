Thanks for your interest in the Rascal Language Servers project!

This project features:

1. an LSP for Rascal itself
2. an LSP "generator" for languages implemented in Rascal
3. an interactive Rascal terminal
4. a VScode client for the above three

These four components communicate with each other, all
via JSON-RPC. Some of this is defined by the LSP protocol,
while some messages are proprietary to Rascal. In particular
the messages to boot up fresh LSP servers and the messages
regarding the terminal are Rascal-specific.

Most of the code is written in Java and Rascal, while a
small part is written in TypeScript.

Please drop us a line (submit a feature request or bug report)
via github issues?
