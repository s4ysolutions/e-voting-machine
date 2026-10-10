--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --

package.path = package.path .. ";../src/?.lua"

require 'busted.runner' ()

local _uuid = require("uuid")
local voting_registry = require("micro-services.public.voting-registry")
local voters_registry = require("micro-services.public.voters-registry")
local cast_service = require("micro-services.public.cast-service")
local reset_services = require('micro-services.internal.clear-all')

describe("public microservices integration tests", function()
    describe("issuing ballots", function()
        local voting_id = "voting_id"
        local voter_id
        before_each(function()
            voting_registry.register_voting(voting_id, { "subject_id_1", "subject_id_2" })
            voter_id = voters_registry.register_voter("voter_id", "voting_id")
            voting_registry.open_voting(voting_id)
        end)

        it("cast_service should issue ballot", function()
            -- Act
            local result = cast_service.issue_ballot(voter_id, voting_id)

            -- Assert
            assert.is_not_nil(result)
            assert.is_not_nil(result.subjects)

            local subject_count = 0
            for id, subject in pairs(result.subjects) do
                subject_count = subject_count + 1
                assert.is_not_nil(subject.contract_yes)
                assert.is_not_nil(subject.contract_no)
            end
            assert.are_equal(2, subject_count)
        end)

        it("voter should not be able to issue ballot twice", function()
            -- Arrange
            cast_service.issue_ballot(voter_id, voting_id)
            -- Act & Assert
            assert.has_error(function() cast_service.issue_ballot(voter_id, voting_id) end, "Ballot already issued")
        end)
    end)
end)
