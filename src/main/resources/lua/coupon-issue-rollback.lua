-- 보상 처리: DB 영속화가 실패했을 때 Redis에서 선점한 재고를 되돌린다.
--
-- KEYS[1] = stock:event:{eventId}
-- KEYS[2] = issued:event:{eventId}
-- ARGV[1] = userId
--
-- return >= 0 : 복구 후 재고
--        -1   : 이미 복구됨 (멱등)
--
-- SREM 성공을 조건으로 INCR을 건다. 보상이 두 번 호출돼도 재고가 부풀지 않는다.

if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
    return redis.call('INCR', KEYS[1])
end
return -1
