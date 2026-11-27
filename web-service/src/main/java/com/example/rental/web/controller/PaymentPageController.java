package com.example.rental.web.controller;

import com.example.rental.web.client.ReservationClient;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 예약 상세 화면에서 모의 결제를 요청한다. */
@Controller
public class PaymentPageController {
    private final ReservationClient client;

    /** @param client 예약 API 클라이언트 */
    public PaymentPageController(ReservationClient client) {
        this.client = client;
    }

    /**
     * 결제 요청 후 GET 상세 화면으로 이동한다.
     * @param id 예약 ID
     * @param redirect 한 번 표시할 안내 메시지
     * @return 예약 상세 주소
     */
    @PostMapping("/reservations/{id}/payment")
    public String pay(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            // 정상 안내는 상세 조회에서 읽은 예약 상태로 표시한다.
            client.pay(id);
        } catch (RestClientException exception) {
            // 응답 실패를 결제 거절로 단정하지 않고 상세 상태를 다시 읽는다.
            redirect.addFlashAttribute("paymentNotice", "결제 결과를 확인하고 있습니다. 잠시 후 예약 상태를 확인해 주세요.");
        }
        return "redirect:/reservations/" + id;
    }
}