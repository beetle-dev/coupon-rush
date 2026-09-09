package com.couponrush.coupon.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.dto.CouponGetResponse;
import com.couponrush.coupon.dto.CouponIssueResponse;
import com.couponrush.event.service.EventService;
import com.couponrush.coupon.domain.Coupon;
import com.couponrush.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final EventService eventService;

    private static final int COUPON_VALID_PERIOD = 6;

    @Transactional
    public CouponIssueResponse issueCoupon(Long eventId, Long userId) {

        Coupon coupon;
        try {
            eventService.decreaseRemainingQty(eventId);

            LocalDateTime now = LocalDateTime.now();
            coupon = couponRepository.save(Coupon.builder()
                    .code(generateCode())
                    .validFrom(now)
                    .validUntil(now.plusMonths(COUPON_VALID_PERIOD))
                    .userId(userId)
                    .issuedAt(now)
                    .eventId(eventId)
                    .build());

            return new CouponIssueResponse(coupon.getId());

        } catch (DataIntegrityViolationException e) {
            throw new CustomRuntimeException(ErrorCode.COUPON_DUPLICATE);
        }
    }

    private String generateCode() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }

    @Transactional(readOnly = true)
    public CouponGetResponse getCoupon(Long couponId) {

        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomRuntimeException(ErrorCode.COUPON_NOT_FOUND))
                .toCouponGetResponse();
    }
}
