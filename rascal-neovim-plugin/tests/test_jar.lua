local helpers = dofile("tests/helpers.lua")
local new_set = MiniTest.new_set
local expect, eq = helpers.expect, helpers.expect.equality

local child = MiniTest.new_child_neovim()

local T = new_set({
  hooks = {
    pre_case = function()
      child.restart({ "-u", "tests/minit.lua" })
      child.lua([[M = require("rascal.jar")]])
    end,
    post_once = child.stop,
  },
})

T["get_rascal_jar()"] = new_set()

T["get_rascal_jar()"]["works"] = function()
  local rascal_jar = child.lua_get([[M.get_rascal_jar()]])
  expect.suffix(rascal_jar, "/rascal-lsp/target/lib/rascal.jar")
  expect.file_exists(rascal_jar)
end

T["get_rascal_lsp_jar()"] = new_set()

T["get_rascal_lsp_jar()"]["works"] = function()
  ---@type string
  local rascal_lsp_jar = child.lua_get([[M.get_rascal_lsp_jar()]])
  expect.match(rascal_lsp_jar, "/rascal%-lsp/target/rascal%-lsp.*%.jar")
  expect.file_exists(rascal_lsp_jar)
end

T["get_classpath()"] = new_set()

T["get_classpath()"]["works"] = function()
  local classpath = child.lua_get([[M.get_classpath()]])
  expect.no_equality(classpath, nil)
  ---@cast classpath string

  local colon = classpath:find(":")
  expect.no_equality(colon, nil)
  ---@cast colon number

  local rascal_lsp_jar = classpath:sub(1, colon - 1)
  local rascal_jar = classpath:sub(colon + 1)
  expect.file_exists(rascal_lsp_jar)
  expect.file_exists(rascal_jar)
end

return T
