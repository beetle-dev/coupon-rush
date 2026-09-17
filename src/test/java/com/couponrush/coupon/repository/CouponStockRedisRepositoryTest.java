package com.couponrush.coupon.repository;

import com.couponrush.support.TestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponStockRedisRepositoryTest extends TestContainer {

    private static final Long EVENT_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final int STOCK = 10;

    @Autowired
    CouponStockRedisRepository couponStockRedisRepository;

    @Autowired
    RedissonClient redissonClient;

    @BeforeEach
    void flushRedis() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("초기화 전에는 미초기화(-9)를 반환한다")
    void 초기화_전에는_미초기화를_반환한다() {

        long result = couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        assertThat(result).isEqualTo(CouponStockRedisRepository.ISSUE_NOT_INITIALIZED);
    }

    @Test
    @DisplayName("발급에 성공하면 재고가 1 줄고 발급자 Set에 등록된다")
    void 발급_성공() {

        initialize(STOCK);

        long result = couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        assertThat(result).isEqualTo(CouponStockRedisRepository.ISSUE_SUCCESS);
        assertThat(couponStockRedisRepository.getStock(EVENT_ID)).isEqualTo(STOCK - 1);
        assertThat(couponStockRedisRepository.countIssuedUsers(EVENT_ID)).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 사용자가 다시 요청하면 중복(-1)이고 재고는 그대로다")
    void 중복_발급() {

        initialize(STOCK);
        couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        long result = couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        assertThat(result).isEqualTo(CouponStockRedisRepository.ISSUE_DUPLICATE);
        assertThat(couponStockRedisRepository.getStock(EVENT_ID)).isEqualTo(STOCK - 1);
    }

    @Test
    @DisplayName("재고가 0이면 품절(-2)이고 재고가 음수로 내려가지 않는다")
    void 재고_소진() {

        initialize(0);

        long result = couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        assertThat(result).isEqualTo(CouponStockRedisRepository.ISSUE_OUT_OF_STOCK);
        assertThat(couponStockRedisRepository.getStock(EVENT_ID)).isZero();
    }

    @Test
    @DisplayName("이미 발급받은 사용자는 재고가 0이어도 품절이 아니라 중복으로 응답한다")
    void 재고가_0이어도_중복이_우선한다() {

        initialize(1);
        couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        long result = couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        assertThat(result).isEqualTo(CouponStockRedisRepository.ISSUE_DUPLICATE);
    }

    @Test
    @DisplayName("발급 기간 밖이면 기간 오류(-3)를 반환한다")
    void 발급_기간_밖() {

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        couponStockRedisRepository.initialize(EVENT_ID, STOCK, start, start.plusDays(1));

        long result = couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        assertThat(result).isEqualTo(CouponStockRedisRepository.ISSUE_INVALID_PERIOD);
        assertThat(couponStockRedisRepository.getStock(EVENT_ID)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("보상은 재고와 발급자 Set을 되돌리며, 두 번 호출해도 재고가 부풀지 않는다")
    void 보상_처리는_멱등하다() {

        initialize(STOCK);
        couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        couponStockRedisRepository.rollbackIssue(EVENT_ID, USER_ID);

        assertThat(couponStockRedisRepository.getStock(EVENT_ID)).isEqualTo(STOCK);
        assertThat(couponStockRedisRepository.countIssuedUsers(EVENT_ID)).isZero();

        couponStockRedisRepository.rollbackIssue(EVENT_ID, USER_ID);

        assertThat(couponStockRedisRepository.getStock(EVENT_ID)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("이미 초기화된 재고는 SET NX 때문에 덮어써지지 않는다")
    void 재초기화는_기존_재고를_덮어쓰지_않는다() {

        initialize(STOCK);
        couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        boolean initialized = couponStockRedisRepository.initStockIfAbsent(EVENT_ID, STOCK);

        assertThat(initialized).isFalse();
        assertThat(couponStockRedisRepository.getStock(EVENT_ID)).isEqualTo(STOCK - 1);
    }

    @Test
    @DisplayName("기존 발급 이력을 발급자 Set에 채우면 중복으로 걸러진다")
    void 발급_이력_복원() {

        initialize(STOCK);
        couponStockRedisRepository.addIssuedUsers(EVENT_ID, List.of(USER_ID));

        long result = couponStockRedisRepository.tryIssue(EVENT_ID, USER_ID, nowMillis());

        assertThat(result).isEqualTo(CouponStockRedisRepository.ISSUE_DUPLICATE);
    }

    private void initialize(int stock) {
        LocalDateTime now = LocalDateTime.now();
        couponStockRedisRepository.initialize(EVENT_ID, stock, now.minusDays(1), now.plusDays(1));
    }

    private long nowMillis() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
