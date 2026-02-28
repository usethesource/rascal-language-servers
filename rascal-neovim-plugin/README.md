# Rascal Neovim plugin

The Rascal Neovim plugin adds the Rascal LSP to Neovim.

## Requirements

For building the LSP:

- Maven
- NodeJS + NPM

During runtime:

- JDK 11

## Installation

Using lazy.nvim:

```lua
{
  "usethesource/rascal-language-servers",
  config = function(plugin)
    vim.opt.rtp:append(plugin.dir .. "/rascal-neovim-plugin")
    require("lazy.core.loader").packadd(plugin.dir .. "/rascal-neovim-plugin")
  end,
  build = "./build.sh -f",
}
```

This config will compile the LSP after downloading.
To enable the LSP, use

```lua
vim.lsp.enable("rascal_lsp")
```

By default, the JAR files in `rascal-lsp/target` will be used.
A different location can be specified using

```lua
vim.lsp.config("rascal_lsp", {
  cmd = {...}
})
```

See [lsp/rascal_lsp.lua](lsp/rascal_lsp.lua) for details.
