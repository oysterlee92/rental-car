package com.example.rental.reservation.application.service;

import com.example.rental.reservation.domain.repository.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만료된 선점을 예약 객체의 상태 변경을 통해 해제한다. */
@Service
public class ReservationExpiryService {
    private final ReservationRepository reservations;
    private final Clock clock;

    /**
     * 예약 저장소와 설정한 시간대의 시계를 받는다.
     *
     * @param reservations 예약 저장소
     * @param clock 만료 판단에 사용할 시계
     */
    public ReservationExpiryService(ReservationRepository reservations, Clock clock) {
        this.reservations = reservations;
        this.clock = clock;
    }

    /** 만료 대상을 한 번에 최대 100건 처리한다. */
    @Transactional
    public void expireBatch() {
        var now = LocalDateTime.now(clock);
        reservations.findDueForUpdate(now, 100)
                .forEach(reservation -> reservation.expireIfDue(now));
    }

    /**
     * 새 선점을 시도할 차량의 만료된 선점을 해제한다.
     *
     * @param vehicleId 새로 선점할 차량 ID
     */
    @Transactional
    public void expireVehicle(long vehicleId) {
        var now = LocalDateTime.now(clock);
        reservations.findDueForVehicleForUpdate(vehicleId, now)
                .forEach(reservation -> reservation.expireIfDue(now));
    }
}