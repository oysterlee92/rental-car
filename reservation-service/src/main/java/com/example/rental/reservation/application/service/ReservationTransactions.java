package com.example.rental.reservation.application.transaction;

import com.example.rental.reservation.application.dto.reservation.HoldResult;
import com.example.rental.reservation.application.dto.reservation.ReservationResponse;
import com.example.rental.reservation.application.exception.ReservationNotFoundException;
import com.example.rental.reservation.application.exception.VehicleNotFoundException;
import com.example.rental.reservation.domain.entity.Reservation;
import com.example.rental.reservation.domain.repository.ReservationRepository;
import com.example.rental.reservation.domain.repository.VehicleRepository;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 예약의 생성과 재조회에 사용할 트랜잭션 경계를 제공한다. */
@Service
public class ReservationTransactions {
    private final ReservationRepository reservations;
    private final VehicleRepository vehicles;
    private final Clock clock;
    private final Duration holdDuration;

    /**
     * 저장소와 선점 유효 시간 설정을 받는다.
     *
     * @param reservations 예약 저장소
     * @param vehicles 차량 저장소
     * @param clock 설정한 시간대의 시계
     * @param holdDuration 설정 파일의 선점 유지 시간
     */
    public ReservationTransactions(
            ReservationRepository reservations,
            VehicleRepository vehicles,
            Clock clock,
            @Value("${reservation.hold-duration:PT5M}") Duration holdDuration
    ) {
        this.reservations = reservations;
        this.vehicles = vehicles;
        this.clock = clock;
        this.holdDuration = holdDuration;
    }

    /**
     * 같은 키의 기존 예약을 확인하고 없으면 새 예약을 저장한다.
     *
     * @param key 요청 키
     * @param branchId 인수·반납 지점
     * @param vehicleId 차량 ID
     * @param period 대여 기간
     * @return 예약 응답과 새 생성 여부
     */
    @Transactional
    public HoldResult hold(
            UUID key,
            BranchId branchId,
            long vehicleId,
            RentalPeriod period
    ) {
        var existing = replay(
                key,
                branchId,
                vehicleId,
                period
        );
        if (existing.isPresent()) {
            return new HoldResult(existing.get(), false);
        }
        var vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        var reservation = Reservation.hold(
                key,
                branchId,
                vehicle,
                period,
                LocalDateTime.now(clock),
                holdDuration
        );
        reservations.saveAndFlush(reservation);
        return new HoldResult(ReservationResponse.from(reservation), true);
    }

    /**
     * 요청 키에 해당하는 예약이 있으면 조건을 확인하고 현재 상태를 반환한다.
     *
     * @param key 요청 키
     * @param branchId 지점
     * @param vehicleId 차량 ID
     * @param period 대여 기간
     * @return 같은 키로 생성한 예약
     */
    @Transactional
    public Optional<ReservationResponse> replay(
            UUID key,
            BranchId branchId,
            long vehicleId,
            RentalPeriod period
    ) {
        return reservations.findByRequestKeyForUpdate(key).map(reservation -> {
            reservation.requireSameRequest(branchId, vehicleId, period);
            reservation.expireIfDue(LocalDateTime.now(clock));
            return ReservationResponse.from(reservation);
        });
    }

    /**
     * 예약을 조회하고 만료 기한이 지났다면 상태도 반영한다.
     *
     * @param id 예약 ID
     * @return 현재 예약 정보
     */
    @Transactional
    public ReservationResponse find(UUID id) {
        var reservation = reservations.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        reservation.expireIfDue(LocalDateTime.now(clock));
        return ReservationResponse.from(reservation);
    }

}