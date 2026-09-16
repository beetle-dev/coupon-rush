package com.couponrush.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    EVENT_INVALID_ISSUE_PERIOD(HttpStatus.BAD_REQUEST, "EVENT_400_01", "이벤트 발급 시작 일시가 발급 종료 일시보다 이전이어야 합니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT_404_01", "존재하지 않는 이벤트입니다."),

    COUPON_OUT_OF_STOCK(HttpStatus.CONFLICT, "COUPON_409_01", "쿠폰 재고가 없습니다."),
    COUPON_DUPLICATE(HttpStatus.CONFLICT, "COUPON_409_02", "이미 발급받은 쿠폰입니다"),
    COUPON_ISSUE_CONFLICT(HttpStatus.CONFLICT, "COUPON_409_03", "요청이 많아 쿠폰 발급에 실패했습니다. 잠시 후 다시 시도해주세요."),
    COUPON_INVALID_ISSUE_PERIOD(HttpStatus.BAD_REQUEST, "COUPON_400_01", "현재 쿠폰 발급이 가능한 이벤트가 아닙니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_404_01", "존재하지 않는 쿠폰입니다."),

    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400_01", "잘못된 요청입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
