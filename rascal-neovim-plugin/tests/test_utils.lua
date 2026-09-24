local helpers = dofile("tests/helpers.lua")
local new_set = MiniTest.new_set
local expect, eq = helpers.expect, helpers.expect.equality

local child = MiniTest.new_child_neovim()

local T = new_set({
  hooks = {
    pre_case = function()
      child.restart({ "-u", "tests/minit.lua" })
      child.lua([[M = require("rascal.utils")]])
    end,
    post_once = child.stop,
  },
})

T["shell_stringify()"] = new_set()

T["shell_stringify()"]["works"] = function()
  eq(child.lua_get([[M.shell_stringify("hello")]]), "'hello'")
end

T["shell_stringify()"]["escapes quotes"] = function()
  eq(child.lua_get([[M.shell_stringify("let's a go")]]), [['let'"'"'s a go']])
end

T["get_project_root()"] = new_set()

T["get_project_root()"]["works"] = function()
  child.cmd("e ../examples/pico-dsl-lsp/src/main/rascal/lang/pico/LanguageServer.rsc")
  local root = child.lua_get("M.get_project_root()")
  expect.suffix(root, "/examples/pico-dsl-lsp")
  expect.file_exists(root)
end

return T
