package com.couponrush.coupon.service;

import com.couponrush.coupon.domain.Coupon;
import com.couponrush.coupon.repository.CouponRepository;
import com.couponrush.event.domain.Event;
import com.couponrush.event.repository.EventRepository;
import com.couponrush.support.TestContainer;
import com.couponrush.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class CouponConcurrencyIssueLockTest extends TestContainer {

    @Autowired
    CouponIssueLockService couponIssueLockService;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    CouponRepository couponRepository;

    @Test
    void 쿠폰_발급_Redis_Lock_동시성_테스트() throws InterruptedException {

        int threadCount = 10000;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch count = new CountDownLatch(threadCount);

        Event event = eventRepository.save(TestFixtures.createNoIDEvent());
        Long eventId = event.getId();

        for (long userId = 1; userId <= threadCount; userId++) {

            long currentUserId = userId;

            executorService.submit(() -> {
                try {
                    couponIssueLockService.issueCoupon(eventId, currentUserId);
                } catch (Exception e) {
                } finally {
                    count.countDown();
                }
            });
        }

        count.await();
        executorService.shutdown();

        Event updatedEvent = eventRepository.findById(eventId).orElseThrow();
        List<Coupon> coupons = couponRepository.findAll().stream()
                .filter(coupon -> coupon.getEventId().equals(eventId))
                .collect(Collectors.toList());

        System.out.println("초기 remainingQuantity = " + event.getRemainingQuantity());
        System.out.println("최종 remainingQuantity = " + updatedEvent.getRemainingQuantity());
        System.out.println("실제 발급된 쿠폰 수 = " + coupons.size());

        assertThat(updatedEvent.getRemainingQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(coupons.size()).isLessThanOrEqualTo(updatedEvent.getTotalQuantity());
        assertThat(coupons.size()).isEqualTo(updatedEvent.getTotalQuantity() - updatedEvent.getRemainingQuantity());
    }
}
