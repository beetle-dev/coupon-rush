package com.couponrush.coupon.repository;

import com.couponrush.coupon.domain.Coupon;
import com.couponrush.support.TestContainer;
import com.couponrush.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
class CouponRepositoryTest extends TestContainer {

    @Autowired
    CouponRepository couponRepository;

    @Test
    void Coupon을_생성하면_자동으로_id_생성된다() {

        Coupon coupon = TestFixtures.createNoIdCoupon(1L);

        Long couponId = couponRepository.save(coupon).getId();

        assertThat(couponId).isNotNull();
    }
}