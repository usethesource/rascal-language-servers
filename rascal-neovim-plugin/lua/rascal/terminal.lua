local utils = require("rascal.utils")

---@alias RascalTerminalBackend fun(command: string, project_root: string|nil):nil

---@type {[string]: RascalTerminalBackend}
local M = {}

function M.neovim(command, project_root)
  if project_root ~= nil then
    command = "cd " .. utils.shell_stringify(project_root) .. ";" .. command
  end
  vim.cmd.terminal(command)
end

function M.toggleterm(command, project_root)
  local Terminal = require("toggleterm.terminal").Terminal
  local term = Terminal:new({ cmd = command, dir = project_root })
  term:open()
end

return M
