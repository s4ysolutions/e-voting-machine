--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --

local uuid = require('uuid')
local read_only = require('ledgers.internal.read-only-table')

local function add_ballot_issued_mark(table_ballot_issues, voter_id, voting_id)
    local id = uuid()
    table_ballot_issues[id] = { voter_id = voter_id, voting_id = voting_id }
    return id
end
--[[
function ledger_ballot_issues.get_mark_ballot_issued_contract(voter_id, voting_id)
    local id = uuid()
    local contract = 'ledger["' ..
        id .. '"] = {voter_id = "' .. voter_id .. '", voting_id = "' .. voting_id .. '"} return "' .. id .. '"'
    return signing.sign(contract)
end

function ledger_ballot_issues.execute_contract(signed_contract)
    local contract = signing.extract(signed_contract)
    return load(contract, 'ledger_ballot_issues', 't', { ledger = table_ballot_issues })()
end
]] --

if arg[0]:match("ballot%-issues%-ledger%.lua$") then
    local _uuid = function(prefix)
        if not prefix then
            prefix = 'uid'
        end
        local count = 0
        return function()
            count = count + 1
            return prefix .. count
        end
    end

    function test_add_ballot_issued_mark()
        uuid = _uuid()
        local table_ballot_issues = {}
        local id = add_ballot_issued_mark(table_ballot_issues, 'voter_id', 'voting_id')
        luaunit.assertEquals(id, 'uid1')
        luaunit.assertEquals(table_ballot_issues['uid1'].voter_id, 'voter_id')
        luaunit.assertEquals(table_ballot_issues['uid1'].voting_id, 'voting_id')
    end

    --[[
    function test_get_mark_ballot_issued_contract()
        uuid = _uuid()
        local contract = ledger_ballot_issues.get_mark_ballot_issued_contract('voter_id', 'voting_id')
        luaunit.assertEquals(signing.extract(contract),
            'ledger["uid1"] = {voter_id = "voter_id", voting_id = "voting_id"} return "uid1"')
    end

    function test_execute_contract()
        table_ballot_issues = {}
        local success = ledger_ballot_issues.execute_contract(signing.sign("ledger['1'] = 'a' return 'b'"))
        luaunit.assertEquals(success, 'b')
        luaunit.assertEquals(table_ballot_issues['1'], 'a')
    end

    function test_execute_contract_2()
        table_ballot_issues = {}
        ledger_ballot_issues.execute_contract(signing.sign("ledger['1'] = 'a'"))
        ledger_ballot_issues.execute_contract(signing.sign("ledger['2'] = 'c'"))
        luaunit.assertEquals(table_ballot_issues['1'], 'a')
        luaunit.assertEquals(table_ballot_issues['2'], 'c')
    end

    function test_execute_mark_ballot_issued_contract()
        -- Arrange
        uuid = _uuid()
        table_ballot_issues = {}
        local contract = ledger_ballot_issues.get_mark_ballot_issued_contract('voter_id', 'voting_id')
        -- Act
        local id = ledger_ballot_issues.execute_contract(contract)
        -- Assert
        luaunit.assertEquals(id, 'uid1')
        luaunit.assertEquals(table_ballot_issues['uid1'].voter_id, 'voter_id')
        luaunit.assertEquals(table_ballot_issues['uid1'].voting_id, 'voting_id')
    end
]]
    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return function(table_ballot_issues)
        return {
            ledger = read_only(table_ballot_issues),
            add_ballot_issued_mark = function(voter_id, voting_id)
                return add_ballot_issued_mark(table_ballot_issues, voter_id, voting_id)
            end
        }
    end
end
