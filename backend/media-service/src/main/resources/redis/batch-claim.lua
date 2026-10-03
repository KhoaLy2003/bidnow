-- Claim one due window. Only the caller whose ZREM succeeds gets the fields; the window is deleted. Atomic.
-- KEYS[1] batch hash, KEYS[2] due sorted set
-- ARGV[1] now (epoch ms): the window is claimed only if it is still due at that time, so a stale claim
-- cannot steal a window that was deleted and re-opened with a later due time.
-- Returns the hash as a flat field/value list, or an empty list if another instance claimed it, it expired,
-- or it is no longer due.
local score = redis.call('ZSCORE', KEYS[2], KEYS[1])
if (not score) or tonumber(score) > tonumber(ARGV[1]) then
  return {}
end
if redis.call('ZREM', KEYS[2], KEYS[1]) == 0 then
  return {}
end
local fields = redis.call('HGETALL', KEYS[1])
redis.call('DEL', KEYS[1])
return fields
