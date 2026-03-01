local helpers = dofile("tests/helpers.lua")
local new_set = MiniTest.new_set
local expect, eq = helpers.expect, helpers.expect.equality

local child = MiniTest.new_child_neovim()

local T = new_set({
  hooks = {
    pre_case = function()
      child.restart({ "-u", "tests/minit.lua" })
      child.lsp.enable("rascal_lsp")
      child.cmd("e ../examples/pico-dsl-lsp/src/main/rascal/lang/pico/LanguageServer.rsc")
    end,
    post_case = function()
      child.lua("vim.lsp.stop_client(vim.lsp.get_clients(), true)")
    end,
    post_once = child.stop,
  },
})

T["lsp"] = new_set()

T["lsp"]["attaches"] = function()
  expect.wait(5000, function()
    eq(child.lua_get("vim.lsp.get_clients()[1].config.name"), "rascal_lsp")
  end)
end

T["lsp"]["highlighting"] = function()
  expect.wait(5000, function()
    eq(child.lua_get("vim.lsp.semantic_tokens.get_at_pos(0, 0, 0)[1].type"), "keyword")
  end)
end

T["lsp"]["diagnostics"] = function()
  child.api.nvim_put({ "syntax error" }, "l", false, false)
  expect.wait(5000, function()
    eq(child.diagnostic.get()[1].message, "The parser couldn't fully understand this code.")
  end)
end

return T
