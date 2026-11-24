package com.example.rental.reservation.application.dto.reservation;

import com.example.rental.reservation.domain.entity.Reservation;
import com.example.rental.reservation.domain.enums.ReservationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 예약 상세와 선점 응답에 사용하는 값이다.
 *
 * @param id 예약 ID
 * @param branchId 인수·반납 지점 ID
 * @param vehicleId 차량 ID
 * @param vehicleName 예약 시점의 차량 이름
 * @param pickupAt 인수 시각
 * @param returnAt 반납 시각
 * @param amount 확정한 원 단위 예약 요금
 * @param expiresAt 선점 만료 시각
 * @param status 현재 선점 상태
 */
public record ReservationResponse(
        UUID id,
        Long branchId,
        Long vehicleId,
        String vehicleName,
        LocalDateTime pickupAt,
        LocalDateTime returnAt,
        long amount,
        LocalDateTime expiresAt,
        ReservationStatus status
) {
    /**
     * 예약 객체에서 응답에 필요한 값을 읽는다.
     *
     * @param reservation 조회한 예약
     * @return 예약 응답
     */
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getBranchId(),
                reservation.getVehicleId(),
                reservation.getVehicleName(),
                reservation.getPickupAt(),
                reservation.getReturnAt(),
                reservation.getAmount(),
                reservation.getExpiresAt(),
                reservation.getStatus()
        );
    }
}