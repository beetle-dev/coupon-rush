package com.couponrush.coupon.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.dto.CouponIssueResponse;
import com.couponrush.coupon.repository.CouponRepository;
import com.couponrush.coupon.repository.CouponStockRedisRepository;
import com.couponrush.event.domain.Event;
import com.couponrush.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueAtomicService {

    private static final String INIT_LOCK_KEY_PREFIX = "lock:event:init:";
    private static final long INIT_LOCK_WAIT_SECONDS = 3L;
    private static final long INIT_LOCK_LEASE_SECONDS = 5L;

    private final CouponStockRedisRepository couponStockRedisRepository;
    private final CouponRepository couponRepository;
    private final CouponService couponService;
    private final EventService eventService;
    private final RedissonClient redissonClient;

    public CouponIssueResponse issueCoupon(Long eventId, Long userId) {

        long result = couponStockRedisRepository.tryIssue(eventId, userId, System.currentTimeMillis());

        if (result == CouponStockRedisRepository.ISSUE_NOT_INITIALIZED) {
            warmUp(eventId);
            result = couponStockRedisRepository.tryIssue(eventId, userId, System.currentTimeMillis());
        }

        throwIfFailed(result);

        try {
            return couponService.issueCouponAtomic(eventId, userId);
        } catch (RuntimeException e) {
            compensate(eventId, userId, e);
            throw e;
        }
    }

    private void warmUp(Long eventId) {

        RLock initLock = redissonClient.getLock(INIT_LOCK_KEY_PREFIX + eventId);
        boolean acquired = false;

        try {
            acquired = initLock.tryLock(INIT_LOCK_WAIT_SECONDS, INIT_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);

            if (!acquired) {
                throw new CustomRuntimeException(ErrorCode.COUPON_ISSUE_CONFLICT);
            }

            if (couponStockRedisRepository.isInitialized(eventId)) {
                return;
            }

            Event event = eventService.getEventEntity(eventId);

            couponStockRedisRepository.initMeta(eventId, event.getIssueStartAt(), event.getIssueEndAt());
            couponStockRedisRepository.addIssuedUsers(eventId, couponRepository.findUserIdsByEventId(eventId));
            couponStockRedisRepository.initStockIfAbsent(eventId, event.getRemainingQuantity());

            log.info("Redis 재고 초기화 완료. eventId={}, remainingQuantity={}", eventId, event.getRemainingQuantity());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomRuntimeException(ErrorCode.COUPON_ISSUE_CONFLICT);
        } finally {
            if (acquired && initLock.isHeldByCurrentThread()) {
                initLock.unlock();
            }
        }
    }

    private void compensate(Long eventId, Long userId, RuntimeException cause) {

        try {
            couponStockRedisRepository.rollbackIssue(eventId, userId);
        } catch (Exception redisException) {
            log.error("재고 보상 실패. 수동 정합성 복구 필요. eventId={}, userId={}", eventId, userId, redisException);
            return;
        }

        log.warn("DB 영속화 실패로 Redis 선점분 반환. eventId={}, userId={}, cause={}",
                eventId, userId, cause.toString());
    }

    private void throwIfFailed(long result) {

        if (result == CouponStockRedisRepository.ISSUE_SUCCESS) {
            return;
        }

        throw new CustomRuntimeException(toErrorCode(result));
    }

    private ErrorCode toErrorCode(long result) {

        if (result == CouponStockRedisRepository.ISSUE_DUPLICATE) {
            return ErrorCode.COUPON_DUPLICATE;
        }
        if (result == CouponStockRedisRepository.ISSUE_OUT_OF_STOCK) {
            return ErrorCode.COUPON_OUT_OF_STOCK;
        }
        if (result == CouponStockRedisRepository.ISSUE_INVALID_PERIOD) {
            return ErrorCode.COUPON_INVALID_ISSUE_PERIOD;
        }

        return ErrorCode.COUPON_ISSUE_CONFLICT;
    }
}
