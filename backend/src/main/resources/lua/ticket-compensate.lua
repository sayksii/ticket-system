if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
    return redis.call('INCR', KEYS[1])
end

local stock = redis.call('GET', KEYS[1])

if not stock then
    return -1
end

return tonumber(stock)
