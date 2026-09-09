package com.couponrush.support;

import com.couponrush.event.domain.Event;
import com.couponrush.event.dto.EventCreateRequest;
import com.couponrush.event.dto.EventResponse;
import com.couponrush.coupon.domain.Coupon;

import java.time.LocalDateTime;
import java.util.UUID;

public final class TestFixtures {

    private static final String EVENT_NAME = "이벤트명";
    private static final int TOTAL_QUANTITY = 50;
    private static final int REMAINING_QUANTITY = 50;

    public static EventCreateRequest createEventCreateRequest() {

        LocalDateTime now = LocalDateTime.now();
        return new EventCreateRequest(
                EVENT_NAME,
                TOTAL_QUANTITY,
                now,
                now.plusMonths(1)
        );
    }

    public static Event createEvent() {

        LocalDateTime now = LocalDateTime.now();
        return Event.builder()
                .id(1L)
                .name(EVENT_NAME)
                .totalQuantity(TOTAL_QUANTITY)
                .remainingQuantity(REMAINING_QUANTITY)
                .issueStartAt(now)
                .issueEndAt(now.plusMonths(1))
                .build();
    }

    public static Event createNoIDEvent() {

        LocalDateTime now = LocalDateTime.now();
        return Event.builder()
                .name(EVENT_NAME)
                .totalQuantity(TOTAL_QUANTITY)
                .remainingQuantity(REMAINING_QUANTITY)
                .issueStartAt(now)
                .issueEndAt(now.plusMonths(1))
                .build();
    }

    public static EventResponse createEventResponse() {

        LocalDateTime now = LocalDateTime.now();
        return new EventResponse(
                1L,
                EVENT_NAME,
                TOTAL_QUANTITY,
                REMAINING_QUANTITY,
                now,
                now.plusMonths(1)
        );
    }

    public static Coupon createCoupon(Long eventId) {

        LocalDateTime now = LocalDateTime.now();
        return Coupon.builder()
                .id(2L)
                .code(UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase())
                .validFrom(now)
                .validUntil(now.plusMonths(6))
                .userId(99L)
                .issuedAt(now)
                .eventId(eventId)
                .build();
    }

    public static Coupon createNoIdCoupon(Long eventId) {

        LocalDateTime now = LocalDateTime.now();
        return Coupon.builder()
                .code(UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase())
                .validFrom(now)
                .validUntil(now.plusMonths(6))
                .userId(99L)
                .issuedAt(now)
                .eventId(eventId)
                .build();
    }
}
