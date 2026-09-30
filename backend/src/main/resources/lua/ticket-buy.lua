local stock = redis.call('GET', KEYS[1])

if not stock then
    return -3
end

local status = redis.call('GET', KEYS[3])

if status ~= 'OPEN' then
    return -4
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

if tonumber(stock) <= 0 then
    return -1
end

local remaining = redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])

return remaining
