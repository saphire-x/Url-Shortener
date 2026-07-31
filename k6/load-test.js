import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Custom metrics so we can see write vs read latency separately in the summary
const shortenLatency = new Trend('shorten_latency', true);
const redirectLatency = new Trend('redirect_latency', true);
const notFoundCount = new Counter('not_found_count');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        // Phase 1: hammer the write path only
        shorten_only: {
            executor: 'constant-vus',
            exec: 'shortenTest',
            vus: 50,
            duration: '30s',
            startTime: '0s',
        },
        // Phase 2: hammer the read/redirect path only (runs after phase 1 finishes)
        redirect_only: {
            executor: 'constant-vus',
            exec: 'redirectTest',
            vus: 50,
            duration: '30s',
            startTime: '35s',
        },
        // Phase 3: realistic mixed traffic (90% reads, 10% writes — typical for a URL shortener)
        mixed_traffic: {
            executor: 'constant-vus',
            exec: 'mixedTest',
            vus: 100,
            duration: '30s',
            startTime: '70s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],   // flag if p95 exceeds 500ms
        http_req_failed: ['rate<0.01'],     // flag if more than 1% of requests fail
    },
};

// Shared pool of short codes created during setup, so the read test
// has real, valid codes to hit instead of only testing 404s.
export function setup() {
    const codes = [];
    for (let i = 0; i < 20; i++) {
        const url = `https://example.com/setup-page-${i}-${Date.now()}`;
        const res = http.post(`${BASE_URL}/api/shorten`, `originalUrl=${encodeURIComponent(url)}`, {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        });
        if (res.status === 200) {
            try {
                const body = JSON.parse(res.body);
                const code = body.shortUrl.split('/').pop();
                codes.push(code);
            } catch (e) {
                // ignore parse failures during setup
            }
        }
    }
    console.log(`Setup created ${codes.length} short codes for read testing`);
    return { codes };
}

// ---- Test 1: write path (/api/shorten) ----
export function shortenTest() {
    const url = `https://example.com/page-${__VU}-${__ITER}-${Date.now()}`;
    const res = http.post(
        `${BASE_URL}/api/shorten`,
        `originalUrl=${encodeURIComponent(url)}`,
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    );

    shortenLatency.add(res.timings.duration);

    check(res, {
        'shorten status is 200': (r) => r.status === 200,
    });

    sleep(0.1);
}

// ---- Test 2: read/redirect path (uses codes created in setup) ----
export function redirectTest(data) {
    if (!data.codes || data.codes.length === 0) {
        return;
    }
    const code = data.codes[Math.floor(Math.random() * data.codes.length)];

    const res = http.get(`${BASE_URL}/${code}`, { redirects: 0 });

    redirectLatency.add(res.timings.duration);

    if (res.status === 404) {
        notFoundCount.add(1);
    }

    check(res, {
        'redirect status is 302 or 404': (r) => r.status === 302 || r.status === 404,
    });

    sleep(0.1);
}

// ---- Test 3: mixed realistic traffic (90% reads, 10% writes) ----
export function mixedTest(data) {
    if (Math.random() < 0.9 && data.codes && data.codes.length > 0) {
        redirectTest(data);
    } else {
        shortenTest();
    }
}