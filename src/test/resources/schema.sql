-- docs/DDL.sql의 미러. 테이블 구조는 그쪽이 기준(source of truth)이다.
-- 다른 점은 DROP TABLE IF EXISTS 뿐이다.
-- MySQL 컨테이너를 JVM당 하나만 띄우므로(TestContainer) 스프링 컨텍스트가 새로 뜰 때마다
-- 이 스크립트가 다시 실행된다. 멱등하지 않으면 "Table already exists"로 컨텍스트 로딩이 실패한다.

DROP TABLE IF EXISTS coupon;

CREATE TABLE coupon
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'ID',
    code        VARCHAR(20) NOT NULL COMMENT '쿠폰 코드',
    valid_from  DATETIME    NOT NULL COMMENT '사용 가능 시작 일시',
    valid_until DATETIME    NOT NULL COMMENT '사용 가능 종료 일시',
    user_id     BIGINT      NOT NULL COMMENT 'USER ID',
    issued_at   DATETIME(6) NOT NULL COMMENT '발급 일시',
    used_at     DATETIME(6) NULL     COMMENT '사용 일시',
    event_id    BIGINT      NOT NULL COMMENT 'event ID',
    PRIMARY KEY (id)
) COMMENT '쿠폰';

CREATE UNIQUE INDEX UQ_code
    ON coupon (code);

CREATE UNIQUE INDEX UQ_event_id_user_id
    ON coupon (user_id, event_id);

DROP TABLE IF EXISTS event;

CREATE TABLE event
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    name               VARCHAR(30)  NOT NULL COMMENT '쿠폰 그룹명',
    total_quantity     INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '초기 수량',
    remaining_quantity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '재고 수량',
    issue_start_at     DATETIME     NOT NULL COMMENT '발급 시작 일시',
    issue_end_at       DATETIME     NOT NULL COMMENT '발급 종료 일시',
    version            BIGINT       NOT NULL DEFAULT 0 COMMENT '버전',
    PRIMARY KEY (id)
) COMMENT '이벤트'
