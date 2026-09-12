import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const CONFIG = {
    baseUrl: __ENV.BASE_URL || 'http://localhost:8080',
    stage: __ENV.STAGE || '0-baseline',                                         // 테스트명

    initialStock: parseInt(__ENV.INITIAL_STOCK || '1000', 10),      // 초기 재고 (1,000)
    totalRequests: parseInt(__ENV.TOTAL_REQUESTS || '10000', 10),   // 전체 요청 수 (10,000)
    vus: parseInt(__ENV.VUS || '3000', 10),                         // 테스트 환경에서 안전하게 실행이 가능한 VU 수 (3,000)
    rampUpDuration: __ENV.RAMP_UP_DURATION || '5s',                            // connection refused를 방지하기 위해 5초동안 VU 서서히 증가
    maxDuration: __ENV.MAX_DURATION || '180s',                                 // stage2(VU 3,000)가 진행되는 시간
};

const issuedCount = new Counter('issued_201');
const rejectedCount = new Counter('rejected_409');
const unexpectedCount = new Counter('unexpected_status');
const completionElapsedMs = new Trend('completion_elapsed_ms');                 // 실제 테스트 시작부터 종료까지의 시간을 재기 위한 메트릭

function toLocalDateTime(date) {
    return date.toISOString().split('.')[0];
}

export function setup() {                       // VU들이 반복을 시작하기 전에 한번만 실행
    const now = new Date();
    const issueStartAt = toLocalDateTime(new Date(now.getTime() - 24 * 60 * 60 * 1000));        // 현재 시간 기준 1일 전
    const issueEndAt = toLocalDateTime(new Date(now.getTime() + 400 * 24 * 60 * 60 * 1000));    // 현재 시간 기준 400일 후

    const res = http.post(      // VU 3,000 전부가 같은 이벤트를 두고 경쟁해야 하므로 한번만 호출하여 해당 이벤트의 쿠폰을 발급 요청
        `${CONFIG.baseUrl}/events`,
        JSON.stringify({
            name: `${CONFIG.stage}`,            // 이벤트명
            totalQuantity: CONFIG.initialStock, // 초기 재고
            issueStartAt,                       // 쿠폰 발급 가능 시작 일시
            issueEndAt,                         // 쿠폰 발급 가능 종료 일시
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

export default function (data) {               // VU가 반복하는 작업
    const i = exec.scenario.iterationInInstance;    // K6가 제공하는 반복 횟수 고유 번호
    if (i >= CONFIG.totalRequests) {                // 전체 요청 수 이상 반복되었다면 VU가 더 이상 새 요청을 보내지 않고 대기 (stage2가 끝날 때 까지만)
        sleep(1);
        return;
    }
    const userId = i + 1;                           // 쿠폰 발급 원칙인 1인 1매 발급을 위해 userId를 모두 고유하게 설정하기 (중복발급 검증은 JUnit에서 별도    테스트)

    const res = http.post(
        `${CONFIG.baseUrl}/events/${data.eventId}/coupons`,
        null,
        { headers: { 'X-User-Id': String(userId) } },
    );

    completionElapsedMs.add(Date.now() - data.testStartedAtMs);

    check(res, { '201 또는 409만 나와야 함': (r) => r.status === 201 || r.status === 409 });   // 통과율 기록

    if (res.status === 201) {
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
            executor: 'ramping-vus',                                    // 목표 VU수 까지 점차 증가
            startVUs: 0,                                                // 시작 VU수
            gracefulRampDown: '0s',                                     // VU가 줄어들 때 감소되는 시간 설정 (즉시 종료)
            stages: [
                { target: CONFIG.vus, duration: CONFIG.rampUpDuration },
                { target: CONFIG.vus, duration: CONFIG.maxDuration },
            ],
        },
    },
};

export function teardown(data) {                                                                // 테스트 종료 후 한번 실행
    const res = http.get(`${CONFIG.baseUrl}/events/${data.eventId}`);
    if (res.status === 200) {
        console.log(`[teardown] 최종 remainingQuantity(DB) = ${res.json('remainingQuantity')}`);      // DB Event에 저장된 재고 쿠폰 수 (실제 검증은 issued_201 카운터로 함)
    }
}

export function handleSummary(data) {                                                   // 테스트 종료 후 한번 실행
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');    // K6 종료 시간 (마지막 요청 완료 시간X)

    const metric = (name, field = 'count') => {
        const m = data.metrics[name];
        if (!m) return 0;
        return m.values[field] ?? 0;
    };

    const issued = metric('issued_201');                                                        // [정상응답] 발급 성공
    const rejected = metric('rejected_409');                                                    // [정상응답] 재고 부족
    const unexpected = metric('unexpected_status');                                             // [예외응답] 예외 발생
    const totalServerResponses = issued + rejected + unexpected;                                      // 위 세 값 총합
    const actualDurationSec = metric('completion_elapsed_ms', 'max') / 1000;       // 실제 요청이 완료된 시점 (maxDuration 때문에 별도 측정 필요)
    const overIssuedCount = Math.max(0, issued - CONFIG.initialStock);                  // 초과 발급 수
    const completedRatio = totalServerResponses / CONFIG.totalRequests;                       // 완료율

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
        issued201: issued,
        rejected409: rejected,
        unexpectedStatus: unexpected,
        overIssuedCount,
        tps: totalServerResponses / actualDurationSec,
        latencyMsAvg: metric('http_req_duration', 'avg'),
        latencyMsP95: metric('http_req_duration', 'p(95)'),
        errorRate: totalServerResponses > 0 ? unexpected / totalServerResponses : 0,
        rawSummary: data,
    };

    const incompleteWarning = completedRatio < 0.95     // 완료율이 95%미만이라면 제한 시간 부족이므로 maxDuration을 늘려서 더 기다려야한다.
        ? ` ⚠ maxDuration(${CONFIG.maxDuration}) 안에 다 못 끝남 — MAX_DURATION을 늘릴 것`
        : '';

    const stdout =
        `\n[${CONFIG.stage}] 결과 요약\n` +
        `  초과 발급   = ${overIssuedCount}건 (발급 ${issued} / 재고 ${CONFIG.initialStock})\n` +
        `  요청/완료    = 목표 ${CONFIG.totalRequests}건 → 서버 응답 ${totalServerResponses}건, 실제 소요 ${actualDurationSec.toFixed(2)}s${incompleteWarning}\n` +
        `  TPS         ≈ ${report.tps.toFixed(1)} req/s\n` +
        `  p95 지연     = ${report.latencyMsP95.toFixed(1)} ms (평균 ${report.latencyMsAvg.toFixed(1)} ms)\n` +
        `  에러율       = ${(report.errorRate * 100).toFixed(2)}% (예상외 응답 ${unexpected}건)\n` +
        `  결과표 한 줄 → ${CONFIG.stage} | TPS ${report.tps.toFixed(1)} | p95 ${report.latencyMsP95.toFixed(1)}ms | 에러율 ${(report.errorRate * 100).toFixed(2)}% | 초과발급 ${overIssuedCount}건\n`;

    return {
        [`k6/results/${CONFIG.stage}-${timestamp}.json`]: JSON.stringify(report, null, 2),
        stdout,
    };
}
