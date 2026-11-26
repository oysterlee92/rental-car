package com.example.rental.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 모의 결제 서비스를 시작한다. */
@SpringBootApplication
public class PaymentApplication {
    /** @param args 앱 실행 인수 */
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}