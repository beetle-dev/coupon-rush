package com.couponrush.coupon.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueMessageProducer {

    public static final String TOPIC = "coupon-issue";
    public static final String DLT_TOPIC = TOPIC + ".DLT";

    private static final long SEND_TIMEOUT_SECONDS = 3L;

    private final KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

    public void  send(CouponIssueMessage message) {

        try {
            kafkaTemplate.send(TOPIC, message.partitionKey(), message)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaException("쿠폰 발급 메시지 전송 중 인터럽트. " + message, e);

        } catch (ExecutionException | TimeoutException e) {
            log.error("쿠폰 발급 메시지 전송 실패. eventId={}, userId={}", message.eventId(), message.userId(), e);
            throw new KafkaException("쿠폰 발급 메시지 전송 실패. " + message, e);
        }
    }
}
