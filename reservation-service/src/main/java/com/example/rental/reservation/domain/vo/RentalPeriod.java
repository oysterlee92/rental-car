package com.example.rental.reservation.domain.vo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 인수부터 반납까지의 기간이다. 인수 시각은 포함하고 반납 시각은 제외한다.
 *
 * @param pickupAt 인수 시각
 * @param returnAt 반납 시각
 */
public record RentalPeriod(LocalDateTime pickupAt, LocalDateTime returnAt) {
    /**
     * 시각의 존재 여부와 대여 기간의 순서를 검사한다.
     *
     * @param pickupAt 인수 시각
     * @param returnAt 반납 시각
     * @throws IllegalArgumentException 시각이 없거나 반납이 인수보다 늦지 않은 경우
     */
    public RentalPeriod {
        // 시각이 빠졌거나 반납이 인수보다 빠르거나 같으면 대여 기간 생성을 거부한다.
        if (pickupAt == null || returnAt == null) {
            throw new IllegalArgumentException("인수·반납 시각을 입력해 주세요.");
        }
        // PostgreSQL과 같은 마이크로초 정밀도로 맞춰 재전송 조건을 비교한다.
        pickupAt = pickupAt.truncatedTo(ChronoUnit.MICROS);
        returnAt = returnAt.truncatedTo(ChronoUnit.MICROS);
        if (!returnAt.isAfter(pickupAt)) {
            throw new IllegalArgumentException("반납 시각은 인수 시각보다 늦어야 합니다.");
        }
    }

    /**
     * 대여 기간을 24시간 단위로 올림해 청구 일수를 구한다.
     *
     * @return 하루 미만도 하루로 계산한 일수
     */
    public long billableDays() {
        var duration = Duration.between(pickupAt, returnAt);
        long days = duration.toDays();
        return duration.minusDays(days).isZero() ? days : days + 1;
    }
}