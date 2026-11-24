import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import { Counter } from 'k6/metrics';

const created = new Counter('created_reservations');
const conflicts = new Counter('conflicting_reservations');
const baseUrl = __ENV.BASE_URL || 'http://localhost:8081';
const request = {
    branchId: Number(__ENV.BRANCH_ID || 1),
    vehicleId: Number(__ENV.VEHICLE_ID || 1),
    pickupAt: __ENV.PICKUP_AT || '2030-06-01T10:00:00',
    returnAt: __ENV.RETURN_AT || '2030-06-02T10:00:00',
};

export const options = {
    scenarios: {
        reservationRace: {
            executor: 'per-vu-iterations',
            vus: 100,
            iterations: 1,
            maxDuration: '1m',
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        created_reservations: ['count==1'],
        conflicting_reservations: ['count==99'],
    },
};

// 선점 충돌은 예상한 업무 응답으로 집계하고 서버 오류와 구분한다.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

/**
 * 재실행할 때도 다른 요청 키를 사용하도록 UUID를 만든다.
 *
 * @returns {string} UUID v4 형식의 요청 키
 */
function requestKey() {
    const bytes = new Uint8Array(crypto.randomBytes(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
    return hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16)
        + '-' + hex.slice(16, 20) + '-' + hex.slice(20);
}

/** 테스트 전에 대상 차량의 기간이 비어 있는지 확인한다. */
export function setup() {
    const url = baseUrl + '/api/vehicles?branchId=' + request.branchId
        + '&pickupAt=' + encodeURIComponent(request.pickupAt)
        + '&returnAt=' + encodeURIComponent(request.returnAt);
    const result = http.get(url);
    if (result.status !== 200
        || !result.json().vehicles.some(vehicle => vehicle.id === request.vehicleId)) {
        throw new Error('테스트할 차량의 비어 있는 대여 기간을 선택해 주세요.');
    }
}

/** 가상 사용자마다 서로 다른 키로 같은 차량·기간을 한 번 요청한다. */
export default function () {
    const result = http.post(baseUrl + '/api/reservations', JSON.stringify(request), {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': requestKey(),
        },
        timeout: '30s',
    });
    created.add(result.status === 201 ? 1 : 0);
    conflicts.add(result.status === 409 ? 1 : 0);
    check(result, { '선점 생성 또는 충돌로 응답합니다.': response => response.status === 201 || response.status === 409 });
}