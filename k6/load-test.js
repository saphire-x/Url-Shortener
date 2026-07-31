import http from "k6/http";
import { check } from "k6";
import { Trend, Counter } from "k6/metrics";

const shortenLatency = new Trend("shorten_latency", true);
const redirectLatency = new Trend("redirect_latency", true);
const notFoundCount = new Counter("not_found_count");

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
    scenarios: {
        shorten_only: {
            executor: "ramping-vus",
            exec: "shortenTest",
            stages: [
                { duration: "30s", target: 100 },
                { duration: "30s", target: 200 },
                { duration: "30s", target: 300 },
                { duration: "30s", target: 500 },
                { duration: "30s", target: 0 },
            ],
        },

        redirect_only: {
            executor: "ramping-vus",
            exec: "redirectTest",
            startTime: "3m",
            stages: [
                { duration: "30s", target: 100 },
                { duration: "30s", target: 300 },
                { duration: "30s", target: 500 },
                { duration: "30s", target: 700 },
                { duration: "30s", target: 0 },
            ],
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        shorten_latency: ["p(95)<1500"],
        redirect_latency: ["p(95)<500"],
    },
};

// Create plenty of URLs for redirect testing
export function setup() {
    const codes = [];

    for (let i = 0; i < 1000; i++) {
        const url = `https://example.com/setup-${i}-${Date.now()}`;

        const res = http.post(
            `${BASE_URL}/api/shorten`,
            `originalUrl=${encodeURIComponent(url)}`,
            {
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                },
            }
        );

        if (res.status === 200) {
            try {
                const body = JSON.parse(res.body);
                codes.push(body.shortUrl.split("/").pop());
            } catch (_) {}
        }
    }

    console.log(`Created ${codes.length} URLs`);
    return { codes };
}

// ---------------- WRITE TEST ----------------

export function shortenTest() {

    const url = `https://example.com/${__VU}-${__ITER}-${Date.now()}-${Math.random()}`;

    const res = http.post(
        `${BASE_URL}/api/shorten`,
        `originalUrl=${encodeURIComponent(url)}`,
        {
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
            redirects: 0,
        }
    );

    shortenLatency.add(res.timings.duration);

    check(res, {
        "status 200": (r) => r.status === 200,
    });

    if (res.status !== 200) {
        console.log(`${res.status} ${res.body}`);
    }
}

// ---------------- READ TEST ----------------

export function redirectTest(data) {

    const code =
        data.codes[Math.floor(Math.random() * data.codes.length)];

    const res = http.get(`${BASE_URL}/${code}`, {
        redirects: 0,
    });

    redirectLatency.add(res.timings.duration);

    if (res.status === 404) {
        notFoundCount.add(1);
    }

    check(res, {
        "redirect 302": (r) => r.status === 302,
    });

    if (res.status !== 302) {
        console.log(`${res.status}`);
    }
}