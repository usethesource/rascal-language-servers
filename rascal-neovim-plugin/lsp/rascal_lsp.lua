local rascal_lsp = require("rascal_lsp")
local classpath = rascal_lsp.get_classpath() or ""

return {
  name = "rascal_lsp",
  -- https://github.com/usethesource/rascal-language-servers/blob/4ef17204f9bc15dabc05f9a88b9fa837eb92a633/rascal-vscode-extension/src/lsp/RascalLSPConnection.ts#L160
  cmd = {
    "java",
    "-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.log.LogJsonConfiguration",
    "-Dlog4j2.level=DEBUG",
    "-Drascal.fallbackResolver=org.rascalmpl.vscode.lsp.uri.FallbackResolver",
    "-Drascal.lsp.deploy=true",
    "-Drascal.compilerClasspath=" .. classpath,
    "-cp",
    classpath,
    "org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer",
  },
  filetypes = { "rascal" },
  root_markers = { "build.properties" },
}
