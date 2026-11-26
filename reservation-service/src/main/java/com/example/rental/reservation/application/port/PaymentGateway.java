package com.example.rental.reservation.application.port;

import com.example.rental.reservation.application.dto.payment.PaymentCommand;
import com.example.rental.reservation.application.dto.payment.PaymentResult;

/** 예약 서비스가 결제 결과를 요청하는 인터페이스다. */
public interface PaymentGateway {
    /**
     * 같은 예약 ID로 요청할 때 동일한 결제 결과를 반환한다.
     * @param command 예약에 저장된 결제 조건
     * @return 최종 결제 결과
     */
    PaymentResult pay(PaymentCommand command);
}