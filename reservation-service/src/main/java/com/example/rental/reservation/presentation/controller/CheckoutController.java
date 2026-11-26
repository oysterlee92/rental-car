package com.example.rental.reservation.presentation.controller;

import com.example.rental.reservation.application.dto.reservation.ReservationResponse;
import com.example.rental.reservation.application.service.CheckoutService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 예약 결제 API를 제공한다. */
@RestController
public class CheckoutController {
    private final CheckoutService checkout;

    /** @param checkout 결제 흐름을 담당하는 서비스 */
    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    /**
     * 예약 ID로 결제를 요청하거나 기존 결과를 반환한다.
     * @param id 예약 ID
     * @return 결제 결과를 반영한 예약
     */
    @PostMapping("/api/reservations/{id}/payment")
    public ReservationResponse pay(@PathVariable UUID id) {
        return checkout.pay(id);
    }
}