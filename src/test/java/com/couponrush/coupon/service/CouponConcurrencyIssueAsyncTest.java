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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class CouponConcurrencyIssueAsyncTest extends TestContainer {

    private static final Duration PERSIST_TIMEOUT = Duration.ofSeconds(120);
    private static final long POLL_INTERVAL_MILLIS = 200L;

    @Autowired
    CouponIssueAsyncService couponIssueAsyncService;

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
    void 쿠폰_발급_Kafka_비동기_동시성_테스트() throws InterruptedException {

        int threadCount = 10000;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch count = new CountDownLatch(threadCount);
        AtomicInteger accepted = new AtomicInteger();

        Long eventId = eventService.createEvent(TestFixtures.createEventCreateRequest()).id();
        int totalQuantity = eventRepository.findById(eventId).orElseThrow().getTotalQuantity();

        for (long userId = 1; userId <= threadCount; userId++) {

            long currentUserId = userId;

            executorService.submit(() -> {
                try {
                    couponIssueAsyncService.issueCoupon(eventId, currentUserId);
                    accepted.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    count.countDown();
                }
            });
        }

        count.await();
        executorService.shutdown();

        assertThat(accepted.get()).isEqualTo(totalQuantity);
        assertThat(couponStockRedisRepository.getStock(eventId)).isZero();

        awaitPersisted(eventId, accepted.get());

        Event updatedEvent = eventRepository.findById(eventId).orElseThrow();
        List<Coupon> coupons = couponRepository.findAll().stream()
                .filter(coupon -> coupon.getEventId().equals(eventId))
                .collect(Collectors.toList());

        System.out.println("접수(202) 수 = " + accepted.get());
        System.out.println("최종 remainingQuantity = " + updatedEvent.getRemainingQuantity());
        System.out.println("실제 발급된 쿠폰 수 = " + coupons.size());
        System.out.println("Redis 잔여 재고 = " + couponStockRedisRepository.getStock(eventId));

        assertThat(coupons.size()).isEqualTo(accepted.get());
        assertThat(coupons.size()).isEqualTo(updatedEvent.getTotalQuantity());
        assertThat(updatedEvent.getRemainingQuantity()).isZero();
        assertThat(coupons.stream().map(Coupon::getUserId).distinct().count())
                .isEqualTo(coupons.size());
        assertThat(coupons.stream().map(Coupon::getCode).distinct().count())
                .isEqualTo(coupons.size());
        assertThat(couponStockRedisRepository.getStock(eventId))
                .isEqualTo(updatedEvent.getRemainingQuantity());
    }

    @Test
    void 같은_사용자가_동시에_요청해도_한_장만_접수되고_한_장만_저장된다() throws InterruptedException {

        int threadCount = 1000;
        long sameUserId = 777L;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch count = new CountDownLatch(threadCount);
        AtomicInteger accepted = new AtomicInteger();

        Long eventId = eventService.createEvent(TestFixtures.createEventCreateRequest()).id();

        for (int i = 0; i < threadCount; i++) {

            executorService.submit(() -> {
                try {
                    couponIssueAsyncService.issueCoupon(eventId, sameUserId);
                    accepted.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    count.countDown();
                }
            });
        }

        count.await();
        executorService.shutdown();

        assertThat(accepted.get()).isEqualTo(1);

        awaitPersisted(eventId, 1);

        Event updatedEvent = eventRepository.findById(eventId).orElseThrow();

        assertThat(couponRepository.countByEventId(eventId)).isEqualTo(1);
        assertThat(updatedEvent.getRemainingQuantity()).isEqualTo(updatedEvent.getTotalQuantity() - 1);
        assertThat(couponStockRedisRepository.getStock(eventId))
                .isEqualTo(updatedEvent.getRemainingQuantity());
    }

    private void awaitPersisted(Long eventId, long expectedCount) throws InterruptedException {

        long deadline = System.nanoTime() + PERSIST_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {

            if (couponRepository.countByEventId(eventId) >= expectedCount) {
                return;
            }

            Thread.sleep(POLL_INTERVAL_MILLIS);
        }

        throw new AssertionError("제한 시간 안에 비동기 영속화가 끝나지 않았다. 기대=%d, 실제=%d"
                .formatted(expectedCount, couponRepository.countByEventId(eventId)));
    }
}
