package com.example.rental.reservation.infrastructure.client;

import com.example.rental.reservation.application.dto.payment.PaymentCommand;
import com.example.rental.reservation.application.dto.payment.PaymentResult;
import com.example.rental.reservation.application.exception.PaymentUnavailableException;
import com.example.rental.reservation.application.port.PaymentGateway;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Payment HTTP 호출과 대기 시간 제한을 담당한다. */
@Component
public class PaymentClient implements PaymentGateway {
    private final RestClient client;

    /**
     * 추적 정보가 전파되는 HTTP 클라이언트를 만든다.
     * @param builder 자동 설정된 클라이언트 빌더
     * @param baseUrl Payment 주소
     */
    public PaymentClient(RestClient.Builder builder, @Value("${payment.base-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * Payment API에 결제를 요청하고 응답의 예약 ID·금액·기한·최종 상태를 검사한다.
     *
     * @param command 예약에 저장한 결제 조건
     * @return 검증한 최종 결제 결과
     * @throws PaymentUnavailableException 통신에 실패하거나 응답 조건·상태가 잘못된 경우
     */
    @Override
    public PaymentResult pay(PaymentCommand command) {
        try {
            var result = client.post().uri("/api/payments").body(command)
                    .retrieve().body(PaymentResult.class);
            // 잘못된 응답을 승인으로 해석하지 않고 재확인 대상으로 남긴다.
            if (result == null || !command.reservationId().equals(result.reservationId())
                    || command.amount() != result.amount() || !command.expiresAt().equals(result.expiresAt())
                    || !("SUCCEEDED".equals(result.status()) || "FAILED".equals(result.status()))) {
                throw new IllegalStateException("결제 응답의 예약 조건이나 상태를 확인해 주세요.");
            }
            return result;
        } catch (RestClientException | IllegalStateException exception) {
            throw new PaymentUnavailableException(exception);
        }
    }
}