local M = {}

---Look in the parent directories for the project root.
---@return string
function M.get_project_root()
  for dir in vim.fs.parents(vim.api.nvim_buf_get_name(0)) do
    if vim.uv.fs_stat(dir .. "/META-INF") or vim.uv.fs_stat(dir .. "/pom.xml") then
      return dir
    end
  end
end

---Escape a string for in a shell command.
---@param str string
---@return string
function M.shell_stringify(str)
  return "'" .. str:gsub("'", "'\"'\"'") .. "'"
end

return M
