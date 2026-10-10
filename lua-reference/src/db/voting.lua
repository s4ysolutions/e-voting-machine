--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]]

local uuid = require('uuid')

local function create_voting(table_voting, voting_id, status)
    table_voting[voting_id] = { status = status }
end

local function create_voting_subject(table_voting, table_voting_subjects, subject_id, voting_id)
    if not table_voting[voting_id] then
        error('Voting ' .. voting_id .. ' does not exist')
    end
    local id = uuid()
    table_voting_subjects[id] = { voting_id = voting_id, subject_id = subject_id }
end

local function read_voting(table_voting, table_voting_subjects, voting_id)
    if table_voting[voting_id] then
        subjects = {}
        for k, v in pairs(table_voting_subjects) do
            if v.voting_id == voting_id then
                subjects[k] = v
            end
        end
        return { status = table_voting[voting_id].status, subjects = subjects }
    end
end

local function set_status(table_voting, voting_id, status)
    if table_voting[voting_id] then
        table_voting[voting_id].status = status
    else
        error('Voting ' .. voting_id .. ' does not exist')
    end
end

if arg[0]:match("voting%.lua$") then
    function test_create_voting()
        -- Arrange
        local table_voting = {}
        -- Act
        create_voting(table_voting, 'voting_id', 'test_status')
        -- Assert
        luaunit.assertNotNil(table_voting['voting_id'])
        luaunit.assertEquals(table_voting['voting_id'].status, 'test_status')
    end

    function test_create_voting_subject_fail_when_no_voting()
        local table_voting = {}
        local table_voting_subjects = {}
        luaunit.assertErrorMsgContains('Voting voting_id does not exist', create_voting_subject,
            table_voting,
            table_voting_subjects,
            'subject_id_1',
            'voting_id')
    end

    function test_create_voting_subject()
        -- Arrange
        local table_voting = {}
        local table_voting_subjects = {}
        create_voting(table_voting, 'voting_id', 'test_status')
        -- Act
        create_voting_subject(table_voting, table_voting_subjects, 'subject_id_1', 'voting_id')
        create_voting_subject(table_voting, table_voting_subjects, 'subject_id_2', 'voting_id')
        -- Assert
        local subject_count = 0
        for k, v in pairs(table_voting_subjects) do
            if v.voting_id == 'voting_id' then
                subject_count = subject_count + 1
            end
        end
        luaunit.assertEquals(subject_count, 2)
    end

    function test_read_voting()
        -- Arrange
        local table_voting = {}
        local table_voting_subjects = {}
        create_voting(table_voting, 'voting_id', 'test_status')
        create_voting_subject(table_voting, table_voting_subjects, 'subject_id_1', 'voting_id')
        create_voting_subject(table_voting, table_voting_subjects, 'subject_id_2', 'voting_id')
        -- Act
        local voting = read_voting(table_voting, table_voting_subjects, 'voting_id')
        -- Assert
        luaunit.assertNotNil(voting)
        luaunit.assertNotNil(voting.subjects)
        local subject_count = 0
        for k, voting in pairs(voting.subjects) do
            if voting.voting_id == 'voting_id' then
                subject_count = subject_count + 1
            end
        end
        luaunit.assertEquals(subject_count, 2)
    end

    function test_set_status()
        -- Arrange
        local table_voting = {}
        create_voting(table_voting, 'voting_id', 'test_status')
        -- Act
        set_status(table_voting, 'voting_id', 'new_status')
        -- Assert
        luaunit.assertEquals(table_voting['voting_id'].status, 'new_status')
    end

    function test_set_status_should_error_when_voting_does_not_exist()
        -- Arrange
        local table_voting = {}
        -- Act
        -- Assert
        luaunit.assertErrorMsgContains('Voting voting_id does not exist', set_status,
            table_voting,
            'voting_id',
            'new_status')
    end

    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return function(table_voting, table_voting_subjects)
        return {
            create_voting = function(voting_id, status)
                return create_voting(table_voting, voting_id, status)
            end,
            create_voting_subject = function(subject_id, voting_id)
                return create_voting_subject(table_voting, table_voting_subjects, subject_id, voting_id)
            end,
            read_voting = function(voting_id)
                return read_voting(table_voting, table_voting_subjects, voting_id)
            end,
            set_status = function(voting_id, status)
                return set_status(table_voting, voting_id, status)
            end
        }
    end
end
