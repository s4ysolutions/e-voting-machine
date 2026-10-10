--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]]

local uuid = require("uuid")

local function create_voter(table_voters, voter_id, voting_id)
    if read_voter(table_voters, voter_id, voting_id) then
        error("Voter already registered")
    end
    local id = uuid()
    table_voters[id] = { voter_id = voter_id, voting_id = voting_id }
    return id
end

function read_voter(table_voters, voter_id, voting_id)
    for k, v in pairs(table_voters) do
        if v.voter_id == voter_id and v.voting_id == voting_id then
            return k
        end
    end
end

if arg[0]:match("voters%.lua$") then
    function test_create_voter()
        -- Arrange
        local table_voters = {}
        -- Act
        local id = create_voter(table_voters, 'voter_id', 'voting_id')
        -- Assert
        luaunit.assertNotNil(table_voters[id])
        luaunit.assertEquals(table_voters[id].voter_id, 'voter_id')
        luaunit.assertEquals(table_voters[id].voting_id, 'voting_id')
    end

    function test_create_voter_twice()
        local table_voters = {}
        create_voter(table_voters, 'voter_id', 'voting_id')
        luaunit.assertErrorMsgContains('Voter already registered',
            create_voter,
            table_voters,
            'voter_id', 'voting_id')
    end

    function test_find_voter()
        local table_voters = {}
        create_voter(table_voters, 'voter_id', 'voting_id')
        local id = read_voter(table_voters, 'voter_id', 'voting_id')
        luaunit.assertNotNil(id)
        luaunit.assertEquals(table_voters[id].voter_id, 'voter_id')
        luaunit.assertEquals(table_voters[id].voting_id, 'voting_id')
    end

    function test_find_voter_not_found()
        local table_voters = {}
        create_voter(table_voters, 'voter_id', 'voting_id')
        local id = read_voter(table_voters, 'voter_id', 'voting_id_2')
        luaunit.assertIsNil(id)
    end

    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return function(table_voters)
        return {
            create_voter = function(voter_id, voting_id) return create_voter(table_voters, voter_id, voting_id) end,
            read_voter = function(voter_id, voting_id) return read_voter(table_voters, voter_id, voting_id) end
        }
    end
end
