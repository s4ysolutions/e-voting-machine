--[[
E-Voting Machine © 2024 by S4Y Solutions is licensed under CC BY-NC-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
]] --

local md5 = require('md5')
local base64 = require('base64')

local function sign(secret, contract)
    nonce = math.random()
    digest = base64.encode(md5.sum(contract .. '|' .. secret .. '|' .. nonce))
    return contract .. '|' .. nonce .. '|' .. digest
end

local function extract(secret, signed)
    local contract, nonce, digest = signed:match('^(.*)|(.*)|(.*)$')
    if not contract or not nonce or not digest then
        return nil
    end
    if base64.encode(md5.sum(contract .. '|' .. secret .. '|' .. nonce)) == digest then
        return contract
    end
end

local function verify(secret, signed)
    local contract = extract(secret, signed)
    return contract ~= nil
end

if arg[0]:match("signing%.lua$") then
    local secret = 'test'

    function test_sign()
        local contract = 'print("Hello, World!")'
        local signed = sign(secret, contract)
        local contract0, nonce, digest = signed:match('^(.*)|(.*)|(.*)$')
        luaunit.assertEquals(contract, contract0)
        luaunit.assertNotNil(nonce)
        luaunit.assertNotNil(digest)
    end

    function test_extract()
        local contract = 'print("Hello, World!")'
        local signed = sign(secret, contract)
        contract0 = extract(secret, signed)
        luaunit.assertEquals(contract0, contract)
    end

    function test_verify()
        local code = 'print("Hello, World!")'
        local signed = sign(secret, code)
        luaunit.assertTrue(verify(secret, signed))
    end

    function test_verify_tampered()
        local code = 'print("Hello, World!")'
        local signed = sign(secret, code)
        local tampered = 'x' .. signed
        luaunit.assertFalse(verify(secret, tampered))
    end

    function test_verify_lambda()
        local _extract = function(signed)
            return extract(secret, signed)
        end
        local signed = sign(secret, 'Hello, World!')
        local code = _extract(signed)
        luaunit.assertEquals(code, 'Hello, World!')
    end

    luaunit = require('luaunit')
    os.exit(luaunit.LuaUnit.run())
else
    return function(secret)
        return {
            sign = function(secret_or_code, code)
                if code then
                    return sign(secret_or_code, code)
                else
                    return sign(secret, secret_or_code)
                end
            end,
            verify = function(secret_or_signed, signed)
                if signed then
                    return verify(secret_or_signed, signed)
                else
                    return verify(secret, secret_or_signed)
                end
            end,
            extract = function(secret_or_signed, signed)
                if signed then
                    return extract(secret_or_signed, signed)
                else
                    return extract(secret, secret_or_signed)
                end
            end
        }
    end
end
