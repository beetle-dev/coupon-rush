package com.couponrush.event.dto;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        int totalQuantity,
        int remainingQuantity,
        LocalDateTime issueStartAt,
        LocalDateTime issueEndAt) {
}
