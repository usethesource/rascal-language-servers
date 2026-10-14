local helpers = dofile("tests/helpers.lua")
local new_set = MiniTest.new_set
local expect, eq = helpers.expect, helpers.expect.equality

local child = MiniTest.new_child_neovim()

local T = new_set({
  hooks = {
    pre_case = function()
      child.restart({ "-u", "tests/minit.lua" })
      child.lua([[M = require("rascal.terminal")]])
    end,
    post_once = child.stop,
  },
})

T["backend"] = new_set({
  parametrize = { { "neovim" }, { "toggleterm" } },
})

T["backend"]["runs commands"] = function(backend)
  child.lua_get(([[M.%s("echo hello ; cat")]]):format(backend))
  -- Wait for the output to be visible
  vim.uv.sleep(20)
  -- Ignore the status bar with nondeterministic pid
  expect.reference_screenshot(child.get_screenshot(), nil, { ignore_text = { 23 } })
end

T["backend"]["changes to the correct directory"] = function(backend)
  child.lua_get(([[M.%s("pwd ; cat", "/")]]):format(backend))
  -- Wait for the output to be visible
  vim.uv.sleep(20)
  -- Ignore the status bar with nondeterministic pid
  expect.reference_screenshot(child.get_screenshot(), nil, { ignore_text = { 23 } })
end

return T
