package com.example.rental.reservation.application.exception;



/** 결제 응답을 확인하지 못해 재조회가 필요한 오류다. */
public class PaymentUnavailableException extends RuntimeException {
    /**
     * 원인을 유지하고 사용자 안내 메시지를 설정한다.
     * @param cause 통신 실패 또는 응답 오류
     */
    public PaymentUnavailableException(Throwable cause) {
        super("결제 결과를 확인하지 못했습니다. 예약 상세에서 다시 확인해 주세요.", cause);
    }
}