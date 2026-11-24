package com.example.rental.reservation.domain.entity;

import com.example.rental.reservation.domain.enums.ReservationStatus;
import com.example.rental.reservation.domain.exception.ReservationConflictException;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 예약의 대여 기간, 차량 배정과 선점 상태를 함께 관리한다.
 * HELD 상태인 행의 기간 중복은 PostgreSQL 배제 제약이 검사한다.
 */
@Entity
public class Reservation {
    @Id
    private UUID id;
    private UUID requestKey;
    private Long branchId;
    private Long vehicleId;
    private String vehicleName;
    private LocalDateTime pickupAt;
    private LocalDateTime returnAt;
    private long amount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    // 다른 트랜잭션이 먼저 상태를 바꿨다면 덮어쓰지 않도록 버전을 확인한다.
    @Version
    private Long version;

    // JPA가 DB 조회 결과로 예약 객체를 만들 때 사용하는 생성자다.
    protected Reservation() {
    }

    /**
     * 검증한 차량과 기간으로 예약을 생성한다.
     *
     * @param requestKey 같은 요청의 재전송을 구분할 키
     * @param branchId 선택한 지점
     * @param vehicle 선점할 차량
     * @param period 대여 기간
     * @param now 생성 시각
     * @param holdDuration 선점을 유지할 시간
     * @return HELD 상태의 새 예약
     * @throws IllegalArgumentException 입력이나 선점 시간이 잘못된 경우
     */
    public static Reservation hold(
            UUID requestKey,
            BranchId branchId,
            Vehicle vehicle,
            RentalPeriod period,
            LocalDateTime now,
            Duration holdDuration
    ) {
        if (requestKey == null || holdDuration.isZero() || holdDuration.isNegative()) {
            throw new IllegalArgumentException("요청 키와 선점 유효 시간을 확인해 주세요.");
        }
        now = now.truncatedTo(ChronoUnit.MICROS);
        if (!period.pickupAt().isAfter(now)) {
            throw new IllegalArgumentException("인수 시각은 현재 시각보다 늦어야 합니다.");
        }
        vehicle.requireAtBranch(branchId);
        var reservation = new Reservation();
        reservation.id = UUID.randomUUID();
        reservation.requestKey = requestKey;
        reservation.branchId = branchId.value();
        reservation.vehicleId = vehicle.getId();
        reservation.vehicleName = vehicle.getName();
        reservation.pickupAt = period.pickupAt();
        reservation.returnAt = period.returnAt();
        reservation.amount = vehicle.quote(period);
        reservation.createdAt = now;
        reservation.expiresAt = now.plus(holdDuration).truncatedTo(ChronoUnit.MICROS);
        reservation.status = ReservationStatus.HELD;
        return reservation;
    }

    /**
     * 요청 키를 처음 사용했을 때와 같은 조건인지 확인한다.
     *
     * @param branchId 재전송한 지점
     * @param vehicleId 재전송한 차량 ID
     * @param period 재전송한 기간
     * @throws ReservationConflictException 같은 키로 다른 조건을 보낸 경우
     */
    public void requireSameRequest(BranchId branchId, long vehicleId, RentalPeriod period) {
        if (this.branchId != branchId.value() || this.vehicleId != vehicleId
                || !pickupAt.equals(period.pickupAt()) || !returnAt.equals(period.returnAt())) {
            throw new ReservationConflictException("같은 요청 키로 다른 예약 조건을 보낼 수 없습니다.");
        }
    }

    /**
     * 선점 기한에 도달했으면 만료 상태로 바꾼다.
     *
     * @param now 만료 여부를 판단할 현재 시각
     */
    public void expireIfDue(LocalDateTime now) {
        // 만료된 예약을 다시 처리해도 상태가 반복해서 바뀌지 않는다.
        if (status == ReservationStatus.HELD && !expiresAt.isAfter(now)) {
            status = ReservationStatus.EXPIRED;
        }
    }

    public UUID getId() {
        return id;
    }
    public Long getBranchId() {
        return branchId;
    }
    public Long getVehicleId() {
        return vehicleId;
    }
    public String getVehicleName() {
        return vehicleName;
    }
    public LocalDateTime getPickupAt() {
        return pickupAt;
    }
    public LocalDateTime getReturnAt() {
        return returnAt;
    }
    public long getAmount() {
        return amount;
    }
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    public ReservationStatus getStatus() {
        return status;
    }
}