#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def text(path):
    return path.read_text(encoding="utf-8")


def main():
    thin_pom = text(ROOT / "thin-web/pom.xml")
    central_pom = text(ROOT / "central-server/pom.xml")
    thick_pom = text(ROOT / "thick-client/pom.xml")
    thin_js = text(ROOT / "thin-web/src/main/resources/META-INF/resources/app.js")
    central_service = text(ROOT / "central-server/src/main/java/com/example/airline/central/BookingService.java")
    thick_service = text(ROOT / "thick-client/src/main/java/com/example/airline/thick/ThickBookingService.java")

    assert "domain-core" not in thin_pom, "thin-web must not depend on domain-core"
    assert "domain-core" in central_pom, "central-server must execute shared rules for V1"
    assert "domain-core" in thick_pom, "thick-client must execute shared rules for V2"
    assert "BookingCoordinator" in thick_service, "V2 must coordinate bookings in the client"
    assert "HttpAirlineClient" in thick_service, "V2 must call airline services directly"
    assert "BookingCoordinator" in central_service, "V1 must coordinate bookings on the server"
    assert "new BookingCoordinator" not in thin_js, "browser must not own booking coordination"
    assert "ConnectionTime" not in thin_js, "browser must not calculate connection rules"
    assert "ownDiscount" not in thin_js, "browser must not calculate discounts"

    print("ARCHITECTURE_CHECK_PASS")
    print("- thin-web: presentation only, no domain-core dependency")
    print("- central-server: V1 trip composition and booking coordination")
    print("- thick-client: V2 trip composition and direct airline coordination")
    print("- browser: no connection-time or discount calculation")


if __name__ == "__main__":
    main()
