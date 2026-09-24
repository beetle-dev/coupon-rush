package com.couponrush.coupon.service;

import com.couponrush.coupon.domain.Coupon;
import com.couponrush.coupon.repository.CouponRepository;
import com.couponrush.coupon.repository.CouponStockRedisRepository;
import com.couponrush.event.domain.Event;
import com.couponrush.event.repository.EventRepository;
import com.couponrush.event.service.EventService;
import com.couponrush.support.TestContainer;
import com.couponrush.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class CouponConcurrencyIssueAtomicTest extends TestContainer {

    @Autowired
    CouponIssueAtomicService couponIssueAtomicService;

    @Autowired
    CouponStockRedisRepository couponStockRedisRepository;

    @Autowired
    EventService eventService;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    RedissonClient redissonClient;

    @BeforeEach
    void flushRedis() {
        redissonClient.getKeys().flushall();
    }

    @Test
    void 쿠폰_발급_Redis_Lua_동시성_테스트() throws InterruptedException {

        int threadCount = 10000;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch count = new CountDownLatch(threadCount);

        Long eventId = eventService.createEvent(TestFixtures.createEventCreateRequest()).id();

        for (long userId = 1; userId <= threadCount; userId++) {

            long currentUserId = userId;

            executorService.submit(() -> {
                try {
                    couponIssueAtomicService.issueCoupon(eventId, currentUserId);
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

        System.out.println("최종 remainingQuantity = " + updatedEvent.getRemainingQuantity());
        System.out.println("실제 발급된 쿠폰 수 = " + coupons.size());
        System.out.println("Redis 잔여 재고 = " + couponStockRedisRepository.getStock(eventId));

        assertThat(updatedEvent.getRemainingQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(coupons.size()).isLessThanOrEqualTo(updatedEvent.getTotalQuantity());
        assertThat(coupons.size()).isEqualTo(updatedEvent.getTotalQuantity() - updatedEvent.getRemainingQuantity());

        assertThat(coupons.size()).isEqualTo(updatedEvent.getTotalQuantity());
        assertThat(updatedEvent.getRemainingQuantity()).isZero();
        assertThat(coupons.stream().map(Coupon::getUserId).distinct().count())
                .isEqualTo(coupons.size());
        assertThat(couponStockRedisRepository.getStock(eventId))
                .isEqualTo(updatedEvent.getRemainingQuantity());
    }

    @Test
    void 같은_사용자가_동시에_요청해도_한_장만_발급된다() throws InterruptedException {

        int threadCount = 1000;
        long sameUserId = 777L;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch count = new CountDownLatch(threadCount);

        Long eventId = eventService.createEvent(TestFixtures.createEventCreateRequest()).id();

        for (int i = 0; i < threadCount; i++) {

            executorService.submit(() -> {
                try {
                    couponIssueAtomicService.issueCoupon(eventId, sameUserId);
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

        assertThat(coupons.size()).isEqualTo(1);
        assertThat(updatedEvent.getRemainingQuantity()).isEqualTo(updatedEvent.getTotalQuantity() - 1);
        assertThat(couponStockRedisRepository.getStock(eventId))
                .isEqualTo(updatedEvent.getRemainingQuantity());
    }
}
