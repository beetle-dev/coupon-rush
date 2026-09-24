package com.couponrush.coupon.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.event.domain.Event;
import com.couponrush.event.service.EventService;
import com.couponrush.coupon.domain.Coupon;
import com.couponrush.coupon.repository.CouponRepository;
import com.couponrush.event.repository.EventRepository;
import com.couponrush.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    EventService eventService;

    @Mock
    CouponRepository couponRepository;

    @Mock
    EventRepository eventRepository;

    @InjectMocks
    CouponService couponService;

    @Test
    void Coupon_발급시_EventService에_재고_차감을_위임하고_CouponIssueResponse를_반환한다() {

        given(couponRepository.save(any(Coupon.class)))
                .willReturn(TestFixtures.createCoupon(1L));

        Long couponId = couponService.issueCoupon(1L, 2L).couponId();

        assertThat(couponId).isNotNull();
        then(eventService).should().decreaseRemainingQty(1L);
    }

    @Test
    void 재고_없으면_예외() {

        doThrow(new CustomRuntimeException(ErrorCode.COUPON_OUT_OF_STOCK))
                .when(eventService).decreaseRemainingQty(1L);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 2L))
                .isInstanceOf(CustomRuntimeException.class)
                .hasMessage(ErrorCode.COUPON_OUT_OF_STOCK.getMessage());
    }

    @Test
    void 원자_발급은_DB_조회_없이_재고를_차감한다() {

        given(couponRepository.save(any(Coupon.class)))
                .willReturn(TestFixtures.createCoupon(1L));
        given(eventRepository.decreaseRemainingQuantity(1L))
                .willReturn(1);

        Long couponId = couponService.issueCouponAtomic(1L, 2L).couponId();

        assertThat(couponId).isNotNull();
        then(eventService).shouldHaveNoInteractions();
    }

    @Test
    void 원자_발급에서_재고_차감이_0행이면_예외() {

        given(couponRepository.save(any(Coupon.class)))
                .willReturn(TestFixtures.createCoupon(1L));
        given(eventRepository.decreaseRemainingQuantity(1L))
                .willReturn(0);

        assertThatThrownBy(() -> couponService.issueCouponAtomic(1L, 2L))
                .isInstanceOf(CustomRuntimeException.class)
                .hasMessage(ErrorCode.COUPON_OUT_OF_STOCK.getMessage());
    }
}