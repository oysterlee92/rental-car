package com.example.rental.reservation.application.transaction;

import com.example.rental.reservation.application.dto.payment.CheckoutCompletion;
import com.example.rental.reservation.application.dto.payment.PaymentResult;
import com.example.rental.reservation.application.dto.reservation.ReservationResponse;
import com.example.rental.reservation.application.exception.ReservationNotFoundException;
import com.example.rental.reservation.domain.repository.ReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제 시작과 완료의 DB 트랜잭션을 제공한다. */
@Service
public class CheckoutTransactions {
    private final ReservationRepository reservations;
    private final Clock clock;

    /**
     * 예약 저장소와 시계를 받는다.
     * @param reservations 예약 저장소
     * @param clock 현재 시각을 제공하는 시계
     */
    public CheckoutTransactions(ReservationRepository reservations, Clock clock) {
        this.reservations = reservations;
        this.clock = clock;
    }

    /**
     * 예약을 잠깐 잠그고 결제 중 상태를 커밋한다.
     * @param id 예약 ID
     * @return 변경한 예약의 고정된 결제 조건
     */
    @Transactional
    public ReservationResponse begin(UUID id) {
        var reservation = reservations.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        // 만료된 선점은 EXPIRED로 커밋하고 호출한 서비스에서 결제를 거부한다.
        reservation.startPayment(LocalDateTime.now(clock));
        return ReservationResponse.from(reservation);
    }

    /**
     * 저장된 최종 결제 결과를 예약에 한 번만 반영한다.
     * @param result 결제 결과
     * @return 예약 상태와 새 확정 여부
     */
    @Transactional
    public CheckoutCompletion complete(PaymentResult result) {
        var reservation = reservations.findByIdForUpdate(result.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(result.reservationId()));
        boolean confirmed = reservation.completePayment("SUCCEEDED".equals(result.status()));
        var elapsed = Duration.between(reservation.getCreatedAt(), LocalDateTime.now(clock));
        return new CheckoutCompletion(ReservationResponse.from(reservation), confirmed, elapsed);
    }

    /**
     * HTTP 호출 전에 재확인 대상 ID만 읽고 연결을 반환한다.
     * @return 결제 중인 예약 최대 10건
     */
    @Transactional(readOnly = true)
    public List<UUID> pendingIds() {
        return reservations.findPendingIds(10);
    }
}