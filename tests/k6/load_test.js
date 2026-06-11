import http from 'k6/http';
import { check, sleep } from 'k6';

// Stress-test configuration simulating TARGET_SCALE thresholds
export const options = {
    stages: [
        { duration: '30s', target: 2500 },  // Ramp up aggressively to 2,500 users
        { duration: '2m', target: 10000 },  // Surge to peak capacity of 10,000 requests/sec concurrent load
        { duration: '30s', target: 0 }     // Ramp down safely
    ],
    thresholds: {
        http_req_failed: ['rate<0.0001'],         // Hard constraint: Less than 0.01% error tolerance
        http_req_duration: ['p95<50', 'p99<100'], // Assertions matching TARGET_LATENCY SLA (Sub-50ms P95, Sub-100ms P99)
    },
};

export default function () {
    const url = 'http://localhost:8080/v1/compliance/evaluate';
    
    const payload = JSON.stringify({
        transactionId: `TXN-${Math.floor(Math.random() * 100000000)}`,
        tenantBankId: 'NG-BANK-033',
        amountNgn: 45000000.50,
        assetClassification: 'COMMERCIAL_LOANS',
        counterpartyIdentifier: 'RC-9982312',
        sourceAccountHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
        transactionTimestamp: new Date().toISOString()
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Tenant-Id': 'NG-BANK-033',
            'Authorization': 'Bearer MockJwtTokenSigningKeyAsserted2026'
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
        'transaction validated successfully': (r) => r.body.includes('batchExecutionId') === false,
    });

    sleep(0.01); // 10ms pacing to regulate load distribution loops per VU
}