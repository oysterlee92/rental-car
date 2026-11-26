package com.example.rental.reservation.application.service;

import lombok.extern.slf4j.Slf4j;
import com.example.rental.reservation.application.transaction.CheckoutTransactions;
import com.example.rental.reservation.application.dto.payment.PaymentCommand;
import com.example.rental.reservation.application.dto.reservation.ReservationResponse;
import com.example.rental.reservation.application.port.PaymentGateway;
import com.example.rental.reservation.domain.enums.ReservationStatus;
import com.example.rental.reservation.domain.exception.ReservationConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 동기 결제 호출과 예약 확정의 순서를 관리한다. */
@Slf4j
@Service
public class CheckoutService {
    private final CheckoutTransactions transactions;
    private final PaymentGateway payments;
    private final ObservationRegistry observations;
    private final MeterRegistry meters;

    /**
     * DB 처리, 결제 호출과 관측 도구를 받는다.
     * @param transactions 예약 트랜잭션
     * @param payments 결제 호출 인터페이스
     * @param observations 추적과 요청 지연 기록
     * @param meters 예약 확정 지연 기록
     */
    public CheckoutService(
            CheckoutTransactions transactions,
            PaymentGateway payments,
            ObservationRegistry observations,
            MeterRegistry meters
    ) {
        this.transactions = transactions;
        this.payments = payments;
        this.observations = observations;
        this.meters = meters;
    }

    /**
     * 저장한 예약 금액으로 결제하고 최종 상태를 반환한다.
     * @param id 예약 ID이자 결제 요청 키
     * @return 확정 또는 결제 실패 상태의 예약
     * @throws ReservationConflictException 만료된 예약인 경우
     */
    @Transactional(propagation = Propagation.NEVER)
    public ReservationResponse pay(UUID id) {
        return observedPay(id, "http");
    }

    /**
     * 백그라운드 재확인 요청을 사용자 요청과 구분해 기록한다.
     * @param id 예약 ID
     * @return 재확인한 예약 상태
     */
    @Transactional(propagation = Propagation.NEVER)
    public ReservationResponse recover(UUID id) {
        return observedPay(id, "recovery");
    }

    /**
     * 호출 출처를 낮은 종류 수의 태그로 기록한다.
     * @param id 예약 ID
     * @param source http 또는 recovery
     * @return 현재 예약 상태
     */
    private ReservationResponse observedPay(UUID id, String source) {
        // 예약 ID는 로그와 추적에만 넣고 메트릭 태그에는 사용하지 않는다.
        try (var ignored = MDC.putCloseable("reservationId", id.toString())) {
            return Observation.createNotStarted("reservation.checkout", observations)
                    .lowCardinalityKeyValue("source", source)
                    .highCardinalityKeyValue("reservation.id", id.toString())
                    .observe(() -> execute(id));
        }
    }

    /**
     * DB 트랜잭션 사이에서 HTTP 결제를 기다린다.
     * @param id 예약 ID
     * @return 현재 예약 상태
     */
    private ReservationResponse execute(UUID id) {
        var reservation = transactions.begin(id);
        if (reservation.status() == ReservationStatus.EXPIRED) {
            throw new ReservationConflictException("선점이 만료되어 결제할 수 없습니다. 차량을 다시 검색해 주세요.");
        }
        if (reservation.status() != ReservationStatus.PAYMENT_PENDING) {
            return reservation;
        }
        log.info("결제를 요청했습니다.");
        var command = new PaymentCommand(id, reservation.amount(), reservation.expiresAt());
        var result = payments.pay(command);
        var completion = transactions.complete(result);
        if (completion.confirmedNow()) {
            // 트랜잭션이 커밋된 후에만 새 확정 한 건을 기록한다.
            Timer.builder("reservation.confirmation").register(meters).record(completion.elapsed());
        }
        log.info("결제 결과를 반영했습니다. 상태={}", completion.reservation().status());
        return completion.reservation();
    }
}