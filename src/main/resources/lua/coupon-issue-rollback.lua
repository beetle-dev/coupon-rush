if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
    return redis.call('INCR', KEYS[1])
end
return -1
