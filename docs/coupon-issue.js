import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080';
const EVENT_ID = 2;

export const options = {
    scenarios: {
        coupon_issue: {
            executor: 'per-vu-iterations',
            vus: 100,
            iterations: 1,
            maxDuration: '30s',
        },
    },
};

export default function () {
    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/coupons`,
        null,
        { headers: { 'X-User-Id': String(__VU) } }
    );

    check(res, {
        '201 또는 409만 나와야 함': (r) => r.status === 201 || r.status === 409,
    });
}