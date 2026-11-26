package com.example.rental.payment.infrastructure.repository.adapter;

import com.example.rental.payment.domain.entity.Payment;
import com.example.rental.payment.domain.repository.PaymentRepository;
import com.example.rental.payment.infrastructure.repository.jpa.JpaPaymentRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 결제 저장소 호출을 JPA로 전달한다. */
@Repository
public class PaymentRepositoryAdapter implements PaymentRepository {
    private final JpaPaymentRepository jpa;

    /** @param jpa 실제 DB 호출을 담당하는 저장소 */
    public PaymentRepositoryAdapter(JpaPaymentRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * 결제가 없으면 새 행을 추가하고, 이미 있으면 기존 행을 잠금 조회한다.
     * 금액과 승인 기한의 일치 여부는 반환 후 PaymentTransactions에서 검사한다.
     *
     * @param id 결제 중복 방지 키로 사용하는 예약 ID
     * @param amount 최초 저장할 원 단위 결제 금액
     * @param expiresAt 최초 저장할 승인 기한
     * @return 새로 추가했거나 이미 존재하는 잠금 상태의 결제
     * @throws IllegalStateException 결제 행을 찾을 수 없는 경우
     */
    @Override
    public Payment open(UUID id, long amount, LocalDateTime expiresAt) {
        jpa.insertIfAbsent(id, amount, expiresAt);
        return findForUpdate(id);
    }

    /**
     * 예약 ID로 결제 행을 조회하고 호출한 트랜잭션이 끝날 때까지 쓰기 잠금을 유지한다.
     *
     * @param id 조회할 결제의 예약 ID
     * @return 잠금 조회한 결제
     * @throws IllegalStateException 해당 예약 ID의 결제가 없는 경우
     */
    @Override
    public Payment findForUpdate(UUID id) {
        return jpa.findOneByReservationId(id)
                .orElseThrow(() -> new IllegalStateException("결제 요청을 찾을 수 없습니다."));
    }
}