--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --


local uuid = require("uuid")

local signing = require('ledgers.signing')('cast-ledger-secret')
local read_only = require('ledgers.internal.read-only-table')

local function get_contract_vote(voting_id, subject_id, yes, no)
    local cast_id = uuid()
    return 'ledger["' ..
        cast_id ..
        '"] = {voting_id = "' ..
        voting_id ..
        '", subject_id = "' .. subject_id .. '", yes = ' .. yes .. ', no = ' .. no .. '} return "' .. cast_id .. '"'
end

function ledger_cast__get_contract_vote_yes(voting_id, subject_id)
    return signing.sign(get_contract_vote(voting_id, subject_id, 1, 0))
end

function ledger_cast__get_contract_vote_no(voting_id, subject_id)
    return signing.sign(get_contract_vote(voting_id, subject_id, 0, 1))
end

function ledger_cast__get_contract_vote_yes_after_yes(voting_id, subject_id)
    return signing.sign(get_contract_vote(voting_id, subject_id, 0, 0))
end

function ledger_cast__get_contract_vote_yes_after_no(voting_id, subject_id)
    return signing.sign(get_contract_vote(voting_id, subject_id, 1, -1))
end

function ledger_cast__get_contract_vote_no_after_yes(voting_id, subject_id)
    return signing.sign(get_contract_vote(voting_id, subject_id, -1, 1))
end

function ledger_cast__get_contract_vote_no_after_no(voting_id, subject_id)
    return signing.sign(get_contract_vote(voting_id, subject_id, 0, 0))
end

function ledger_cast__execute_contract(table_cast, signed_contract)
    local contract = signing.extract(signed_contract)
    return load(contract, 'ledger_cast', 't', { ledger = table_cast })()
end

