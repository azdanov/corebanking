import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    transactions: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 50 },
        { duration: "1m", target: 50 },
        { duration: "30s", target: 0 },
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<500"],
    http_req_failed: ["rate<0.01"],
  },
};

const BASE_URL = "http://localhost:8080/api/v1/accounts";

export function setup() {
  const accounts = [];
  for (let i = 0; i < 10; i++) {
    const res = http.post(
      BASE_URL,
      JSON.stringify({
        customerId: "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        country: "US",
        currencies: ["USD"],
      }),
      { headers: { "Content-Type": "application/json" } },
    );
    check(res, { "account created": (r) => r.status === 201 });
    accounts.push(res.json("accountId"));
  }
  return { accounts };
}

export default function main(data) {
  const accountId =
    data.accounts[Math.floor(Math.random() * data.accounts.length)];
  const payload = JSON.stringify({
    accountId: accountId,
    amount: (Math.random() * 100 + 1).toFixed(2),
    currency: "USD",
    direction: "IN",
    description: "load test",
  });

  const res = http.post(`${BASE_URL}/${accountId}/transactions`, payload, {
    headers: { "Content-Type": "application/json" },
  });

  check(res, {
    "transaction created": (r) => r.status === 201,
  });
}
