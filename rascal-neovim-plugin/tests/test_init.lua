local helpers = dofile("tests/helpers.lua")
local new_set = MiniTest.new_set
local expect, eq = helpers.expect, helpers.expect.equality

local child = MiniTest.new_child_neovim()

local T = new_set({
  hooks = {
    pre_case = function()
      child.restart({ "-u", "tests/minit.lua" })
      child.lua([[
        M = require("rascal")
        config = require("rascal.config")
      ]])
    end,
    post_once = child.stop,
  },
})

T["setup()"] = new_set()

T["setup()"]["empty config keeps defaults"] = function()
  child.lua([[M.setup({})]])
  eq(child.lua_get([[vim.deep_equal(config.config, config.default)]]), true)
end

T["setup()"]["sets config"] = function()
  child.lua([[M.setup({
    jar = {
      rascal = "/path/to/rascal.jar",
    },
    terminal = {
      backend = require("rascal.terminal").toggleterm
    }
  })]])

  eq(child.lua_get([[config.config.jar.rascal]]), "/path/to/rascal.jar")
  eq(
    child.lua_get([[config.config.jar.rascal_lsp]]),
    child.lua_get([[config.default.jar.rascal_lsp]])
  )
  eq(child.lua_get([[config.config.terminal.command()]]), "java -jar '/path/to/rascal.jar'")
  eq(child.lua_get([[
    config.config.terminal.backend
      == require("rascal.terminal").toggleterm
  ]]), true)
end

T["terminal()"] = new_set({
  parametrize = { { "neovim" }, { "toggleterm" } },
})

T["terminal()"]["works"] = function(backend)
  child.lua(([[
    M.setup({
      terminal = {
        backend = require("rascal.terminal").%s
      }
    })
  ]]):format(backend))
  child.cmd("e ../examples/pico-dsl-lsp/src/main/rascal/lang/pico/LanguageServer.rsc")
  child.lua("M.terminal()")

  expect.wait(5000, function ()
    expect.screenshot_match(child.get_screenshot(), vim.pesc("/rascal-lsp/target/lib/rascal.jar"))
    expect.screenshot_match(child.get_screenshot(), vim.pesc("/examples/pico-dsl-lsp/"))
  end)
end

return T
