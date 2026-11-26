import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';

// 사용자 요청 지연과 성공·거절·미확인 결과를 분리한다.
const holdLatency = new Trend('hold_latency', true);
const checkoutLatency = new Trend('checkout_latency', true);
const confirmed = new Counter('confirmed_reservations');
const declined = new Counter('declined_reservations');
const uncertain = new Counter('uncertain_payments');
const vus = Number(__ENV.VUS || 5);
const iterations = Number(__ENV.ITERATIONS || 20);
const runId = Number(__ENV.RUN_ID || 0);
if (!Number.isInteger(runId) || runId < 0 || !Number.isInteger(iterations) || (iterations < 1 || iterations > 1000)
    || !Number.isInteger(vus) || vus < 1) {
    throw new Error('RUN_ID, ITERATIONS와 VUS 설정을 확인해 주세요.');
}
export const options = {
    scenarios: {
        checkout: { executor: 'shared-iterations', vus, iterations, maxDuration: '5m' },
    },
    // 기본 지연 실험에서는 503이나 500을 성공으로 집계하지 않는다.
    thresholds: { checks: ['rate==1'], http_req_failed: ['rate==0'] },
};
const base = __ENV.BASE_URL || 'http://localhost:8081';

/** 각 반복에 다른 대여 기간과 UUID를 만들어 선점한 뒤 결제한다. */
export default function () {
    const index = runId * 1000 + exec.scenario.iterationInTest;
    // UTC 계산은 날짜 문자열을 만들기 위한 용도다. API에는 오프셋 없는 시각을 보낸다.
    const start = Date.UTC(2030, 6, 1, 10) + index * 2 * 86400000;
    const payload = JSON.stringify({
        branchId: Number(__ENV.BRANCH_ID || 1),
        vehicleId: Number(__ENV.VEHICLE_ID || 1),
        pickupAt: new Date(start).toISOString().slice(0, 19),
        returnAt: new Date(start + 86400000).toISOString().slice(0, 19),
    });
    // 요청마다 UUID를 만들고 같은 예약 결제는 서버의 예약 ID로 중복을 막는다.
    const key = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, token => {
        const random = Math.floor(Math.random() * 16);
        return (token === 'x' ? random : (random & 3) | 8).toString(16);
    });
    const held = http.post(base + '/api/reservations', payload, {
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
        tags: { name: 'POST /api/reservations' },
    });
    holdLatency.add(held.timings.duration);
    if (!check(held, { '선점 생성': response => response.status === 201 })) {
        return;
    }
    const id = held.json('id');
    const paid = http.post(base + '/api/reservations/' + id + '/payment', null, {
        timeout: '20s', tags: { name: 'POST /api/reservations/:id/payment' },
    });
    checkoutLatency.add(paid.timings.duration);
    if (!check(paid, { '최종 결제 결과 응답': response => response.status === 200 })) {
        uncertain.add(1);
        return;
    }
    const status = paid.json('status');
    confirmed.add(status === 'CONFIRMED' ? 1 : 0);
    declined.add(status === 'PAYMENT_FAILED' ? 1 : 0);
    check(status, { '승인 또는 거절 확정': value => value === 'CONFIRMED' || value === 'PAYMENT_FAILED' });
}