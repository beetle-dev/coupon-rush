package com.couponrush.coupon.controller;

import com.couponrush.coupon.dto.CouponGetResponse;
import com.couponrush.coupon.dto.CouponIssueAcceptedResponse;
import com.couponrush.coupon.service.CouponIssueAsyncService;
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

    private final CouponIssueAsyncService couponIssueAsyncService;

    @PostMapping
    public ResponseEntity<CouponIssueAcceptedResponse> issueCoupon(@PathVariable("eventId") Long eventId,
                                                                   @RequestHeader("X-User-Id") Long userId) {

        CouponIssueAcceptedResponse result = couponIssueAsyncService.issueCoupon(eventId, userId);

        return ResponseEntity
                .accepted()
                .location(URI.create("/events/" + eventId + "/coupons?userId=" + userId))
                .body(result);
    }

    @GetMapping
    public ResponseEntity<CouponGetResponse> getCouponByUser(@PathVariable("eventId") Long eventId,
                                                             @RequestParam("userId") Long userId) {

        return ResponseEntity
                .ok(couponService.getCoupon(eventId, userId));
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<CouponGetResponse> getCoupon(@PathVariable("couponId") Long couponId) {

        return ResponseEntity
                .ok(couponService.getCoupon(couponId));
    }
}
