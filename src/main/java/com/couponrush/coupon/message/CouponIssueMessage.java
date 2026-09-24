package com.couponrush.coupon.message;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record CouponIssueMessage(
        Long eventId,
        Long userId,
        String code,
        long issuedAtEpochMilli) {

    public static CouponIssueMessage of(Long eventId, Long userId, String code, Instant issuedAt) {
        return new CouponIssueMessage(eventId, userId, code, issuedAt.toEpochMilli());
    }

    public LocalDateTime issuedAt() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(issuedAtEpochMilli), ZoneId.systemDefault());
    }

    public String partitionKey() {
        return eventId + ":" + userId;
    }
}
