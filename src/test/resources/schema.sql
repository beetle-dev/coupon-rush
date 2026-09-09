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
