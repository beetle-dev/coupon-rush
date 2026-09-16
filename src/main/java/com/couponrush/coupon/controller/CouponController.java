package com.couponrush.coupon.controller;

import com.couponrush.coupon.dto.CouponGetResponse;
import com.couponrush.coupon.dto.CouponIssueResponse;
import com.couponrush.coupon.service.CouponIssueRetryService;
import com.couponrush.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/events/{eventId}/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final CouponIssueRetryService couponIssueRetryService;

    @PostMapping
    public ResponseEntity<CouponIssueResponse> issueCoupon(@PathVariable("eventId") Long eventId,
                                                           @RequestHeader("X-User-Id") Long userId) {

        CouponIssueResponse result = couponIssueRetryService.issueCouponWithRetry(eventId, userId);

        return ResponseEntity
                .created(URI.create("/events/" + eventId + "/coupons/" + result.couponId()))
                .body(result);
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<CouponGetResponse> getCoupon(@PathVariable("couponId") Long couponId) {

        return ResponseEntity
                .ok(couponService.getCoupon(couponId));
    }
}
