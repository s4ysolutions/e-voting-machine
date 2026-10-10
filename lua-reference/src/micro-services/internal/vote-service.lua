--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --

local uuid = require("uuid")
local cast_ledger = require("ledgers.cast-ledger")

local function request_first_vote(voter_id, voting_id, subject_id)
    local voter_status = voters__request_voter_status(voter_id, voting_id)
    if voter_status == VOTER_STATUS.NOT_REGISTERED then
        error("Voter is not registered")
    end
    if voter_status == VOTER_STATUS.VOTED then
        error("Voter has already voted")
    end
    local id = uuid()
    local contract_yes = cast_ledger.get_contract_vote_yes(id, voting_id, subject_id)
    local contract_no = cast_ledger.get_contract_vote_no(id, voting_id, subject_id)
    return {
        id = cast_id,
        contract_yes = contract.sign(cast_id, contract_yes),
        contract_no = contract.sign(cast_id, contract_no)
    }
end

local function request_next_vote(voter_id, voting_id, subject_id, previous_vote)
    local voter_status = voters__request_voter_status(voter_id, voting_id)
    if voter_status == VOTER_STATUS.NOT_REGISTERED then
        error("Voter is not registered")
    end
    if voter_status == VOTER_STATUS.VOTED then
        error("Voter has already voted")
    end
    local id = uuid()
    local contract_yes = cast_ledger.get_contract_vote_yes(id, voting_id, subject_id)
    local contract_no = cast_ledger.get_contract_vote_no(id, voting_id, subject_id)
    return {
        id = cast_id,
        contract_yes = contract.sign(cast_id, contract_yes),
        contract_no = contract.sign(cast_id, contract_no)
    }
end

return {
    request_first_vote = request_first_vote,
    request_next_vote = request_next_vote
}
