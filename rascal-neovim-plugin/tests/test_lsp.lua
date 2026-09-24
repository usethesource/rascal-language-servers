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

T["lsp"]["hover"] = function()
  expect.wait(5000, function()
    eq(child.lua_get("vim.lsp.get_clients()[1].config.name"), "rascal_lsp")
  end)

  child.fn.search("pathConfig")
  vim.uv.sleep(50)
  child.lua("vim.lsp.buf.hover()")
  expect.wait(90000, function()
    expect.screenshot_match(child.get_screenshot(), vim.pesc("PathConfig::pathConfig"))
  end)
end

T["lsp"]["go to definition"] = function()
  expect.wait(5000, function()
    eq(child.lua_get("vim.lsp.get_clients()[1].config.name"), "rascal_lsp")
  end)

  child.fn.search("uses", "b")
  vim.uv.sleep(50)
  local cursor = child.api.nvim_win_get_cursor(0)
  expect.wait(90000, function()
    child.lua("vim.lsp.buf.hover()")
    vim.uv.sleep(50)
    expect.no_equality(child.api.nvim_win_get_cursor(0), cursor)
  end)
end

T["lsp"]["code actions"] = function()
  expect.wait(5000, function()
    eq(child.lua_get("vim.lsp.get_clients()[1].config.name"), "rascal_lsp")
  end)

  local imports = child.api.nvim_buf_get_lines(0, 2, 8, true)
  child.fn.search("import")
  child.lua_get([[
    vim.lsp.buf.code_action({
      filter = function(action)
        return action.title == "Sort imports and extends"
      end,
      apply = true,
    })
  ]])

  expect.wait(90000, function()
    expect.no_equality(child.api.nvim_buf_get_lines(0, 2, 8, true), imports)
  end)
end

return T
