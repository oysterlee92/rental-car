package com.example.rental.reservation.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import com.example.rental.reservation.application.service.CheckoutService;
import com.example.rental.reservation.application.transaction.CheckoutTransactions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 결제 결과 반영이 끝나지 않은 예약을 주기적으로 다시 확인한다. */
@Slf4j
@Component
public class PaymentRecoveryJob {
    private final CheckoutTransactions transactions;
    private final CheckoutService checkout;

    /**
     * 대상 조회와 결제 재처리를 담당할 빈을 받는다.
     * @param transactions 재확인 대상 조회
     * @param checkout 중복 요청을 처리하는 결제 서비스
     */
    public PaymentRecoveryJob(CheckoutTransactions transactions, CheckoutService checkout) {
        this.transactions = transactions;
        this.checkout = checkout;
    }

    /** 한 배치가 끝난 뒤 10초 후 최대 10건을 다시 처리한다. */
    @Scheduled(fixedDelayString = "${payment.recovery-delay:10000}")
    public void recover() {
        for (var id : transactions.pendingIds()) {
            try {
                checkout.recover(id);
            } catch (RuntimeException exception) {
                // 한 예약의 오류가 다음 예약의 재확인을 막지 않도록 계속 진행한다.
                log.atWarn().addKeyValue("reservationId", id)
                        .log("결제 결과를 다시 확인하지 못했습니다.", exception);
            }
        }
    }
}