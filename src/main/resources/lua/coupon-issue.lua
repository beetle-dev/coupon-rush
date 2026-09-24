local stock = redis.call('GET', KEYS[1])
if stock == false then
    return -9
end

local meta = redis.call('HMGET', KEYS[3], 'startAt', 'endAt')
local startAt = tonumber(meta[1])
local endAt = tonumber(meta[2])
if startAt == nil or endAt == nil then
    return -9
end

local now = tonumber(ARGV[2])
if now < startAt or now > endAt then
    return -3
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -1
end

if tonumber(stock) <= 0 then
    return -2
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 1
