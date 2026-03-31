import json
import urllib.request
import urllib.error
import time

payload = {
    "orderId": f"TEST_{int(time.time())}",
    "amount": 50000,
    "orderInfo": "Thanh toan test",
    "transferContent": "LMSTEST"
}

try:
    req = urllib.request.Request(
        'https://lms-payment-backend.onrender.com/createMomoPayment',
        data=json.dumps(payload).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='POST'
    )
    response = urllib.request.urlopen(req, timeout=15)
    result = json.loads(response.read().decode())
    print(json.dumps(result, indent=2))
except urllib.error.HTTPError as e:
    print(f"HTTP Error {e.code}")
    print(e.read().decode())
except Exception as e:
    print(f"Error: {type(e).__name__}: {e}")
