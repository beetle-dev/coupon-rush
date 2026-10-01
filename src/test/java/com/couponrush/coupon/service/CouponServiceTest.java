package com.couponrush.coupon.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.message.CouponIssueMessage;
import com.couponrush.event.service.EventService;
import com.couponrush.coupon.domain.Coupon;
import com.couponrush.coupon.repository.CouponRepository;
import com.couponrush.event.repository.EventRepository;
import com.couponrush.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.sql.SQLException;

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

    @Mock
    CouponCodeGenerator couponCodeGenerator;

    @InjectMocks
    CouponService couponService;

    @Test
    void 사용자_중복_제약만_중복_발급으로_변환한다() {
        DataIntegrityViolationException failure = integrityViolation("UQ_event_id_user_id");
        given(couponRepository.save(any(Coupon.class))).willThrow(failure);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 2L))
                .isInstanceOf(CustomRuntimeException.class)
                .hasMessage(ErrorCode.COUPON_DUPLICATE.getMessage());
        assertThatThrownBy(() -> couponService.issueCouponAtomic(1L, 2L))
                .isInstanceOf(CustomRuntimeException.class)
                .hasMessage(ErrorCode.COUPON_DUPLICATE.getMessage());
    }

    @Test
    void 코드_충돌은_원래_예외를_전달한다() {
        DataIntegrityViolationException failure = integrityViolation("UQ_code");
        given(couponRepository.save(any(Coupon.class))).willThrow(failure);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 2L)).isSameAs(failure);
        assertThatThrownBy(() -> couponService.issueCouponAtomic(1L, 2L)).isSameAs(failure);
    }

    @Test
    void 제약_정보가_없는_무결성_오류는_원래_예외를_전달한다() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("integrity failure");
        given(couponRepository.save(any(Coupon.class))).willThrow(failure);

        assertThatThrownBy(() -> couponService.issueCoupon(1L, 2L)).isSameAs(failure);
        assertThatThrownBy(() -> couponService.issueCouponAtomic(1L, 2L)).isSameAs(failure);
    }

    private DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException("integrity failure",
                new ConstraintViolationException(
                        "constraint violation", new SQLException("duplicate key", "23000", 1062), constraintName));
    }

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

    @Test
    void 비동기_영속화는_메시지의_코드와_발급시각을_그대로_저장한다() {

        Instant issuedAt = Instant.now();
        CouponIssueMessage message = CouponIssueMessage.of(1L, 2L, "ABCDEF123456", issuedAt);

        given(eventRepository.decreaseRemainingQuantity(1L))
                .willReturn(1);

        couponService.persistIssuedCoupon(message);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        then(couponRepository).should().save(captor.capture());

        Coupon saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("ABCDEF123456");
        assertThat(saved.getUserId()).isEqualTo(2L);
        assertThat(saved.getEventId()).isEqualTo(1L);
        assertThat(saved.getIssuedAt()).isEqualTo(message.issuedAt());
        then(couponCodeGenerator).shouldHaveNoInteractions();
    }

    @Test
    void 비동기_영속화에서_재고_차감이_0행이면_예외() {

        CouponIssueMessage message = CouponIssueMessage.of(1L, 2L, "ABCDEF123456", Instant.now());

        given(eventRepository.decreaseRemainingQuantity(1L))
                .willReturn(0);

        assertThatThrownBy(() -> couponService.persistIssuedCoupon(message))
                .isInstanceOf(CustomRuntimeException.class)
                .hasMessage(ErrorCode.COUPON_OUT_OF_STOCK.getMessage());
    }
}
