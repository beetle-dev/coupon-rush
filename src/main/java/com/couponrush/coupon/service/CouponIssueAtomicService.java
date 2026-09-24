package com.couponrush.coupon.service;

import com.couponrush.coupon.dto.CouponIssueResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueAtomicService {

    private final CouponStockReservationService couponStockReservationService;
    private final CouponService couponService;

    public CouponIssueResponse issueCoupon(Long eventId, Long userId) {

        couponStockReservationService.reserve(eventId, userId);

        try {
            return couponService.issueCouponAtomic(eventId, userId);
        } catch (RuntimeException e) {
            couponStockReservationService.release(eventId, userId, e);
            throw e;
        }
    }
}
