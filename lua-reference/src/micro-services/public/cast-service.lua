--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --

function issue_ballot(db_voting, ballot_issues_ledger, cast_ledger, voter_id, voting_id)
    local voting = db_voting.read_voting(voting_id)
    if voting == nil then
        error("Voting does not exist")
    end
    for _, ballot in pairs(ballot_issues_ledger.ledger) do
        if ballot.voter_id == voter_id and ballot.voting_id == voting_id then
            error("Ballot already issued")
        end
    end
    local subjects = {}
    local subject_count = 0
    for subject_id, subject in pairs(voting.subjects) do
        assert(subject.voting_id == voting_id)
        subjects[subject_id] = {
            contract_yes = cast_ledger.get_contract_vote_yes(voting_id, subject.subject_id),
            contract_no = cast_ledger.get_contract_vote_no(voting_id, subject.subject_id)
        }
        subject_count = subject_count + 1
    end
    if subject_count == 0 then
        error("No subjects found")
    end
    ballot_issues_ledger.add_ballot_issued_mark(voter_id, voting_id)
    return { subjects = subjects }
end

if arg[0]:match("cast%-service%.lua$") then
    function test_issue_ballot()
        local db_voting = require("db.voting")({}, {})
        local db_voters = require("db.voters")({})
        local voting_registry = require('micro-services.public.voting-registry')(db_voting)
        local voters_registry = require('micro-services.public.voters-registry')(db_voters, cast_ledger)
        local cast_ledger = require("ledgers.cast-ledger")({})
        local ballot_issues_ledger = require('ledgers.ballot-issues-ledger')({})
        -- Arrange
        local voting_id = "voting_id"
        voting_registry.register_voting(voting_id, { "subject1", "subject2" })
        local voter_id = voters_registry.register_voter("voter_id", voting_id)

        -- precondition
        local voting = db_voting.read_voting(voting_id)
        luaunit.assertNotNil(voting)
        luaunit.assertNotNil(voting.subjects)
        local subject_count = 0
        for _, _ in pairs(voting.subjects) do
            subject_count = subject_count + 1
        end
        luaunit.assertEquals(subject_count, 2)

        -- Act
        local result = issue_ballot(db_voting, ballot_issues_ledger, cast_ledger, voter_id, voting_id)

        -- Assert
        luaunit.assertNotNil(result)
        luaunit.assertNotNil(result.subjects)

        local subject_count = 0
        for _, subject in pairs(result.subjects) do
            subject_count = subject_count + 1
            luaunit.assertNotNil(subject.contract_yes)
            luaunit.assertNotNil(subject.contract_no)
        end
        luaunit.assertEquals(subject_count, 2)
    end

    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return {
        issue_ballot = issue_ballot
    }
end
