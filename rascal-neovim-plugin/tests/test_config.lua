local helpers = dofile("tests/helpers.lua")
local new_set = MiniTest.new_set
local expect, eq = helpers.expect, helpers.expect.equality

local child = MiniTest.new_child_neovim()

local T = new_set({
  hooks = {
    pre_case = function()
      child.restart({ "-u", "tests/minit.lua" })
      child.lua([[M = require("rascal.config")]])
    end,
    post_once = child.stop,
  },
})

T["config"] = new_set()

T["config"]["default config works"] = function()
  expect.file_exists(child.lua_get([[M.config.jar.rascal]]))
  expect.file_exists(child.lua_get([[M.config.jar.rascal_lsp]]))

  local command = child.lua_get([[
    type(M.config.terminal.command) == "string"
      and M.config.terminal.command
      or M.config.terminal.command()
  ]])
  eq(type(command), "string")

  eq(child.lua_get([[type(M.config.terminal.backend)]]), "function")
end

return T
