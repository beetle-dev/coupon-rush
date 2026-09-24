import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Gauge, Trend } from 'k6/metrics';

const CONFIG = {
    baseUrl: __ENV.BASE_URL || 'http://localhost:8080',
    stage: __ENV.STAGE || '0-baseline',

    initialStock: parseInt(__ENV.INITIAL_STOCK || '1000', 10),
    totalRequests: parseInt(__ENV.TOTAL_REQUESTS || '10000', 10),
    vus: parseInt(__ENV.VUS || '3000', 10),
    rampUpDuration: __ENV.RAMP_UP_DURATION || '5s',
    maxDuration: __ENV.MAX_DURATION || '180s',
};

const issuedCount = new Counter('issued_success');
const rejectedCount = new Counter('rejected_409');
const unexpectedCount = new Counter('unexpected_status');
const completionElapsedMs = new Trend('completion_elapsed_ms');
const persistDrainMs = new Trend('persist_drain_ms');
const dbRemainingQuantity = new Gauge('db_remaining_quantity');

function toLocalDateTime(date) {
    return date.toISOString().split('.')[0];
}

export function setup() {
    const now = new Date();
    const issueStartAt = toLocalDateTime(new Date(now.getTime() - 24 * 60 * 60 * 1000));
    const issueEndAt = toLocalDateTime(new Date(now.getTime() + 400 * 24 * 60 * 60 * 1000));

    const res = http.post(
        `${CONFIG.baseUrl}/events`,
        JSON.stringify({
            name: `${CONFIG.stage}`,
            totalQuantity: CONFIG.initialStock,
            issueStartAt,
            issueEndAt,
        }),
        { headers: { 'Content-Type': 'application/json' } },
    );

    if (res.status !== 201) {
        throw new Error(`이벤트 생성 실패: status=${res.status} body=${res.body}`);
    }

    const eventId = res.json('id');
    console.log(`[setup] 이벤트 생성 완료: eventId=${eventId}, stock=${CONFIG.initialStock}`);

    return { eventId, testStartedAtMs: Date.now() };
}

export default function (data) {
    const i = exec.scenario.iterationInInstance;
    if (i >= CONFIG.totalRequests) {
        sleep(1);
        return;
    }
    const userId = i + 1;

    const res = http.post(
        `${CONFIG.baseUrl}/events/${data.eventId}/coupons`,
        null,
        { headers: { 'X-User-Id': String(userId) } },
    );

    completionElapsedMs.add(Date.now() - data.testStartedAtMs);

    check(res, { '201/202 또는 409만 나와야 함': (r) => r.status === 201 || r.status === 202 || r.status === 409 });

    if (res.status === 201 || res.status === 202) {
        issuedCount.add(1);
    } else if (res.status === 409) {
        rejectedCount.add(1);
    } else {
        unexpectedCount.add(1);
    }
}

export const options = {
    scenarios: {
        coupon_burst: {
            executor: 'ramping-vus',
            startVUs: 0,
            gracefulRampDown: '0s',
            stages: [
                { target: CONFIG.vus, duration: CONFIG.rampUpDuration },
                { target: CONFIG.vus, duration: CONFIG.maxDuration },
            ],
        },
    },
};

const DRAIN_TIMEOUT_MS = 120000;
const DRAIN_POLL_INTERVAL_SEC = 0.5;
const DRAIN_STABLE_POLLS = 3;

export function teardown(data) {
    const startedAt = Date.now();
    let remaining = null;
    let stablePolls = 0;

    while (Date.now() - startedAt < DRAIN_TIMEOUT_MS) {
        const res = http.get(`${CONFIG.baseUrl}/events/${data.eventId}`);

        if (res.status === 200) {
            const current = res.json('remainingQuantity');

            stablePolls = current === remaining ? stablePolls + 1 : 0;
            remaining = current;

            if (remaining === 0 || stablePolls >= DRAIN_STABLE_POLLS) {
                break;
            }
        }

        sleep(DRAIN_POLL_INTERVAL_SEC);
    }

    persistDrainMs.add(Date.now() - startedAt);
    dbRemainingQuantity.add(remaining === null ? -1 : remaining);

    console.log(`[teardown] 최종 remainingQuantity(DB) = ${remaining}, DB 반영 대기 ${((Date.now() - startedAt) / 1000).toFixed(1)}s`);
}

