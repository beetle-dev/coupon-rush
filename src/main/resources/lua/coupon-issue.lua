-- 쿠폰 발급 원자 처리: 발급 기간 검증 + 1인 1매 중복 검사 + 재고 차감
--
-- KEYS[1] = stock:event:{eventId}   (String, 남은 재고)
-- KEYS[2] = issued:event:{eventId}  (Set, 이미 발급받은 userId)
-- KEYS[3] = meta:event:{eventId}    (Hash, startAt/endAt: epoch millis)
-- ARGV[1] = userId
-- ARGV[2] = now (epoch millis) — 애플리케이션이 주입한다.
--           redis.call('TIME')을 쓰면 스크립트가 비결정적이 되고 테스트에서 시간 고정이 불가능해진다.
--
-- return  1 : 발급 성공 (재고 1 차감 + issued Set 등록 완료)
--        -1 : 중복 발급 (1인 1매 위반)
--        -2 : 재고 소진
--        -3 : 발급 가능 기간 아님
--        -9 : Redis 미초기화 (warm-up 필요)
--
-- 참고: Redis Cluster로 확장한다면 세 키가 같은 슬롯에 놓이도록
--       stock:event:{123} 처럼 해시태그가 필요하다. 단일 노드인 지금은 불필요.

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

-- 재고보다 중복 검사를 먼저 한다.
-- 이미 발급받은 사용자는 재고가 0이어도 OUT_OF_STOCK이 아니라 DUPLICATE로 응답해야 하기 때문.
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -1
end

-- DECR이 음수로 내려갈 여지를 여기서 차단한다.
-- 스크립트 전체가 단일 원자 실행이라 이 검사와 아래 DECR 사이에 끼어들 틈이 없다.
-- 이것이 초과 발급 0건의 근거다.
if tonumber(stock) <= 0 then
    return -2
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 1
