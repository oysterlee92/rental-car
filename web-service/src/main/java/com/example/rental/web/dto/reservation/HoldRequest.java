package com.example.rental.web.dto.reservation;

import java.time.LocalDateTime;

/**
 * Reservation API에 전송할 선점 입력이다.
 *
 * @param branchId 지점
 * @param vehicleId 차량
 * @param pickupAt 인수 시각
 * @param returnAt 반납 시각
 */
public record HoldRequest(
        Long branchId,
        Long vehicleId,
        LocalDateTime pickupAt,
        LocalDateTime returnAt
) {
}