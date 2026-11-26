-- 새 결제 DB와 현재 JDBC 연결의 시간대를 통일한다.
ALTER DATABASE payment SET timezone TO 'Asia/Seoul';
SET TIME ZONE 'Asia/Seoul';
CREATE TABLE payment (
    reservation_id UUID PRIMARY KEY,
    amount BIGINT NOT NULL CHECK (amount >= 0),
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    -- 최종 상태에는 완료 시각이 필요하고 처리 중에는 없어야 한다.
    CONSTRAINT ck_payment_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND completed_at IS NOT NULL)
    )
);