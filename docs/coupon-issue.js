import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Gauge } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

const INITIAL_STOCK = 1000;
const VUS = 10000;
const MAX_CONCURRENT_VUS = 150;
const MAX_DURATION = '60s';
const STATUS_ZERO_SAMPLE_RATE = 0.01;

const KNOWN_STATUSES = [0, 400, 401, 403, 404, 408, 429, 500, 502, 503, 504];
const statusCounters = {};
for (const code of KNOWN_STATUSES) {
    statusCounters[code] = new Counter(`status_${code}`);
}
const otherStatusCounter = new Counter('status_other');
function getStatusCounter(status) {
    return statusCounters[status] ?? otherStatusCounter;
}

const issuedCount = new Counter('coupon_issued_201');
const duplicateOrOutOfStockCount = new Counter('coupon_rejected_409');
const unexpectedCount = new Counter('coupon_unexpected_status');
const eventIdGauge = new Gauge('created_event_id');

const STATUS_ZERO_ERROR_CATEGORIES = [
    { key: 'connection_refused', match: (msg) => msg.includes('connection refused') },
    { key: 'connection_reset', match: (msg) => msg.includes('reset by peer') || msg.includes('connection reset') },
    { key: 'dial_timeout', match: (msg) => msg.includes('dial') && msg.includes('timeout') },
    { key: 'read_timeout', match: (msg) => msg.includes('timeout') },
    { key: 'eof', match: (msg) => msg.includes('EOF') },
    { key: 'dns', match: (msg) => msg.includes('lookup') || msg.includes('no such host') },
];
const statusZeroErrorCounters = {};
const statusZeroErrorCodeGauges = {};
for (const { key } of STATUS_ZERO_ERROR_CATEGORIES) {
    statusZeroErrorCounters[key] = new Counter(`status0_error_${key}`);
    statusZeroErrorCodeGauges[key] = new Gauge(`status0_error_code_${key}`);
}
const statusZeroErrorOtherCounter = new Counter('status0_error_other');
const statusZeroErrorOtherCodeGauge = new Gauge('status0_error_code_other');

function classifyStatusZeroError(errorMessage) {
    const msg = errorMessage || '';
    return STATUS_ZERO_ERROR_CATEGORIES.find(({ match }) => match(msg)) ?? null;
}

export const options = {
    scenarios: {
        coupon_issue: {
            executor: 'shared-iterations',
            vus: MAX_CONCURRENT_VUS,
            iterations: VUS,
            maxDuration: MAX_DURATION,
        },
    },
};

export function setup() {
    const res = http.post(
        `${BASE_URL}/events`,
        JSON.stringify({
            name: "K6 test",
            totalQuantity: INITIAL_STOCK,
            issueStartAt: "2026-09-09T00:00:00",
            issueEndAt: "2027-09-09T00:00:00"
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (res.status !== 201) {
        throw new Error(`이벤트 생성 실패: status=${res.status} body=${res.body}`);
    }

    const eventId = res.json('id');
    eventIdGauge.add(eventId);
    console.log(`[setup] 이벤트 생성 완료: eventId=${eventId}`);

    return { eventId };
}

export default function (data) {
    const userId = exec.scenario.iterationInInstance + 1;

    const res = http.post(
        `${BASE_URL}/events/${data.eventId}/coupons`,
        null,
        { headers: { 'X-User-Id': String(userId) } }
    );

    check(res, {
        '201 또는 409만 나와야 함': (r) => r.status === 201 || r.status === 409,
    });

    if (res.status === 201) {
        issuedCount.add(1);
    } else if (res.status === 409) {
        duplicateOrOutOfStockCount.add(1);
    } else {
        unexpectedCount.add(1);
        getStatusCounter(res.status).add(1);

        if (res.status === 0) {
            const category = classifyStatusZeroError(res.error);
            if (category) {
                statusZeroErrorCounters[category.key].add(1);
                statusZeroErrorCodeGauges[category.key].add(res.error_code);
            } else {
                statusZeroErrorOtherCounter.add(1);
                statusZeroErrorOtherCodeGauge.add(res.error_code);
            }

            if (Math.random() < STATUS_ZERO_SAMPLE_RATE) {
                console.warn(`[status 0 샘플] category=${category ? category.key : 'other'} error=${res.error} error_code=${res.error_code}`);
            }
        }
    }
}

export function handleSummary(data) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

    const eventId = data.metrics.created_event_id ? data.metrics.created_event_id.values.value : null;
    const issued = data.metrics.coupon_issued_201 ? data.metrics.coupon_issued_201.values.count : 0;
    const rejected = data.metrics.coupon_rejected_409 ? data.metrics.coupon_rejected_409.values.count : 0;
    const unexpected = data.metrics.coupon_unexpected_status ? data.metrics.coupon_unexpected_status.values.count : 0;
    const oversellRatio = INITIAL_STOCK > 0 ? issued / INITIAL_STOCK : null;

    const statusBreakdown = {};
    for (const key in data.metrics) {
        if (key.startsWith('status_')) {
            const code = key.replace('status_', '');
            const count = data.metrics[key].values.count;
            if (count > 0) statusBreakdown[code] = count;
        }
    }

    const statusZeroErrorBreakdown = {};
    for (const key in data.metrics) {
        if (key.startsWith('status0_error_code_')) continue;
        if (key.startsWith('status0_error_')) {
            const category = key.replace('status0_error_', '');
            const count = data.metrics[key].values.count;
            if (count === 0) continue;
            const codeMetric = data.metrics[`status0_error_code_${category}`];
            statusZeroErrorBreakdown[category] = {
                count,
                sampleErrorCode: codeMetric ? codeMetric.values.value : null,
            };
        }
    }

    const report = {
        ranAt: timestamp,
        eventId,
        initialStock: INITIAL_STOCK,
        vus: VUS,
        maxConcurrentVUs: MAX_CONCURRENT_VUS,
        issued201: issued,
        rejected409: rejected,
        unexpectedStatus: unexpected,
        unexpectedStatusBreakdown: statusBreakdown,
        statusZeroErrorBreakdown,
        oversellRatio: oversellRatio,
        rawSummary: data,
    };

    return {
        [`docs/results/coupon-issue-${timestamp}.json`]: JSON.stringify(report, null, 2),
        stdout: `\n[coupon-issue 결과] eventId=${eventId} 재고=${INITIAL_STOCK} 발급=${issued}건 거부=${rejected}건 오버셀배율=${oversellRatio}\n` +
            `상태코드별 미분류 응답: ${JSON.stringify(statusBreakdown)}\n`,
    };
}