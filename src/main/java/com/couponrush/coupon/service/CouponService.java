package com.couponrush.coupon.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.dto.CouponGetResponse;
import com.couponrush.coupon.dto.CouponIssueResponse;
import com.couponrush.coupon.message.CouponIssueMessage;
import com.couponrush.event.service.EventService;
import com.couponrush.coupon.domain.Coupon;
import com.couponrush.coupon.repository.CouponRepository;
import com.couponrush.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;
    private final CouponCodeGenerator couponCodeGenerator;

    private static final int COUPON_VALID_PERIOD = 6;

    @Transactional
    public CouponIssueResponse issueCoupon(Long eventId, Long userId) {

        Coupon coupon;
        try {
            eventService.decreaseRemainingQty(eventId);

            LocalDateTime now = LocalDateTime.now();
            coupon = couponRepository.save(Coupon.builder()
                    .code(couponCodeGenerator.generate())
                    .validFrom(now)
                    .validUntil(now.plusMonths(COUPON_VALID_PERIOD))
                    .userId(userId)
                    .issuedAt(now)
                    .eventId(eventId)
                    .build());

            return new CouponIssueResponse(coupon.getId());

        } catch (DataIntegrityViolationException e) {
            if (isDuplicateIssue(e)) {
                throw new CustomRuntimeException(ErrorCode.COUPON_DUPLICATE);
            }
            throw e;
        }
    }

    @Transactional
    public CouponIssueResponse issueCouponAtomic(Long eventId, Long userId) {

        try {
            LocalDateTime now = LocalDateTime.now();

            Long couponId = couponRepository.save(Coupon.builder()
                    .code(couponCodeGenerator.generate())
                    .validFrom(now)
                    .validUntil(now.plusMonths(COUPON_VALID_PERIOD))
                    .userId(userId)
                    .issuedAt(now)
                    .eventId(eventId)
                    .build()).getId();

            if (eventRepository.decreaseRemainingQuantity(eventId) == 0) {
                throw new CustomRuntimeException(ErrorCode.COUPON_OUT_OF_STOCK);
            }

            return new CouponIssueResponse(couponId);

        } catch (DataIntegrityViolationException e) {
            if (isDuplicateIssue(e)) {
                throw new CustomRuntimeException(ErrorCode.COUPON_DUPLICATE);
            }
            throw e;
        }
    }

    private boolean isDuplicateIssue(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cause) {
            String name = cause.getConstraintName();
            return "coupon.UQ_event_id_user_id".equals(name);
        }
        return false;
    }

    @Transactional
    public void persistIssuedCoupon(CouponIssueMessage message) {

        LocalDateTime issuedAt = message.issuedAt();

        couponRepository.save(Coupon.builder()
                .code(message.code())
                .validFrom(issuedAt)
                .validUntil(issuedAt.plusMonths(COUPON_VALID_PERIOD))
                .userId(message.userId())
                .issuedAt(issuedAt)
                .eventId(message.eventId())
                .build());

        if (eventRepository.decreaseRemainingQuantity(message.eventId()) == 0) {
            throw new CustomRuntimeException(ErrorCode.COUPON_OUT_OF_STOCK);
        }
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyIssued(Long eventId, Long userId) {
        return couponRepository.findByEventIdAndUserId(eventId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public CouponGetResponse getCoupon(Long couponId) {

        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomRuntimeException(ErrorCode.COUPON_NOT_FOUND))
                .toCouponGetResponse();
    }

    @Transactional(readOnly = true)
    public CouponGetResponse getCoupon(Long eventId, Long userId) {

        return couponRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new CustomRuntimeException(ErrorCode.COUPON_NOT_FOUND))
                .toCouponGetResponse();
    }
}
