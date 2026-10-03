-- Open a batching window or count a bid into the open one. Atomic.
-- KEYS[1] batch hash, KEYS[2] due sorted set
-- ARGV[1] now (epoch ms)  ARGV[2] due at (epoch ms)  ARGV[3] ttl (ms)  ARGV[4] bidId
-- ARGV[5] amount ('' if none)  ARGV[6] auction title ('' if none)  ARGV[7] kind  ARGV[8] userId  ARGV[9] auctionId
-- Returns IMMEDIATE (send now), BATCHED (counted for the flush) or DUPLICATE (a batched bid seen before).
local bidField = 'bid:' .. ARGV[4]
local seen = redis.call('HGET', KEYS[1], bidField)
if seen then
  if seen == 'IMMEDIATE' then
    return 'IMMEDIATE'
  end
  return 'DUPLICATE'
end
if redis.call('HSETNX', KEYS[1], 'windowStart', ARGV[1]) == 1 then
  redis.call('HSET', KEYS[1], 'count', '0', 'latestAmount', ARGV[5], 'auctionTitle', ARGV[6],
    'kind', ARGV[7], 'userId', ARGV[8], 'auctionId', ARGV[9], bidField, 'IMMEDIATE')
  redis.call('PEXPIRE', KEYS[1], ARGV[3])
  redis.call('ZADD', KEYS[2], ARGV[2], KEYS[1])
  return 'IMMEDIATE'
end
redis.call('HINCRBY', KEYS[1], 'count', 1)
redis.call('HSET', KEYS[1], 'latestAmount', ARGV[5], 'auctionTitle', ARGV[6], bidField, 'BATCHED')
return 'BATCHED'
