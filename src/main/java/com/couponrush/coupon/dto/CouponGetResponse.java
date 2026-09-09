package com.couponrush.coupon.dto;

import java.time.LocalDateTime;

public record CouponGetResponse(
        Long couponId,
        String code,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Long userId,
        LocalDateTime issuedAt,
        LocalDateTime usedAt,
        Long eventId) {
}
