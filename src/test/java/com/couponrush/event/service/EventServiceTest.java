package com.couponrush.event.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.repository.CouponStockRedisRepository;
import com.couponrush.event.domain.Event;
import com.couponrush.event.dto.EventCreateRequest;
import com.couponrush.event.dto.EventResponse;
import com.couponrush.event.repository.EventRepository;
import com.couponrush.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    EventRepository eventRepository;

    @Mock
    CouponStockRedisRepository couponStockRedisRepository;

    @InjectMocks
    EventService eventService;

    @Test
    void Event_생성시_저장된_id를_반환한다() {

        EventCreateRequest request = TestFixtures.createEventCreateRequest();
        Event event = request.toEvent();

        given(eventRepository.save(any(Event.class)))
                .willReturn(event);

        Long eventId = eventService.createEvent(request).id();
        assertThat(eventId).isEqualTo(event.getId());
    }

    @Test
    void id에_해당하는_Event를_반환한다() {

        Event event = TestFixtures.createEvent();

        given(eventRepository.findById(1L))
                .willReturn(Optional.ofNullable(event));

        EventResponse response = eventService.getEvent(1L);

        assertThat(response.name()).isEqualTo(event.getName());
        assertThat(response.remainingQuantity()).isEqualTo(event.getRemainingQuantity());
    }

    @Test
    void 존재하지_않는_Event_조회시_예외() {

        given(eventRepository.findById(any(Long.class)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(1L))
                .isInstanceOf(CustomRuntimeException.class)
                .hasMessage(ErrorCode.EVENT_NOT_FOUND.getMessage());
    }
}