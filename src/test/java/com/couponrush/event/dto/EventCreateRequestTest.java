package com.couponrush.event.dto;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.event.domain.Event;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class EventCreateRequestTest {

    String EVENT_NAME = "테스트 이벤트";
    int TOTAL_QUANTITY = 100;

    @Test
    void 정상_요청은_Event로_변환된다() {

        LocalDateTime now = LocalDateTime.now();
        var request = new EventCreateRequest(
                EVENT_NAME,
                TOTAL_QUANTITY,
                now,
                now.plusMonths(1)
        );

        Event event = request.toEvent();

        assertThat(event.getName()).isEqualTo("테스트 이벤트");
        assertThat(event.getIssueStartAt()).isEqualTo(now);
    }

    @Test
    void 발급_시작_일시가_발급_종료_일시보다_이후이면_예외() {

        LocalDateTime now = LocalDateTime.now();
        var request = new EventCreateRequest(
                EVENT_NAME,
                TOTAL_QUANTITY,
                now,
                now.minusMonths(1)
        );

        assertThatThrownBy(request::toEvent)
                .isInstanceOf(CustomRuntimeException.class)
                .hasMessage(ErrorCode.EVENT_INVALID_ISSUE_PERIOD.getMessage());
    }
}