package com.example.rental.payment.domain.exception;



/** 같은 결제 키에 다른 조건을 사용한 오류다. */
public class PaymentConflictException extends RuntimeException {
    /** 조건 불일치 안내 메시지를 설정한다. */
    public PaymentConflictException() {
        super("같은 예약 ID로 다른 금액이나 승인 기한을 요청할 수 없습니다.");
    }
}