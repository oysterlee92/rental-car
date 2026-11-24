package com.example.rental.reservation.application.exception;

/** 선점할 차량이 등록되어 있지 않을 때 발생한다. */
public class VehicleNotFoundException extends RuntimeException {
    /**
     * 조회한 차량 ID로 오류 메시지를 만든다.
     *
     * @param id 조회한 차량 ID
     */
    public VehicleNotFoundException(long id) {
        super("차량을 찾을 수 없습니다. 차량 ID=" + id);
    }
}