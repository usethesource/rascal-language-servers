vim.env.LAZY_STDPATH = ".tests"
load(vim.fn.system("curl -s https://raw.githubusercontent.com/folke/lazy.nvim/main/bootstrap.lua"))()

require("lazy.minit").setup({
  spec = {
    { dir = vim.uv.cwd() },
    { "nvim-mini/mini.test", version = "*" },
    { "akinsho/toggleterm.nvim", version = "*", config = true },
  },
})
