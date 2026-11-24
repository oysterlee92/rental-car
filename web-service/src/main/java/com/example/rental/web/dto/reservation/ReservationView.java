package com.example.rental.web.dto.reservation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 예약 상세와 화면 표시에 사용할 응답이다.
 *
 * @param id 예약 ID
 * @param branchId 지점 ID
 * @param vehicleId 차량 ID
 * @param vehicleName 차량 이름
 * @param pickupAt 인수 시각
 * @param returnAt 반납 시각
 * @param amount 원 단위 예약 요금
 * @param expiresAt 선점 만료 시각
 * @param status HELD 또는 EXPIRED
 */
public record ReservationView(
        UUID id,
        Long branchId,
        Long vehicleId,
        String vehicleName,
        LocalDateTime pickupAt,
        LocalDateTime returnAt,
        long amount,
        LocalDateTime expiresAt,
        String status
) {
    /**
     * 인수 시각을 지정한 형식으로 표시한다.
     *
     * @return 날짜와 시간
     */
    public String pickupAtText() {
        return displayTime(pickupAt);
    }

    /**
     * 반납 시각을 지정한 형식으로 표시한다.
     *
     * @return 날짜와 시간
     */
    public String returnAtText() {
        return displayTime(returnAt);
    }

    /**
     * 선점 만료 시각을 지정한 형식으로 표시한다.
     *
     * @return 날짜와 시간
     */
    public String expiresAtText() {
        return displayTime(expiresAt);
    }

    /**
     * API에서 받은 시각을 화면에 표시할 문자열로 만든다.
     *
     * @param value API 응답에서 받은 시각
     * @return 화면 표시용 문자열
     */
    private static String displayTime(LocalDateTime value) {
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}