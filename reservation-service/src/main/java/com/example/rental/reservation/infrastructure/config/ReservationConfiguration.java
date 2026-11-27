package com.example.rental.reservation.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 선점 시각과 만료 작업에 필요한 설정이다. */
@Configuration
@EnableScheduling
public class ReservationConfiguration {
    /**
     * 서버의 기본 시간대와 관계없이 설정한 시간대의 시계를 제공한다.
     *
     * @return 선점 서비스에서 사용할 시계
     */
    @Bean
    public Clock reservationClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}