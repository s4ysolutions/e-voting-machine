--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --

local function read_only(table)
    local proxy = {}
    local mt = {
        __index = table,
        __newindex = function(_, _, _)
            error("Attempt to modify a read-only table", 2)
        end,
        __pairs = function()
            return pairs(table)
        end,
        __ipairs = function()
            return ipairs(table)
        end,
        __len = function()
            return #table
        end
    }
    setmetatable(proxy, mt)
    return proxy
end

if arg[0]:match("read%-only%-table%.lua$") then
    function test_read_only()
        local table = {a=1, b=2}
        local read_only_table = read_only(table)
        luaunit.assertEquals(read_only_table.a, 1)
        luaunit.assertEquals(read_only_table.b, 2)
        luaunit.assertErrorMsgContains("read-only table", function() read_only_table.a = 3 end)
    end

    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return read_only
end
