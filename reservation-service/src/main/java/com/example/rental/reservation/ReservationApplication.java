package com.example.rental.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 차량 검색과 예약 기능을 담당하는 Reservation 앱을 시작하는 클래스다.
 */
@SpringBootApplication
public class ReservationApplication {
    /**
     * Reservation을 시작하고 DB 연결과 Flyway 마이그레이션을 초기화한다.
     *
     * @param args Spring Boot에 전달할 실행 인수
     */
    public static void main(String[] args) {
        SpringApplication.run(ReservationApplication.class, args);
    }
}