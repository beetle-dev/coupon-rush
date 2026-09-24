package com.couponrush.coupon.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CouponStockRedisRepository {

    private static final String STOCK_KEY_PREFIX = "stock:event:";
    private static final String ISSUED_KEY_PREFIX = "issued:event:";
    private static final String META_KEY_PREFIX = "meta:event:";

    private static final String META_FIELD_START_AT = "startAt";
    private static final String META_FIELD_END_AT = "endAt";

    public static final long ISSUE_SUCCESS = 1L;
    public static final long ISSUE_DUPLICATE = -1L;
    public static final long ISSUE_OUT_OF_STOCK = -2L;
    public static final long ISSUE_INVALID_PERIOD = -3L;
    public static final long ISSUE_NOT_INITIALIZED = -9L;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> couponIssueScript;
    private final RedisScript<Long> couponIssueRollbackScript;

    public long tryIssue(Long eventId, Long userId, long nowEpochMilli) {

        Long result = redisTemplate.execute(
                couponIssueScript,
                List.of(stockKey(eventId), issuedKey(eventId), metaKey(eventId)),
                String.valueOf(userId),
                String.valueOf(nowEpochMilli));

        return result == null ? ISSUE_NOT_INITIALIZED : result;
    }

    public void rollbackIssue(Long eventId, Long userId) {

        redisTemplate.execute(
                couponIssueRollbackScript,
                List.of(stockKey(eventId), issuedKey(eventId)),
                String.valueOf(userId));
    }

    public void initialize(Long eventId, int remainingQuantity,
                           LocalDateTime issueStartAt, LocalDateTime issueEndAt) {

        initMeta(eventId, issueStartAt, issueEndAt);
        initStockIfAbsent(eventId, remainingQuantity);
    }

    public void initMeta(Long eventId, LocalDateTime issueStartAt, LocalDateTime issueEndAt) {

        redisTemplate.opsForHash().putAll(metaKey(eventId), Map.of(
                META_FIELD_START_AT, String.valueOf(toEpochMilli(issueStartAt)),
                META_FIELD_END_AT, String.valueOf(toEpochMilli(issueEndAt))));
    }

    public void addIssuedUsers(Long eventId, Collection<Long> userIds) {

        if (userIds.isEmpty()) {
            return;
        }

        redisTemplate.opsForSet().add(issuedKey(eventId),
                userIds.stream().map(String::valueOf).toArray(String[]::new));
    }

    public boolean initStockIfAbsent(Long eventId, int remainingQuantity) {

        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(stockKey(eventId), String.valueOf(remainingQuantity)));
    }

    public boolean isInitialized(Long eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(stockKey(eventId)));
    }

    public Integer getStock(Long eventId) {

        String stock = redisTemplate.opsForValue().get(stockKey(eventId));
        return stock == null ? null : Integer.valueOf(stock);
    }

    public Long countIssuedUsers(Long eventId) {
        return redisTemplate.opsForSet().size(issuedKey(eventId));
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String stockKey(Long eventId) {
        return STOCK_KEY_PREFIX + eventId;
    }

    private String issuedKey(Long eventId) {
        return ISSUED_KEY_PREFIX + eventId;
    }

    private String metaKey(Long eventId) {
        return META_KEY_PREFIX + eventId;
    }
}
