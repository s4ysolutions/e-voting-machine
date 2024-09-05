--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --

VOTER_STATUS = {
    NOT_REGISTERED = 0,
    REGISTERED = 1,
    BALLOT_ISSUED = 2,
}

local function register_voter(db_voters, voter_id, voting_id)
    local uid = db_voters.create_voter(voter_id, voting_id)
    return uid
end

local function _is_ballot_issued(ledger_issue_ballot, voter_id, voting_id)
    for _, v in pairs(ledger_issue_ballot.ledger) do
        if v.voter_id == voter_id and v.voting_id == voting_id then
            return true
        end
    end
    return false
end

local function request_voter_status(db_voters, ledger_issue_ballot, voter_id, voting_id)
    if db_voters.read_voter(voter_id, voting_id) == nil then
        return VOTER_STATUS.NOT_REGISTERED
    end
    if _is_ballot_issued(ledger_issue_ballot, voter_id, voting_id) then
        return VOTER_STATUS.BALLOT_ISSUED
    else
        return VOTER_STATUS.REGISTERED
    end
end

if arg[0]:match("voters%-registry%.lua$") then
    function test_voter_should_be_not_registered()
        -- Arrange
        local db_voters = require('db.voters')({}, {})
        local ledger_issue_ballot = require('ledgers.ballot-issues-ledger')({})
        -- Act
        local status = request_voter_status(db_voters, ledger_issue_ballot, 'voter_id', 'voting_id')
        -- Assert
        luaunit.assertEquals(status, VOTER_STATUS.NOT_REGISTERED)
    end

    function test_voter_should_be_registered()
        -- Arrange
        local db_voters = require('db.voters')({}, {})
        local ledger_issue_ballot = require('ledgers.ballot-issues-ledger')({})
        -- Act
        local uid = register_voter(db_voters, 'voter_id', 'voting_id')
        local status = request_voter_status(db_voters, ledger_issue_ballot, 'voter_id', 'voting_id')
        -- Assert
        luaunit.assertNotNil(uid)
        luaunit.assertEquals(status, VOTER_STATUS.REGISTERED)
    end

    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return function(db_voters, ledger_issue_ballot)
        return {
            register_voter = function(voter_id, voting_id)
                return register_voter(db_voters, voter_id, voting_id)
            end,
            request_voter_status = function(voter_id, voting_id)
                return request_voter_status(db_voters, ledger_issue_ballot, voter_id, voting_id)
            end,
            VOTER_STATUS = VOTER_STATUS
        }
    end
end
