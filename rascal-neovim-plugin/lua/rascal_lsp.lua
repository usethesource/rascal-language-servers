local M = {}

---Get the classpath for the default JAR location.
---The classpath contains the filenames of the Rascal LSP and Rascal after building.
---@return string|nil
function M.get_classpath()
  local source_file = debug.getinfo(1).source:sub(2)
  local plugin_root = source_file
  for _ = 1, 3 do
    plugin_root = vim.fs.dirname(plugin_root)
  end

  local rascal_jar = plugin_root .. "/rascal-lsp/target/lib/rascal.jar"
  local rascal_lsp_jar = nil

  local dir = vim.uv.fs_opendir(plugin_root .. "/rascal-lsp/target")
  if dir == nil then
    return nil
  end

  local dir_data = vim.uv.fs_readdir(dir)
  while dir_data do
    if string.match(dir_data[1].name, "rascal%-lsp.*%.jar") then
      rascal_lsp_jar = plugin_root .. "/rascal-lsp/target/" .. dir_data[1].name
      break
    end
    dir_data = vim.uv.fs_readdir(dir)
  end

  if rascal_lsp_jar == nil then
    return nil
  end

  return rascal_lsp_jar .. ":" .. rascal_jar
end

return M
