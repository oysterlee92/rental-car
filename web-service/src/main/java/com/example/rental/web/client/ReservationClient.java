package com.example.rental.web.client;

import com.example.rental.web.dto.reservation.HoldRequest;
import com.example.rental.web.dto.reservation.ReservationView;
import com.example.rental.web.dto.search.BranchView;
import com.example.rental.web.dto.search.SearchResult;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Web에서 Reservation API를 호출하는 HTTP 클라이언트다.
 * API의 JSON 응답을 Web에서 사용하는 record로 읽는다.
 */
@Component
public class ReservationClient {
    private final RestClient client;

    /**
     * API 주소와 대기 시간 제한을 적용한 클라이언트를 만든다.
     *
     * @param builder Spring Boot가 제공하는 RestClient 빌더
     * @param baseUrl 실행 환경에 맞는 Reservation 주소
     */
    public ReservationClient(RestClient.Builder builder, @Value("${reservation.base-url}") String baseUrl) {
        // 연결은 최대 2초, 응답 읽기는 최대 3초 동안 기다리도록 설정한다.
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * 검색 화면에 표시할 지점 목록을 가져온다.
     *
     * @return 지점 ID와 이름 목록
     */
    public List<BranchView> branches() {
        return client.get().uri("/api/branches").retrieve()
                .body(new ParameterizedTypeReference<List<BranchView>>() { });
    }

    /**
     * 시각을 그대로 전달하고 차량 검색 결과를 받는다.
     *
     * @param branchId 검색할 지점 ID
     * @param pickupAt 인수 시각
     * @param returnAt 반납 시각
     * @return API가 반환한 검색 조건과 차량 목록
     */
    public SearchResult search(Long branchId, LocalDateTime pickupAt, LocalDateTime returnAt) {
        // 검색값을 URI 변수에 넣는다. RestClient가 URL에 필요한 문자 변환을 처리한다.
        return client.get().uri(builder -> builder.path("/api/vehicles")
                        .queryParam("branchId", "{branchId}")
                        .queryParam("pickupAt", "{pickupAt}")
                        .queryParam("returnAt", "{returnAt}")
                        .build(branchId, pickupAt.toString(), returnAt.toString()))
                .retrieve().body(SearchResult.class);
    }

    /**
     * 같은 요청 키와 조건으로 차량 선점을 요청한다.
     *
     * @param key 재시도에도 유지할 요청 키
     * @param branchId 지점 ID
     * @param vehicleId 차량 ID
     * @param pickupAt 인수 시각
     * @param returnAt 반납 시각
     * @return 생성했거나 이미 생성된 예약
     */
    public ReservationView hold(
            UUID key,
            Long branchId,
            Long vehicleId,
            LocalDateTime pickupAt,
            LocalDateTime returnAt
    ) {
        return client.post().uri("/api/reservations")
                .header("Idempotency-Key", key.toString())
                .body(new HoldRequest(
                        branchId,
                        vehicleId,
                        pickupAt,
                        returnAt
                ))
                .retrieve().body(ReservationView.class);
    }

    /**
     * 현재 예약 상태를 조회한다.
     *
     * @param id 예약 ID
     * @return 예약 상세
     */
    public ReservationView findReservation(UUID id) {
        return client.get().uri("/api/reservations/{id}", id)
                .retrieve().body(ReservationView.class);
    }

}