export function handleSummary(data) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

    const metric = (name, field = 'count') => {
        const m = data.metrics[name];
        if (!m) return 0;
        return m.values[field] ?? 0;
    };

    const issued = metric('issued_success');
    const rejected = metric('rejected_409');
    const unexpected = metric('unexpected_status');
    const totalServerResponses = issued + rejected + unexpected;
    const actualDurationSec = metric('completion_elapsed_ms', 'max') / 1000;
    const overIssuedCount = Math.max(0, issued - CONFIG.initialStock);
    const completedRatio = totalServerResponses / CONFIG.totalRequests;

    const persistDrainSec = metric('persist_drain_ms', 'max') / 1000;
    const dbRemaining = metric('db_remaining_quantity', 'value');
    const dbIssuedCount = dbRemaining < 0 ? null : CONFIG.initialStock - dbRemaining;
    const persistGap = dbIssuedCount === null ? null : issued - dbIssuedCount;

    const report = {
        stage: CONFIG.stage,
        ranAt: timestamp,
        config: CONFIG,
        eventId: data.setup_data ? data.setup_data.eventId : null,
        initialStock: CONFIG.initialStock,
        totalRequests: CONFIG.totalRequests,
        actualDurationSec,
        totalServerResponses,
        completedRatio,
        issued,
        rejected409: rejected,
        unexpectedStatus: unexpected,
        overIssuedCount,
        persistDrainSec,
        dbRemainingQuantity: dbRemaining,
        dbIssuedCount,
        persistGap,
        tps: totalServerResponses / actualDurationSec,
        latencyMsAvg: metric('http_req_duration', 'avg'),
        latencyMsP95: metric('http_req_duration', 'p(95)'),
        errorRate: totalServerResponses > 0 ? unexpected / totalServerResponses : 0,
        rawSummary: data,
    };

    const incompleteWarning = completedRatio < 0.95
        ? ` ⚠ maxDuration(${CONFIG.maxDuration}) 안에 다 못 끝남 — MAX_DURATION을 늘릴 것`
        : '';

    const persistGapWarning = persistGap
        ? ` ⚠ 접수 ${issued}건 중 ${persistGap}건이 DB에 없음 — 컨슈머 지연/DLT 확인 필요`
        : '';

    const stdout =
        `\n[${CONFIG.stage}] 결과 요약\n` +
        `  초과 발급   = ${overIssuedCount}건 (발급 ${issued} / 재고 ${CONFIG.initialStock})\n` +
        `  요청/완료    = 목표 ${CONFIG.totalRequests}건 → 서버 응답 ${totalServerResponses}건, 실제 소요 ${actualDurationSec.toFixed(2)}s${incompleteWarning}\n` +
        `  TPS         ≈ ${report.tps.toFixed(1)} req/s\n` +
        `  p95 지연     = ${report.latencyMsP95.toFixed(1)} ms (평균 ${report.latencyMsAvg.toFixed(1)} ms)\n` +
        `  에러율       = ${(report.errorRate * 100).toFixed(2)}% (예상외 응답 ${unexpected}건)\n` +
        `  DB 반영      = ${dbIssuedCount === null ? '측정 실패' : `${dbIssuedCount}건 (잔여 재고 ${dbRemaining}), 응답 종료 후 ${persistDrainSec.toFixed(1)}s 만에 수렴`}${persistGapWarning}\n` +
        `  결과표 한 줄 → ${CONFIG.stage} | TPS ${report.tps.toFixed(1)} | p95 ${report.latencyMsP95.toFixed(1)}ms | 에러율 ${(report.errorRate * 100).toFixed(2)}% | 초과발급 ${overIssuedCount}건 | DB수렴 ${persistDrainSec.toFixed(1)}s\n`;

    return {
        [`k6/results/${CONFIG.stage}-${timestamp}.json`]: JSON.stringify(report, null, 2),
        stdout,
    };
}
