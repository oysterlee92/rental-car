package com.example.rental.payment.infrastructure.repository.jpa;

import com.example.rental.payment.domain.entity.Payment;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 결제 테이블의 원자적 삽입과 잠금 조회를 수행한다. */
public interface JpaPaymentRepository extends JpaRepository<Payment, UUID> {
    /**
     * 같은 예약 ID의 결제가 없다면 처리 중 상태로 추가한다.
     * @param id 예약 ID
     * @param amount 결제 금액
     * @param expiresAt 승인 기한
     */
    @Modifying
    @Query(value = """
            INSERT INTO payment (reservation_id, amount, expires_at, status)
            VALUES (:id, :amount, :expiresAt, 'PENDING')
            ON CONFLICT (reservation_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("amount") long amount, @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 최종 결과 변경을 직렬화하도록 해당 행을 잠근다.
     * @param id 예약 ID
     * @return 기존 결제
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findOneByReservationId(UUID id);
}