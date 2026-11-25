package com.example.rental.payment.domain.enums;



/** 결제의 처리 상태다. */
public enum PaymentStatus {
    /** 결과가 아직 정해지지 않았다. */
    PENDING,
    /** 모의 승인이 완료됐다. */
    SUCCEEDED,
    /** 거절됐거나 승인 기한이 지났다. */
    FAILED
}