package com.example.rental.reservation.infrastructure.repository.adapter;

import com.example.rental.reservation.domain.entity.Vehicle;
import com.example.rental.reservation.domain.repository.VehicleRepository;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import com.example.rental.reservation.infrastructure.repository.jpa.JpaVehicleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 차량 조회 요청을 Spring Data JPA에 전달한다. */
@Repository
public class VehicleRepositoryAdapter implements VehicleRepository {
    private final JpaVehicleRepository jpa;

    /**
     * JPA 차량 저장소를 받는다.
     *
     * @param jpa 차량 저장소
     */
    public VehicleRepositoryAdapter(JpaVehicleRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * 입력 기간에 유효한 선점이 없는 차량을 조회한다.
     *
     * @param branchId 지점
     * @param period 대여 기간
     * @param now 현재 시각
     * @return 차량 목록
     */
    @Override
    public List<Vehicle> findAvailableByBranch(BranchId branchId, RentalPeriod period, LocalDateTime now) {
        return jpa.findAvailable(
                branchId.value(),
                period.pickupAt(),
                period.returnAt(),
                now
        );
    }

    /**
     * 차량 ID로 조회한다.
     *
     * @param vehicleId 차량 ID
     * @return 차량 정보
     */
    @Override
    public Optional<Vehicle> findById(long vehicleId) {
        return jpa.findById(vehicleId);
    }
}