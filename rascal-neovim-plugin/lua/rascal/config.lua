local jar = require("rascal.jar")
local utils = require("rascal.utils")
local terminal = require("rascal.terminal")

local M = {}

---@class RascalConfig
---@field jar RascalConfigJar
---@field terminal RascalConfigTerminal

---@class RascalConfigJar
---@field rascal string
---@field rascal_lsp string

---@class RascalConfigTerminal
---@field command string|fun():string
---@field backend RascalTerminalBackend

---@type RascalConfig
M.default = {
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

---@type RascalConfig
M.config = vim.tbl_deep_extend("force", M.default, {})

return M
