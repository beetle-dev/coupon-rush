package com.couponrush.coupon.message;

import com.couponrush.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueMessageConsumer {

    private final CouponService couponService;

    @KafkaListener(topics = CouponIssueMessageProducer.TOPIC)
    public void consume(CouponIssueMessage message) {

        try {
            couponService.persistIssuedCoupon(message);

        } catch (DataIntegrityViolationException e) {

            if (couponService.isAlreadyIssued(message.eventId(), message.userId())) {
                log.info("이미 반영된 발급 메시지라 건너뛴다. eventId={}, userId={}",
                        message.eventId(), message.userId());
                return;
            }

            throw e;
        }
    }
}
