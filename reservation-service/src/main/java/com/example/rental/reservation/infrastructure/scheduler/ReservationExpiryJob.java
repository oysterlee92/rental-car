package com.example.rental.reservation.infrastructure.scheduler;

import com.example.rental.reservation.application.service.ReservationExpiryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 주기적으로 만료된 차량 선점을 정리한다. */
@Component
public class ReservationExpiryJob {
    private final ReservationExpiryService expiry;

    /**
     * 만료 상태 변경을 담당할 서비스를 받는다.
     *
     * @param expiry 선점 만료 서비스
     */
    public ReservationExpiryJob(ReservationExpiryService expiry) {
        this.expiry = expiry;
    }

    /** 이전 작업이 끝난 뒤 10초마다 다음 만료 대상을 처리한다. */
    @Scheduled(fixedDelay = 10000)
    public void expire() {
        expiry.expireBatch();
    }
}