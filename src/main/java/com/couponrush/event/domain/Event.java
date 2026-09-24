package com.couponrush.event.domain;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.event.dto.EventResponse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Event {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private int totalQuantity;

    private int remainingQuantity;

    @Column(nullable = false)
    private LocalDateTime issueStartAt;

    @Column(nullable = false)
    private LocalDateTime issueEndAt;

    public EventResponse toEventResponse() {

        return new EventResponse(
                this.id,
                this.name,
                this.totalQuantity,
                this.remainingQuantity,
                this.issueStartAt,
                this.issueEndAt
        );
    }

    public void decreaseRemainingQty() {

        if (this.remainingQuantity <= 0) {
            throw new CustomRuntimeException(ErrorCode.COUPON_OUT_OF_STOCK);
        }

        this.remainingQuantity--;
    }
}
