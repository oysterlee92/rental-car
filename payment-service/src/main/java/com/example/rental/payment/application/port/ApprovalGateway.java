package com.example.rental.payment.application.port;



/** 외부 결제 승인 대기를 표현하는 인터페이스다. */
public interface ApprovalGateway {
    /**
     * 지연 후 모의 승인 여부를 반환한다.
     * @return 승인 시 true, 거절 시 false
     */
    boolean approve();
}