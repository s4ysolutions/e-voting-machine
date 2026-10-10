--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --

local VOTING_STATUS = {
    VOTING_STATUS_NOT_EXIST = 0,
    VOTING_STATUS_PLANNED = 1,
    VOTING_STATUS_OPEN = 2,
    VOTING_STATUS_CLOSED = 3,
}

local function register_voting(db_voting, voting_id, subjects_ids)
    db_voting.create_voting(voting_id, VOTING_STATUS.VOTING_STATUS_PLANNED)
    for _, subject_id in ipairs(subjects_ids) do
        db_voting.create_voting_subject(subject_id, voting_id)
    end
    return VOTING_STATUS.VOTING_STATUS_NOT_EXIST
end

local function open_voting(db_voting, voting_id)
    voting = db_voting.read_voting(voting_id)
    if voting then
        if voting.status == VOTING_STATUS.VOTING_STATUS_CLOSED then
            error('Voting is closed and cannot be re-opened')
        end
        db_voting.set_status(voting_id, VOTING_STATUS.VOTING_STATUS_OPEN)
        return voting.status
    end
    error('Voting does not exist')
end

local function close_voting(db_voting, voting_id)
    voting = db_voting.read_voting(voting_id)
    if voting then
        db_voting.set_status(voting_id, VOTING_STATUS.VOTING_STATUS_CLOSED)
        return voting.status
    end
    error('Voting does not exist')
end

local function get_voting_status(db_voting, voting_id)
    voting = db_voting.read_voting(voting_id)
    if voting then
        return voting.status
    end
    return VOTING_STATUS.VOTING_STATUS_NOT_EXIST
end

if arg[0]:match("voting%-registry%.lua$") then
    function test_voting_should_change_status()
        local db_voting = require('db.voting')({}, {})

        local status = get_voting_status(db_voting, 'voting_id')
        luaunit.assertEquals(status, VOTING_STATUS.VOTING_STATUS_NOT_EXIST)

        luaunit.assertErrorMsgContains('Voting does not exist', open_voting,
            db_voting,
            'voting_id')
        luaunit.assertErrorMsgContains('Voting does not exist', close_voting,
            db_voting,
            'voting_id')

        status = register_voting(db_voting, 'voting_id', { 'subject_id_1', 'subject_id_2' })
        luaunit.assertEquals(status, VOTING_STATUS.VOTING_STATUS_NOT_EXIST)
        status = get_voting_status(db_voting, 'voting_id')
        luaunit.assertEquals(status, VOTING_STATUS.VOTING_STATUS_PLANNED)

        status = open_voting(db_voting, 'voting_id')
        luaunit.assertEquals(status, VOTING_STATUS.VOTING_STATUS_PLANNED)
        status = get_voting_status(db_voting, 'voting_id')
        luaunit.assertEquals(status, VOTING_STATUS.VOTING_STATUS_OPEN)

        status = close_voting(db_voting, 'voting_id')
        luaunit.assertEquals(status, VOTING_STATUS.VOTING_STATUS_OPEN)
        status = get_voting_status(db_voting, 'voting_id')
        luaunit.assertEquals(status, VOTING_STATUS.VOTING_STATUS_CLOSED)

        luaunit.assertErrorMsgContains('Voting is closed and cannot be re-opened',
            open_voting,
            db_voting,
            'voting_id')
    end

    -- not-unit test
    function test_voting_should_not_exist()
        -- Arrange
        local db_voting = require('db.voting')({}, {})

        register_voting(db_voting, 'voting_id', { 'subject_id_1', 'subject_id_2' })
        -- Act
        local voting = db_voting.read_voting('voting_id')
        -- Assert
        luaunit.assertNotNil(voting)
        luaunit.assertNotNil(voting.subjects)
        local subject_count = 0
        for id, subject in pairs(voting.subjects) do
            subject_count = subject_count + 1
        end
        luaunit.assertEquals(2, subject_count)
    end

    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return function(db_voting)
        return {
            VOTING_STATUS = VOTING_STATUS,
            register_voting = function(voting_id, subjects_ids)
                return register_voting(db_voting, voting_id, subjects_ids)
            end,
            open_voting = function(voting_id)
                return open_voting(db_voting, voting_id)
            end,
            close_voting = function(voting_id)
                return voting_registry__close_voting(db_voting, voting_id)
            end,
            get_voting_status = function(voting_id)
                return get_voting_status(db_voting, voting_id)
            end
        }
    end
end
