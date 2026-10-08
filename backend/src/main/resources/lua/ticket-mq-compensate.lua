if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then return redis.call('INCR', KEYS[1]) end
return tonumber(redis.call('GET', KEYS[1]) or '-1')
