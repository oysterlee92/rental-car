package com.example.rental.payment.infrastructure.simulator;

import com.example.rental.payment.application.port.ApprovalGateway;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 실제 과금 없이 결제 지연과 거절을 재현한다. */
@Component
public class MockApprovalGateway implements ApprovalGateway {
    private final long minMillis;
    private final long maxMillis;
    private final double failureRate;

    /**
     * 랜덤 대기 범위와 거절 비율을 검사한다.
     * @param minMillis 최소 지연 밀리초
     * @param maxMillis 최대 지연 밀리초
     * @param failureRate 거절 비율 0~1
     */
    public MockApprovalGateway(@Value("${payment.mock.min-millis}") long minMillis, @Value("${payment.mock.max-millis}") long maxMillis, @Value("${payment.mock.failure-rate}") double failureRate) {
        if (minMillis < 0 || maxMillis < minMillis || maxMillis > 30000
                || !Double.isFinite(failureRate) || failureRate < 0 || failureRate > 1) {
            throw new IllegalArgumentException("모의 결제 지연 범위와 실패 비율을 확인해 주세요.");
        }
        this.minMillis = minMillis;
        this.maxMillis = maxMillis;
        this.failureRate = failureRate;
    }

    /**
     * 설정 범위에서 랜덤으로 기다린 뒤 모의 승인 또는 거절을 결정한다.
     * DB 트랜잭션 안에서 호출하면 실행을 거부한다.
     *
     * @return 승인 시 true, 거절 시 false
     * @throws IllegalStateException 대기 중 스레드가 중단된 경우
     */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public boolean approve() {
        var random = ThreadLocalRandom.current();
        try {
            // DB 연결을 사용하지 않는 구간에서 요청 스레드만 기다리게 한다.
            Thread.sleep(random.nextLong(minMillis, maxMillis + 1));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("결제 처리가 중단되었습니다. 결과를 다시 확인해 주세요.", exception);
        }
        return random.nextDouble() >= failureRate;
    }
}