if arg[0]:match("cast%-ledger%.lua$") then
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

    function test_execute_contract()
        local table_cast = {}
        uuid = _uuid()
        local success = ledger_cast__execute_contract(table_cast, signing.sign("ledger['1'] = 'a' return 'b'"))
        luaunit.assertEquals(success, 'b')
        luaunit.assertEquals(table_cast['1'], 'a')
    end

    function test_get_contract_vote_yes()
        -- Arrange
        uuid = _uuid()
        -- Act
        local signed_contract = ledger_cast__get_contract_vote_yes('1', '2')
        -- Assert
        local contract = signing.extract(signed_contract)
        luaunit.assertEquals(contract,
            'ledger["uid1"] = {voting_id = "1", subject_id = "2", yes = 1, no = 0} return "uid1"')
    end

    function test_get_contract_vote_no()
        -- Arrange
        uuid = _uuid()
        -- Act
        local signed_contract = ledger_cast__get_contract_vote_no('1', '2')
        -- Assert
        local contract = signing.extract(signed_contract)
        luaunit.assertEquals(contract,
            'ledger["uid1"] = {voting_id = "1", subject_id = "2", yes = 0, no = 1} return "uid1"')
    end

    function test_contract_vote_yes_after_yes()
        -- Arrange
        uuid = _uuid()
        -- Act
        local signed_contract = ledger_cast__get_contract_vote_yes_after_yes('1', '2')
        -- Assert
        local contract = signing.extract(signed_contract)
        luaunit.assertEquals(contract,
            'ledger["uid1"] = {voting_id = "1", subject_id = "2", yes = 0, no = 0} return "uid1"')
    end

    function test_contract_vote_yes_after_no()
        -- Arrange
        uuid = _uuid()
        -- Act
        local signed_contract = ledger_cast__get_contract_vote_yes_after_no('1', '2')
        -- Assert
        local contract = signing.extract(signed_contract)
        luaunit.assertEquals(contract,
            'ledger["uid1"] = {voting_id = "1", subject_id = "2", yes = 1, no = -1} return "uid1"')
    end

    function test_contract_vote_no_after_yes()
        -- Arrange
        uuid = _uuid()
        -- Act
        local signed_contract = ledger_cast__get_contract_vote_no_after_yes('1', '2')
        -- Assert
        local contract = signing.extract(signed_contract)
        luaunit.assertEquals(contract,
            'ledger["uid1"] = {voting_id = "1", subject_id = "2", yes = -1, no = 1} return "uid1"')
    end

    function test_contract_vote_no_after_no()
        -- Arrange
        uuid = _uuid()
        -- Act
        local signed_contract = ledger_cast__get_contract_vote_no_after_no('1', '2')
        -- Assert
        local contract = signing.extract(signed_contract)
        luaunit.assertEquals(contract,
            'ledger["uid1"] = {voting_id = "1", subject_id = "2", yes = 0, no = 0} return "uid1"')
    end

    local function count_votes(table_cast, voting_id, subject_id)
        local yes = 0
        local no = 0
        for id, cast in pairs(table_cast) do
            if cast.voting_id == voting_id and cast.subject_id == subject_id then
                yes = yes + cast.yes
                no = no + cast.no
            end
        end
        return yes, no
    end

    function test_execute_contract_vote_yes()
        -- Arrange
        local table_cast = {}
        uuid = _uuid()
        local signed_contract = ledger_cast__get_contract_vote_yes('1', '2')
        -- Act
        local id = ledger_cast__execute_contract(table_cast, signed_contract)
        -- Assert
        luaunit.assertEquals('uid1', id)
        luaunit.assertEquals(table_cast['uid1'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid1'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid1'].yes, 1)
        luaunit.assertEquals(table_cast['uid1'].no, 0)

        local yes, no = count_votes(table_cast, '1', '2')
        luaunit.assertEquals(yes, 1)
        luaunit.assertEquals(no, 0)
    end

    function test_execute_contract_vote_no()
        -- Arrange
        local table_cast = {}
        uuid = _uuid()
        local signed_contract = ledger_cast__get_contract_vote_no('1', '2')
        -- Act
        local id = ledger_cast__execute_contract(table_cast, signed_contract)
        -- Assert
        luaunit.assertEquals('uid1', id)
        luaunit.assertEquals(table_cast['uid1'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid1'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid1'].yes, 0)
        luaunit.assertEquals(table_cast['uid1'].no, 1)

        local yes, no = count_votes(table_cast, '1', '2')
        luaunit.assertEquals(yes, 0)
        luaunit.assertEquals(no, 1)
    end

    function test_execute_contract_vote_yes_after_yes()
        -- Arrange
        local table_cast = {}
        uuid = _uuid()
        local signed_contract1 = ledger_cast__get_contract_vote_yes('1', '2')
        local signed_contract2 = ledger_cast__get_contract_vote_yes_after_yes('1', '2')
        local id1 = ledger_cast__execute_contract(table_cast, signed_contract1)
        -- Act
        local id2 = ledger_cast__execute_contract(table_cast, signed_contract2)
        -- Assert
        luaunit.assertEquals(id1, 'uid1')
        luaunit.assertEquals(id2, 'uid2')
        luaunit.assertEquals(table_cast['uid1'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid1'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid1'].yes, 1)
        luaunit.assertEquals(table_cast['uid1'].no, 0)
        luaunit.assertEquals(table_cast['uid2'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid2'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid2'].yes, 0)
        luaunit.assertEquals(table_cast['uid2'].no, 0)

        local yes, no = count_votes(table_cast, '1', '2')
        luaunit.assertEquals(yes, 1)
        luaunit.assertEquals(no, 0)
    end

    function test_execute_contract_vote_yes_after_no()
        -- Arrange
        local table_cast = {}
        uuid = _uuid()
        local signed_contract1 = ledger_cast__get_contract_vote_no('1', '2')
        local signed_contract2 = ledger_cast__get_contract_vote_yes_after_no('1', '2')
        local id1 = ledger_cast__execute_contract(table_cast, signed_contract1)
        -- Act
        local id2 = ledger_cast__execute_contract(table_cast, signed_contract2)
        -- Assert
        luaunit.assertEquals(id1, 'uid1')
        luaunit.assertEquals(id2, 'uid2')
        luaunit.assertEquals(table_cast['uid1'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid1'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid1'].yes, 0)
        luaunit.assertEquals(table_cast['uid1'].no, 1)
        luaunit.assertEquals(table_cast['uid2'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid2'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid2'].yes, 1)
        luaunit.assertEquals(table_cast['uid2'].no, -1)

        local yes, no = count_votes(table_cast, '1', '2')
        luaunit.assertEquals(yes, 1)
        luaunit.assertEquals(no, 0)
    end

    function test_execute_contract_vote_no_after_yes()
        -- Arrange
        local table_cast = {}
        uuid = _uuid()
        local signed_contract1 = ledger_cast__get_contract_vote_yes('1', '2')
        local signed_contract2 = ledger_cast__get_contract_vote_no_after_yes('1', '2')
        local id1 = ledger_cast__execute_contract(table_cast, signed_contract1)
        -- Act
        local id2 = ledger_cast__execute_contract(table_cast, signed_contract2)
        -- Assert
        luaunit.assertEquals(id1, 'uid1')
        luaunit.assertEquals(id2, 'uid2')
        luaunit.assertEquals(table_cast['uid1'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid1'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid1'].yes, 1)
        luaunit.assertEquals(table_cast['uid1'].no, 0)
        luaunit.assertEquals(table_cast['uid2'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid2'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid2'].yes, -1)
        luaunit.assertEquals(table_cast['uid2'].no, 1)

        local yes, no = count_votes(table_cast, '1', '2')
        luaunit.assertEquals(yes, 0)
        luaunit.assertEquals(no, 1)
    end

    function test_execute_contract_vote_no_after_no()
        -- Arrange
        local table_cast = {}
        uuid = _uuid()
        local signed_contract1 = ledger_cast__get_contract_vote_no('1', '2')
        local signed_contract2 = ledger_cast__get_contract_vote_no_after_no('1', '2')
        local id1 = ledger_cast__execute_contract(table_cast, signed_contract1)
        -- Act
        local id2 = ledger_cast__execute_contract(table_cast, signed_contract2)
        -- Assert
        luaunit.assertEquals(id1, 'uid1')
        luaunit.assertEquals(id2, 'uid2')
        luaunit.assertEquals(table_cast['uid1'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid1'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid1'].yes, 0)
        luaunit.assertEquals(table_cast['uid1'].no, 1)
        luaunit.assertEquals(table_cast['uid2'].voting_id, '1')
        luaunit.assertEquals(table_cast['uid2'].subject_id, '2')
        luaunit.assertEquals(table_cast['uid2'].yes, 0)
        luaunit.assertEquals(table_cast['uid2'].no, 0)

        local yes, no = count_votes(table_cast, '1', '2')
        luaunit.assertEquals(yes, 0)
        luaunit.assertEquals(no, 1)
    end

    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return function(table_cast)
        return {
            get_contract_vote_yes = function(voting_id, subject_id)
                return ledger_cast__get_contract_vote_yes(voting_id, subject_id, 1, 0)
            end,
            get_contract_vote_no = function(voting_id, subject_id)
                return ledger_cast__get_contract_vote_no(voting_id, subject_id, 0, 1)
            end,
            get_contract_vote_yes_after_yes = function(voting_id, subject_id)
                return ledger_cast__get_contract_vote_yes_after_yes(voting_id, subject_id, 0, 0)
            end,
            get_contract_vote_yes_after_no = function(voting_id, subject_id)
                return ledger_cast__get_contract_vote_yes_after_no(voting_id, subject_id, 1, -1)
            end,
            get_contract_vote_no_after_yes = function(voting_id, subject_id)
                return ledger_cast__get_contract_vote_no_after_yes(voting_id, subject_id, -1, 1)
            end,
            get_contract_vote_no_after_no = function(voting_id, subject_id)
                return ledger_cast__get_contract_vote_no_after_no(voting_id, subject_id, 0, 0)
            end,
            execute_contract = function(signed_contract)
                return ledger_cast__execute_contract(table_cast, signed_contract)
            end
        }
    end
end
