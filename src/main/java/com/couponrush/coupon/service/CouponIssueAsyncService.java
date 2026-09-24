package com.couponrush.coupon.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.dto.CouponIssueAcceptedResponse;
import com.couponrush.coupon.message.CouponIssueMessage;
import com.couponrush.coupon.message.CouponIssueMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueAsyncService {

    private final CouponStockReservationService couponStockReservationService;
    private final CouponIssueMessageProducer couponIssueMessageProducer;
    private final CouponCodeGenerator couponCodeGenerator;

    public CouponIssueAcceptedResponse issueCoupon(Long eventId, Long userId) {

        couponStockReservationService.reserve(eventId, userId);

        CouponIssueMessage message = CouponIssueMessage.of(
                eventId, userId, couponCodeGenerator.generate(), Instant.now());

        try {
            couponIssueMessageProducer.send(message);
        } catch (RuntimeException e) {
            couponStockReservationService.release(eventId, userId, e);
            throw new CustomRuntimeException(ErrorCode.COUPON_ISSUE_ENQUEUE_FAILED);
        }

        return CouponIssueAcceptedResponse.pending(message.code());
    }
}
