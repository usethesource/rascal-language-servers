local config = require("rascal.config")
local utils = require("rascal.utils")

local M = {}

---Open a Rascal terminal.
function M.terminal()
  local cmd = config.config.terminal.command
  if type(cmd) == "function" then
    cmd = cmd()
  end

  local root = utils.get_project_root()
  config.config.terminal.backend(cmd, root)
end

---Set configuration for this plugin.
---@param opts RascalConfig
function M.setup(opts)
  config.config = vim.tbl_deep_extend("force", config.default, opts)
end

return M
