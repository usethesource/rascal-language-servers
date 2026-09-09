local M = {}

---Get the location of the `target` directory.
---@return string
local function get_target_dir()
  local source_file = debug.getinfo(1).source:sub(2)
  local plugin_root = source_file
  for _ = 1, 4 do
    plugin_root = vim.fs.dirname(plugin_root)
  end
  return plugin_root .. "/rascal-lsp/target"
end

---Get the default location of `rascal.jar.
---@return string
function M.get_rascal_jar()
  return get_target_dir() .. "/lib/rascal.jar"
end

---Get the default location of `rascal-lsp*.jar`.
---@return string|nil
function M.get_rascal_lsp_jar()
  local target_dir = get_target_dir()

  local dir = vim.uv.fs_opendir(target_dir)
  if dir == nil then
    return nil
  end

  local dir_data = vim.uv.fs_readdir(dir)
  while dir_data do
    if string.match(dir_data[1].name, "rascal%-lsp.*%.jar") then
      return target_dir .. "/" .. dir_data[1].name
    end
    dir_data = vim.uv.fs_readdir(dir)
  end

  return nil
end

---Get the classpath for the default JAR location.
---The classpath contains the filenames of the Rascal LSP and Rascal after building.
---@return string|nil
function M.get_classpath()
  local rascal_jar = M.get_rascal_jar()
  local rascal_lsp_jar = M.get_rascal_lsp_jar()
  if rascal_lsp_jar == nil then
    return nil
  end
  return rascal_lsp_jar .. ":" .. rascal_jar
end

return M
