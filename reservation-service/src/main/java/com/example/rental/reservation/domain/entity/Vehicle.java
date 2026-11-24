package com.example.rental.reservation.domain.entity;

import com.example.rental.reservation.domain.enums.VehicleStatus;
import com.example.rental.reservation.domain.exception.ReservationConflictException;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 지점에 소속된 차량의 기본 정보와 운영 상태를 저장하는 JPA 엔티티다.
 */
@Entity
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 차량이 속한 지점의 ID만 저장한다. 차량을 읽을 때 지점 객체를 함께 가져오지 않는다.
    private Long branchId;
    private String name;
    private int seats;
    // 원 단위 금액을 정수로 저장한다.
    private long dailyPrice;
    // DB에는 순서 번호 대신 AVAILABLE, MAINTENANCE라는 이름을 저장한다.
    @Enumerated(EnumType.STRING)
    private VehicleStatus status;

    // JPA가 DB에서 읽은 값으로 객체를 만들 때 사용하는 생성자다.
    protected Vehicle() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSeats() {
        return seats;
    }

    public long getDailyPrice() {
        return dailyPrice;
    }

    /**
     * 선택한 지점에 소속된 차량인지 확인한다.
     *
     * @param branchId 선택한 지점
     */
    public void requireAtBranch(BranchId branchId) {
        if (this.branchId != branchId.value()) {
            throw new IllegalArgumentException("선택한 지점의 차량이 아닙니다.");
        }
    }

    /**
     * 운영 가능한 차량인지 확인하고 예약 요금을 계산한다.
     *
     * @param period 대여 기간
     * @return 원 단위 예약 요금
     * @throws ReservationConflictException 정비 중인 차량인 경우
     */
    public long quote(RentalPeriod period) {
        if (status != VehicleStatus.AVAILABLE) {
            throw new ReservationConflictException("현재 예약할 수 없는 차량입니다.");
        }
        try {
            return Math.multiplyExact(dailyPrice, period.billableDays());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("대여 기간이 요금 계산 범위를 초과했습니다.");
        }
    }
}