package com.couponrush.coupon.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.dto.CouponIssueResponse;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CouponIssueLockService {

    private final RedissonClient redissonClient;
    private final CouponService couponService;

    private static final long WAIT_TIME_SECONDS = 3L;
    private static final long LEASE_TIME_SECONDS = 3L;

    public CouponIssueResponse issueCoupon(Long eventId, Long userId) {

        RLock lock = redissonClient.getLock("lock:event:" + eventId);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS);

            if (!acquired) {
                throw new CustomRuntimeException(ErrorCode.COUPON_ISSUE_CONFLICT);
            }

            return couponService.issueCoupon(eventId, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomRuntimeException(ErrorCode.COUPON_ISSUE_CONFLICT);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
