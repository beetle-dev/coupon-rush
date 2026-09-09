package com.couponrush.coupon.domain;

import com.couponrush.coupon.dto.CouponGetResponse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Coupon {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validUntil;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    @Column(nullable = false)
    private Long eventId;

    public CouponGetResponse toCouponGetResponse(){

        return new CouponGetResponse(
                this.getId(),
                this.getCode(),
                this.getValidFrom(),
                this.getValidUntil(),
                this.getUserId(),
                this.getIssuedAt(),
                this.getUsedAt(),
                this.getEventId()
        );
    }
}
