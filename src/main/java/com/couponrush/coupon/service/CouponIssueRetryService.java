package com.couponrush.coupon.service;

import com.couponrush.coupon.dto.CouponIssueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CouponIssueRetryService {

    private final CouponService couponService;

    @Retryable(
            includes = ObjectOptimisticLockingFailureException.class,
            maxRetries = 5,
            delay = 50
    )
    public CouponIssueResponse issueCouponWithRetry(Long eventId, Long userId) {
        return couponService.issueCoupon(eventId, userId);
    }
}
