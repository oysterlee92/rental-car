-- 예약에 결제 상태를 추가하고 모든 활성 배정에 기간 제약을 적용한다.
ALTER TABLE reservation DROP CONSTRAINT reservation_status_check;
ALTER TABLE reservation ADD CONSTRAINT reservation_status_check
    CHECK (status IN ('HELD', 'EXPIRED', 'PAYMENT_PENDING', 'CONFIRMED', 'PAYMENT_FAILED'));
ALTER TABLE reservation DROP CONSTRAINT ex_reservation_vehicle_period;
ALTER TABLE reservation ADD CONSTRAINT ex_reservation_vehicle_period
    EXCLUDE USING GIST (vehicle_id WITH =, during WITH &&)
    WHERE (status IN ('HELD', 'PAYMENT_PENDING', 'CONFIRMED'));
-- 결제 결과를 반영하지 못한 예약을 주기적으로 재처리한다.
CREATE INDEX idx_reservation_payment_pending ON reservation (created_at, id)
    WHERE status = 'PAYMENT_PENDING';