local M = {}

M.expect = vim.deepcopy(MiniTest.expect)

M.expect.file_exists = MiniTest.new_expectation(
  "file exists",
  function(filename) return vim.uv.fs_stat(filename) end,
  function(filename) return ("Filename: %s"):format(filename) end
)

M.expect.match = MiniTest.new_expectation(
  "match",
  function(str, pattern) return string.match(str, pattern) end,
  function(str, pattern) return ("String: %s\nPattern: %s"):format(str, pattern) end
)

M.expect.suffix = MiniTest.new_expectation(
  "has suffix",
  function(str, suffix) return string.sub(str, #str - #suffix + 1) end,
  function(str, suffix) return ("String: %s\nSuffix: %s"):format(str, suffix) end
)

M.expect.screenshot_match = MiniTest.new_expectation(
  "screenshot match",
  function(screenshot, pattern)
    local concatenated = vim.iter(screenshot.text):flatten():join("")
    return concatenated:match(pattern)
  end,
  function(screenshot, pattern)
    return ("Screenshot:\n%s\nPattern: %s"):format(screenshot, pattern)
  end
)

---Retry an expectation until the timeout.
---@param timeout integer How many milliseconds to wait before giving up
---@param fn fun()
---@param interval integer? How many milliseconds to wait between tries (200 by default)
---@see vim.wait
function M.expect.wait(timeout, fn, interval)
  if interval == nil then
    interval = 200
  end
  local timed_out = false
  local timer = vim.uv.new_timer()
  timer:start(timeout, 0, function ()
    timer:stop()
    timer:close()
    timed_out = true
  end)

  local success, output
  while not timed_out do
    success, output = pcall(fn)
    if success then
      timer:stop()
      timer:close()
      return
    end
    vim.uv.sleep(interval)
  end
  error(output)
end

return M
