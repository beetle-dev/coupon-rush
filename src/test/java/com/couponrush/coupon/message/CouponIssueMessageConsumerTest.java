package com.couponrush.coupon.message;

import com.couponrush.coupon.repository.CouponRepository;
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

import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class CouponIssueMessageConsumerTest extends TestContainer {

    @Autowired
    CouponIssueMessageConsumer couponIssueMessageConsumer;

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
    void 같은_메시지를_두_번_소비해도_쿠폰은_한_장_재고는_한_번만_차감된다() {

        Long eventId = eventService.createEvent(TestFixtures.createEventCreateRequest()).id();
        int totalQuantity = eventRepository.findById(eventId).orElseThrow().getTotalQuantity();

        CouponIssueMessage message = CouponIssueMessage.of(eventId, 42L, "DUPLICATE123", Instant.now());

        couponIssueMessageConsumer.consume(message);
        couponIssueMessageConsumer.consume(message);

        Event updatedEvent = eventRepository.findById(eventId).orElseThrow();

        assertThat(couponRepository.countByEventId(eventId)).isEqualTo(1);
        assertThat(updatedEvent.getRemainingQuantity()).isEqualTo(totalQuantity - 1);
    }
}
