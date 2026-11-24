package com.example.rental.reservation.application.exception;

import java.util.UUID;

/** 조회할 예약이 없을 때 발생한다. */
public class ReservationNotFoundException extends RuntimeException {
    /**
     * 조회한 예약 ID로 오류 메시지를 만든다.
     *
     * @param id 조회한 예약 ID
     */
    public ReservationNotFoundException(UUID id) {
        super("예약을 찾을 수 없습니다. 예약 ID=" + id);
    }
}