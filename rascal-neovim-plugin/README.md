# Rascal Neovim plugin

The Rascal Neovim plugin adds the Rascal LSP to Neovim.

## Features

- LSP
- `:RascalTerminal`, which opens a terminal with the Rascal REPL

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
  dependencies = { "akinsho/toggleterm.nvim" }, -- Optional
  config = function(plugin)
    vim.opt.rtp:append(plugin.dir .. "/rascal-neovim-plugin")
    require("lazy.core.loader").packadd(plugin.dir .. "/rascal-neovim-plugin")
    require("rascal").setup({...})
  end,
  build = "./build.sh -f",
}
```

This config will compile the LSP after downloading.

## Usage

This plugin can be configured by passing a table of options to the `setup` function.
The default configuration of this plugin is

```lua
{
  jar = {
    rascal = jar.get_rascal_jar(),
    rascal_lsp = jar.get_rascal_lsp_jar(),
  },
  terminal = {
    command = function ()
      return "java -jar " .. utils.shell_stringify(M.config.jar.rascal)
    end,
    backend = terminal.neovim,
  },
}
```

- `jar`: location of the JAR files of Rascal to use for the terminal.
  Note: changing this option does not affect which JAR files are used by the LSP.
- `terminal`
  - `command`: the command that is used to open a Rascal terminal.
  - `backend`: the terminal backend that is used to render the Rascal REPL.
    By default, the standard Neovim terminal is used.
    This option can also be set to `require("rascal.terminal").toggleterm`
    or to a custom backend.

### LSP

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
