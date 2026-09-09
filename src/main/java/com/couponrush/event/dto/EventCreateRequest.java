package com.couponrush.event.dto;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.event.domain.Event;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventCreateRequest(
        @NotEmpty String name,
        @NotNull @Min(1) Integer totalQuantity,
        @NotNull LocalDateTime issueStartAt,
        @NotNull LocalDateTime issueEndAt) {

    public Event toEvent() {

        if (issueStartAt().isAfter(issueEndAt())){
            throw new CustomRuntimeException(ErrorCode.EVENT_INVALID_ISSUE_PERIOD);
        }

        return Event.builder()
                .name(name())
                .totalQuantity(totalQuantity())
                .remainingQuantity(totalQuantity())
                .issueStartAt(issueStartAt())
                .issueEndAt(issueEndAt())
                .build();
    }
}
