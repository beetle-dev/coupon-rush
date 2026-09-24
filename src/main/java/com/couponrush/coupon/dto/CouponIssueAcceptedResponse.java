package com.couponrush.coupon.dto;

public record CouponIssueAcceptedResponse(String code, CouponIssueStatus status) {

    public static CouponIssueAcceptedResponse pending(String code) {
        return new CouponIssueAcceptedResponse(code, CouponIssueStatus.PENDING);
    }

    public enum CouponIssueStatus {
        PENDING
    }
}
