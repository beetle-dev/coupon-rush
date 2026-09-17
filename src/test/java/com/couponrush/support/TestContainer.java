package com.couponrush.support;

import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * 싱글톤 컨테이너 패턴. JVM당 한 번만 띄우고 종료는 Ryuk에 맡긴다.
 * <p>
 * {@code @Testcontainers} + {@code @Container}를 쓰면 테스트 클래스가 끝날 때마다 static 컨테이너가 stop된다.
 * 그런데 스프링 테스트 컨텍스트는 캐시되어 첫 컨테이너의 JDBC URL을 계속 들고 있으므로,
 * 두 번째 테스트 클래스부터 죽은 컨테이너에 붙어 커넥션 획득에 실패한다.
 * 컨테이너 기동 비용도 클래스마다 치르지 않게 된다.
 */
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)  // 자동 교체 끄기
public class TestContainer {

    private static final int REDIS_PORT = 6379;

    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    // GenericContainer는 testcontainers 코어에 포함돼 있어 의존성 추가가 필요 없다.
    // 이전까지 Redis 테스트가 로컬 6379에 의존하고 있어 환경에 따라 결과가 달라졌다.
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(REDIS_PORT);

    static {
        mysql.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }
}
