package com.example.rental.reservation.presentation.controller;

import com.example.rental.reservation.application.dto.reservation.HoldRequest;
import com.example.rental.reservation.application.dto.reservation.ReservationResponse;
import com.example.rental.reservation.application.service.ReservationService;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 차량 선점 요청과 예약 상세 조회를 HTTP API로 제공한다. */
@RestController
public class ReservationController {
    private final ReservationService reservations;

    /**
     * 예약 처리 서비스를 받는다.
     *
     * @param reservations 선점과 조회 서비스
     */
    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    /**
     * 차량을 선점하거나 같은 요청의 기존 예약을 반환한다.
     *
     * @param key 재전송 시에도 유지할 요청 키
     * @param request 지점, 차량, 대여 기간
     * @return 첫 생성은 201, 같은 요청의 재전송은 200
     */
    @PostMapping("/api/reservations")
    public ResponseEntity<ReservationResponse> hold(@RequestHeader("Idempotency-Key") UUID key, @RequestBody HoldRequest request) {
        var period = new RentalPeriod(request.pickupAt(), request.returnAt());
        var result = reservations.hold(
                key,
                new BranchId(request.branchId()),
                request.vehicleId(),
                period
        );
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/reservations/" + result.reservation().id()))
                    .body(result.reservation());
        }
        return ResponseEntity.ok(result.reservation());
    }

    /**
     * 예약의 현재 선점 상태를 조회한다.
     *
     * @param id 예약 ID
     * @return 예약 상세
     */
    @GetMapping("/api/reservations/{id}")
    public ReservationResponse find(@PathVariable UUID id) {
        return reservations.find(id);
    }

}