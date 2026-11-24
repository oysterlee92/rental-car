ALTER DATABASE reservation SET timezone TO 'Asia/Seoul';
SET TIME ZONE 'Asia/Seoul';

-- 차량 ID의 동등 비교와 시간 범위의 겹침 비교를 하나의 GiST 제약에서 사용한다.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservation (
    id UUID PRIMARY KEY,
    request_key UUID NOT NULL,
    branch_id BIGINT NOT NULL REFERENCES branch(id),
    vehicle_id BIGINT NOT NULL REFERENCES vehicle(id),
    vehicle_name VARCHAR(100) NOT NULL,
    pickup_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    return_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    amount BIGINT NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('HELD', 'EXPIRED')),
    version BIGINT NOT NULL,
    -- 인수는 포함하고 반납은 제외하는 범위를 DB에서 계산한다.
    during TSRANGE GENERATED ALWAYS AS (tsrange(pickup_at, return_at, '[)')) STORED,
    CONSTRAINT uk_reservation_request_key UNIQUE (request_key),
    CONSTRAINT ck_reservation_period CHECK (pickup_at < return_at),
    CONSTRAINT ck_reservation_expiry CHECK (created_at < expires_at),
    -- 상태가 HELD인 예약끼리 차량과 기간이 겹치면 INSERT를 거부한다.
    CONSTRAINT ex_reservation_vehicle_period
        EXCLUDE USING GIST (vehicle_id WITH =, during WITH &&)
        WHERE (status = 'HELD')
);

-- 주기적으로 만료 대상을 읽는 쿼리의 조건과 정렬에 사용한다.
CREATE INDEX idx_reservation_expiry
    ON reservation (expires_at, id) WHERE status = 'HELD';