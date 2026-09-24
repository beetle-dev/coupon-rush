package com.couponrush.common.config;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.coupon.message.CouponIssueMessage;
import com.couponrush.coupon.message.CouponIssueMessageProducer;
import com.couponrush.coupon.service.CouponStockReservationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    private static final long RETRY_INTERVAL_MILLIS = 500L;
    private static final long MAX_RETRIES = 2L;

    @Bean
    public NewTopic couponIssueTopic() {
        return TopicBuilder.name(CouponIssueMessageProducer.TOPIC)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic couponIssueDltTopic() {
        return TopicBuilder.name(CouponIssueMessageProducer.DLT_TOPIC)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, CouponIssueMessage> kafkaTemplate,
                                                 CouponStockReservationService couponStockReservationService) {

        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ConsumerRecordRecoverer recoverer = (record, exception) -> {

            if (record.value() instanceof CouponIssueMessage message) {
                log.error("쿠폰 영속화 최종 실패. DLT로 보내고 재고를 반환한다. eventId={}, userId={}, code={}",
                        message.eventId(), message.userId(), message.code(), exception);
                couponStockReservationService.release(message.eventId(), message.userId(), exception);
            } else {
                log.error("역직렬화 불가 메시지. 재고 반환 없이 DLT로만 보낸다. topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset(), exception);
            }

            deadLetterRecoverer.accept(record, exception);
        };

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES));
        errorHandler.addNotRetryableExceptions(CustomRuntimeException.class);

        return errorHandler;
    }
}
