#!/usr/bin/env python3
import json
import sys
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
        with urllib.request.urlopen(request, timeout=15) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        raise RuntimeError(f"{method} {path} -> HTTP {error.code}: {raw}") from error


def search():
    status, result = call("POST", "/api/search", {
        "origin": "WUH",
        "destination": "PEK",
        "date": "2026-11-15",
        "passengers": 2,
        "airlineCodes": ["A", "B", "C"],
        "sortMode": "PRICE",
    })
    assert status == 200, status
    return result


def seat_snapshot(result, flight_ids):
    for trip in result["trips"]:
        ids = {leg["flightInstanceId"]: leg["availableSeats"] for leg in trip["legs"]}
        if set(flight_ids).issubset(ids):
            return [ids[flight_id] for flight_id in flight_ids]
    return None


def main():
    initial = search()
    cross = next(
        (trip for trip in initial["trips"] if len({leg["airlineCode"] for leg in trip["legs"]}) > 1),
        None,
    )
    if cross is None:
        raise AssertionError("未找到跨航司联程，无法验证核心场景")
    assert cross["passengers"] == 2, cross
    assert float(cross["pricePerPassenger"]) * 2 == float(cross["totalPrice"]), cross
    flight_ids = [leg["flightInstanceId"] for leg in cross["legs"]]
    before_seats = [leg["availableSeats"] for leg in cross["legs"]]
    print("跨航司行程:", " -> ".join(
        f"{leg['airlineCode']}{leg['flightNumber']}({leg['origin']}-{leg['destination']})"
        for leg in cross["legs"]
    ))
    print("预订前余座:", before_seats)

    key = str(uuid.uuid4())
    request = {
        "trip": cross,
        "passengers": [
            {"name": "张三", "passportNumber": "E10000001", "seatPreference": "AISLE"},
            {"name": "李四", "passportNumber": "E10000002", "seatPreference": "WINDOW"},
        ],
        "idempotencyKey": key,
    }
    _, receipt = call("POST", "/api/bookings", request)
    assert receipt["status"] == "CONFIRMED", receipt
    assert receipt["bookingReference"].startswith("BK"), receipt
    assert len(receipt["legs"]) == len(cross["legs"]), receipt
    assert float(receipt["totalPrice"]) == float(cross["totalPrice"]), (receipt["totalPrice"], cross["totalPrice"])
    print("预订成功:", receipt["bookingReference"], receipt["totalPrice"], receipt["status"])

    _, duplicate = call("POST", "/api/bookings", request)
    assert duplicate["bookingReference"] == receipt["bookingReference"], "幂等重试生成了不同预订"
    print("幂等重试通过:", duplicate["bookingReference"])

    _, loaded = call("GET", f"/api/bookings/{receipt['bookingReference']}")
    assert loaded["status"] == "CONFIRMED"
    assert len(loaded["passengers"]) == 2
    assert [p["seatPreference"] for p in loaded["passengers"]] == ["AISLE", "WINDOW"], loaded
    print("收据查询通过:", loaded["bookingReference"],
          "乘客偏好:", [p["seatPreference"] for p in loaded["passengers"]])

    after_booking = search()
    after_seats = seat_snapshot(after_booking, flight_ids)
    if after_seats is not None:
        assert after_seats == [seat - 2 for seat in before_seats], (before_seats, after_seats)
        print("扣座验证通过:", after_seats)

    _, cancelled = call("POST", f"/api/bookings/{receipt['bookingReference']}/cancel")
    assert cancelled["status"] == "CANCELLED", cancelled
    assert all(leg["status"] == "CANCELLED" for leg in cancelled["legs"]), cancelled
    print("取消成功:", cancelled["status"], "航段状态:", [leg["status"] for leg in cancelled["legs"]])

    _, cancelled_again = call("POST", f"/api/bookings/{receipt['bookingReference']}/cancel")
    assert cancelled_again["status"] == "CANCELLED", cancelled_again
    print("重复取消幂等通过")

    after_cancel = search()
    restored = seat_snapshot(after_cancel, flight_ids)
    if restored is not None:
        assert restored == before_seats, (before_seats, restored)
        print("库存回补通过:", restored)

    print("SMOKE_TEST_PASS")


if __name__ == "__main__":
    try:
        main()
    except Exception as exception:
        print(f"SMOKE_TEST_FAIL: {exception}", file=sys.stderr)
        raise
