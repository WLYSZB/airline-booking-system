#!/usr/bin/env python3
import concurrent.futures
import json
import urllib.error
import urllib.request
import uuid

CENTRAL = "http://localhost:8080"


def call(method, path, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        CENTRAL + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode("utf-8"))


def search(passengers=2):
    return call("POST", "/api/search", {
        "origin": "WUH",
        "destination": "PEK",
        "date": "2026-11-15",
        "passengers": passengers,
        "airlineCodes": ["C"],
        "sortMode": "PRICE",
    })[1]


def main():
    result = search()
    direct = next((trip for trip in result["trips"] if trip["legs"][0]["flightNumber"] == "C3101"), None)
    if direct is None:
        raise AssertionError("未找到 C3101 测试航班")
    before = direct["legs"][0]["availableSeats"]
    if before < 2:
        raise AssertionError(f"C3101 余座不足，当前 {before}")

    def attempt(index):
        body = {
            "trip": direct,
            "passengers": [
                {"name": f"并发乘客{index}A", "passportNumber": f"E9000000{index}", "seatPreference": "AISLE"},
                {"name": f"并发乘客{index}B", "passportNumber": f"E9100000{index}", "seatPreference": "WINDOW"},
            ],
            "idempotencyKey": str(uuid.uuid4()),
        }
        status, payload = call("POST", "/api/bookings", body)
        return status, payload

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(attempt, (1, 2)))

    successes = [payload for status, payload in results if status == 200]
    failures = [payload for status, payload in results if status != 200]
    if len(successes) != 1 or len(failures) != 1:
        raise AssertionError(f"并发扣座结果异常: {results}")
    print("并发请求结果: 1 成功, 1 失败")
    print("失败响应:", failures[0]["code"], failures[0]["message"])

    booking = successes[0]
    status, cancelled = call("POST", f"/api/bookings/{booking['bookingReference']}/cancel")
    assert status == 200 and cancelled["status"] == "CANCELLED", cancelled
    after = search()["trips"]
    restored = next(trip for trip in after if trip["legs"][0]["flightNumber"] == "C3101")["legs"][0]["availableSeats"]
    assert restored == before, (before, restored)
    print(f"C3101 余座回补: {before} -> {restored}")
    print("CONCURRENCY_TEST_PASS")


if __name__ == "__main__":
    main()
