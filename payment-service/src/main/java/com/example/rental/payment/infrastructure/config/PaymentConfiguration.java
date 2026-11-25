package com.example.rental.payment.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 결제 서비스의 공통 빈을 제공한다. */
@Configuration
public class PaymentConfiguration {
    /** @return 승인 기한을 비교할 시계 */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}