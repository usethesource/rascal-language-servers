vim.api.nvim_create_user_command("RascalTerminal", function ()
  require("rascal").terminal()
end, {})
