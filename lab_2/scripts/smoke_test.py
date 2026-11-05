#!/usr/bin/env python3
"""Check the lab-2 Compose deployment through its public gateway.

The checks create persistent users, visits and orders. Use a separate Compose
project/volumes for verification. --check-failure briefly stops menu-service.
"""

import argparse
from dataclasses import dataclass
from decimal import Decimal
import json
from pathlib import Path
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid


COMPOSE_ROOT = Path(__file__).resolve().parent.parent


@dataclass
class Response:
    status: int
    headers: dict
    body: object


class SmokeTest:
    def __init__(self, project, gateway, eureka):
        self.project = project
        self.gateway = gateway.rstrip("/")
        self.eureka = eureka.rstrip("/")
        self.suffix = uuid.uuid4().hex[:12]

    def compose(self, *args):
        result = subprocess.run(
            ["docker", "compose", "-p", self.project, *args],
            cwd=COMPOSE_ROOT, capture_output=True, text=True, timeout=120,
        )
        if result.returncode:
            raise AssertionError(f"Compose {' '.join(args)} failed: {result.stderr.strip()}")
        return result.stdout.strip()

    def sql(self, service, database, statement):
        return self.compose(
            "exec", "-T", service, "psql", "-qAt", "-v", "ON_ERROR_STOP=1",
            "-U", database, "-d", database, "-c", statement,
        )

    def request(self, method, path, body=None, expected=200, base=None):
        raw = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Accept": "application/json"}
        if raw is not None:
            headers["Content-Type"] = "application/json"
        req = Request((base or self.gateway) + path, raw, headers, method=method)
        try:
            result = urlopen(req, timeout=15)
        except HTTPError as error:
            result = error
        except (URLError, TimeoutError) as error:
            raise AssertionError(f"{method} {path}: {error}") from error
        with result:
            text = result.read().decode("utf-8")
            try:
                parsed = json.loads(text, parse_float=Decimal) if text else None
            except json.JSONDecodeError:
                parsed = text
            response = Response(result.status, {k.lower(): v for k, v in result.headers.items()}, parsed)
        if expected is not None and response.status != expected:
            raise AssertionError(
                f"{method} {path}: expected {expected}, got {response.status}: {str(response.body)[:500]}"
            )
        return response

    def eventually(self, description, action, timeout=300):
        deadline = time.monotonic() + timeout
        last_error = None
        while time.monotonic() < deadline:
            try:
                result = action()
                print(f"[OK] {description}", flush=True)
                return result
            except (AssertionError, KeyError, TypeError) as error:
                last_error = error
                time.sleep(2)
        raise AssertionError(f"{description}: timed out; last result: {last_error}")

    def registry(self):
        payload = self.request("GET", "/eureka/apps", base=self.eureka).body
        applications = payload["applications"].get("application", [])
        if isinstance(applications, dict):
            applications = [applications]
        up = set()
        for application in applications:
            instances = application.get("instance", [])
            if isinstance(instances, dict):
                instances = [instances]
            if any(instance.get("status") == "UP" for instance in instances):
                up.add(application["name"].lower())
        expected = {"config-service", "api-gateway", "user-service", "menu-service", "visit-order-service"}
        assert expected <= up, f"Eureka missing UP services: {sorted(expected - up)}"
        return up

    def infrastructure(self):
        self.eventually("all five clients registered UP in Eureka", self.registry)
        self.eventually("Gateway routes requests to menu-service", lambda: self.request("GET", "/api/menu"))
        for service, port in [("discovery-service", 8761), ("api-gateway", 8080),
                              ("user-service", 8081), ("menu-service", 8082), ("visit-order-service", 8083)]:
            config = json.loads(self.compose(
                "exec", "-T", "config-service", "curl", "-fsS", f"http://localhost:8888/{service}/default"
            ))
            assert config["name"] == service and config["propertySources"], f"No config for {service}"
            assert any(source["source"].get("server.port") == port for source in config["propertySources"]), service
        print("[OK] Config Server returns service configuration from its repository", flush=True)

        swagger = self.request("GET", "/swagger-ui.html").body
        assert isinstance(swagger, str) and "swagger" in swagger.lower(), "Swagger UI HTML was not returned"
        swagger_config = self.request("GET", "/v3/api-docs/swagger-config").body
        assert {"/openapi/users", "/openapi/menu", "/openapi/visits-orders"} <= {
            entry["url"] for entry in swagger_config.get("urls", [])
        }, "Swagger UI does not list all three services"
        for name in ("users", "menu", "visits-orders"):
            spec = self.request("GET", f"/openapi/{name}").body
            assert spec.get("openapi") and spec.get("paths"), f"Empty OpenAPI specification: {name}"
            assert spec["servers"][0]["url"] == "/", f"OpenAPI uses an internal origin: {name}"
            assert not any(path.startswith("/internal/") for path in spec["paths"]), name
        self.request("GET", "/internal/menu/tags?ids=1", expected=404)
        self.request("DELETE", "/internal/users/2147483647", expected=404)
        print("[OK] one Swagger UI, three API specifications, internal routes hidden", flush=True)

        config = json.loads(self.compose("config", "--format", "json"))
        published = {name for name, service in config["services"].items() if service.get("ports")}
        assert published == {"api-gateway", "discovery-service"}, f"Unexpected published services: {published}"
        for network in ("backend", "users-data", "menu-data", "visits-data"):
            assert config["networks"][network]["internal"], f"Network is not internal: {network}"
        schemas = {
            "users-db": ("users", {"users"}),
            "menu-db": ("menu", {"menu", "taste_tags", "menu_taste_tags"}),
            "visits-db": ("visits", {"visits", "orders", "order_items", "visit_taste_preferences"}),
        }
        for service, (database, expected) in schemas.items():
            tables = set(self.sql(service, database,
                "SELECT tablename FROM pg_tables WHERE schemaname='public' "
                "AND tablename <> 'flyway_schema_history' ORDER BY tablename").splitlines())
            assert tables == expected, f"Unexpected {database} tables: {tables}"
            assert int(self.sql(service, database, "SELECT count(*) FROM flyway_schema_history WHERE success")) > 0
        print("[OK] only Gateway/Eureka ports published; three databases have isolated schemas", flush=True)

    def create_user(self, label):
        return self.request("POST", "/api/users", {
            "name": label, "email": f"{label.lower()}-{self.suffix}@example.com",
        }, expected=201).body

    def menu_payload(self, name=None, price=125.50):
        return {"name": name or f"Smoke {self.suffix}", "price": price,
                "ingredients": "Lime, soda", "tasteTagIds": [1, 2]}

    def create_visit(self, client_id):
        return self.request("POST", "/api/visits", {"clientId": client_id, "tableNumber": 4}, expected=201).body

    def business(self):
        self.request("POST", "/api/users", {"name": "", "email": "invalid"}, expected=400)
        user = self.create_user("Client")
        user_id = user["idUser"]
        assert user["role"] == "CLIENT"
        self.request("POST", "/api/users", {"name": "Duplicate", "email": user["email"]}, expected=409)
        updated = self.request("PUT", f"/api/users/{user_id}", {"name": "Renamed", "email": user["email"]}).body
        assert updated["name"] == "Renamed" and updated["role"] == "CLIENT"
        assert self.request("GET", f"/api/users/{user_id}").body["email"] == user["email"]
        self.request("GET", "/api/users/2147483647", expected=404)

        invalid_menu = self.menu_payload()
        invalid_menu["price"] = -1
        self.request("POST", "/api/menu", invalid_menu, expected=400)
        item = self.request("POST", "/api/menu", self.menu_payload(), expected=201).body
        item_id = item["idPosition"]
        assert self.request("GET", f"/api/menu/{item_id}").body["tasteTags"] == ["SOUR", "SWEET"]
        self.request("GET", "/api/menu/2147483647", expected=404)
        first = self.request("GET", "/api/menu?page=0")
        second = self.request("GET", "/api/menu?page=1").body
        assert len(first.body["content"]) == 10 and first.body["size"] == 10
        assert "totalElements" not in first.body and "x-total-count" not in first.headers
        assert not ({entry["idPosition"] for entry in first.body["content"]} &
                    {entry["idPosition"] for entry in second["content"]})
        self.request("GET", "/api/menu?page=-1", expected=400)

        visit = self.create_visit(user_id)
        visit_id = visit["idVisit"]
        assert visit["status"] == "OPEN"
        assert self.request("GET", f"/api/visits/{visit_id}").body["clientId"] == user_id
        assert self.request("PUT", f"/api/visits/{visit_id}", {"tableNumber": 9}).body["tableNumber"] == 9
        prefs = self.request("PUT", f"/api/visits/{visit_id}/preferences", {"tasteTagIds": [1, 2]}).body
        assert set(prefs["tastePreferences"]) == {"SWEET", "SOUR"}
        self.request("PUT", f"/api/visits/{visit_id}/preferences", {"tasteTagIds": [2147483647]}, expected=400)
        assert set(self.request("GET", f"/api/visits/{visit_id}").body["tastePreferences"]) == {"SWEET", "SOUR"}
        recommendations = self.request("GET", f"/api/visits/{visit_id}/recommendations").body
        assert 0 < len(recommendations) <= 50
        assert all(set(entry["tasteTags"]) & {"SWEET", "SOUR"} for entry in recommendations)

        # Fixtures make the page limit observable even on a fresh deployment.
        self.sql("visits-db", "visits", "INSERT INTO visits (id_user, table_number, status, closed_at) "
                 f"SELECT {int(user_id)}, n, 'CLOSED', CURRENT_TIMESTAMP FROM generate_series(1, 51) AS n")
        visits = self.request("GET", "/api/visits?size=100")
        assert len(visits.body) == 50 and int(visits.headers["x-total-count"]) >= 52
        self.request("GET", "/api/visits?page=-1", expected=400)

        # There is deliberately no role-management REST API in lab 1 or lab 2.
        staff = {}
        for role in ("WAITER", "WORKER"):
            staff[role] = int(self.sql("users-db", "users", "INSERT INTO users (name, email, role) "
                f"VALUES ('Smoke {role}', '{role.lower()}-{self.suffix}@example.com', '{role}') RETURNING id_user"))
        order_payload = {"visitId": visit_id, "waiterId": staff["WAITER"], "workerId": staff["WORKER"],
                         "items": [{"menuItemId": item_id, "quantity": 2}]}
        bad_order = dict(order_payload, waiterId=user_id)
        self.request("POST", "/api/orders", bad_order, expected=409)
        order = self.request("POST", "/api/orders", order_payload, expected=201).body
        assert order["totalAmount"] == Decimal("251.00") and order["items"][0]["unitPrice"] == Decimal("125.50")
        self.request("PUT", f"/api/menu/{item_id}", self.menu_payload(f"Renamed {self.suffix}", 999.99))
        saved = self.request("GET", f"/api/orders/{order['idOrder']}").body
        assert saved["totalAmount"] == Decimal("251.00")
        assert saved["items"][0]["unitPrice"] == Decimal("125.50")
        assert saved["items"][0]["name"] == f"Renamed {self.suffix}"
        for path in (f"/api/users/{user_id}", f"/api/users/{staff['WAITER']}", f"/api/users/{staff['WORKER']}",
                     f"/api/menu/{item_id}"):
            self.request("DELETE", path, expected=409)

        closed = self.request("POST", f"/api/visits/{visit_id}/close").body
        assert closed["status"] == "CLOSED" and closed["closedAt"] and closed["tastePreferences"] == []
        self.request("POST", "/api/orders", order_payload, expected=409)
        self.request("GET", f"/api/visits/{visit_id}/recommendations", expected=409)
        self.request("POST", f"/api/visits/{visit_id}/close", expected=409)

        free_user = self.create_user("Free")
        free_item = self.request("POST", "/api/menu", self.menu_payload(f"Free {self.suffix}"), expected=201).body
        for path in (f"/api/users/{free_user['idUser']}", f"/api/menu/{free_item['idPosition']}"):
            self.request("DELETE", path, expected=204)
            self.request("GET", path, expected=404)
            self.request("DELETE", path, expected=404)
        print("[OK] CRUD, validation, pagination, recommendations, order prices, closure and reference guards", flush=True)
        return user_id

    def failure_recovery(self, client_id):
        visit_id = self.create_visit(client_id)["idVisit"]
        self.request("PUT", f"/api/visits/{visit_id}/preferences", {"tasteTagIds": [1]})
        path = f"/api/visits/{visit_id}/recommendations"
        self.request("GET", path)
        try:
            self.compose("stop", "menu-service")
            self.eventually("menu-service outage produces HTTP 503", lambda: self.request("GET", path, expected=503), timeout=60)
        finally:
            self.compose("start", "menu-service")
        self.eventually("menu-service recovers and recommendations return HTTP 200", lambda: self.request("GET", path), timeout=240)
        self.eventually("all services reappear as UP", self.registry, timeout=120)
        self.request("POST", f"/api/visits/{visit_id}/close")
        print("[OK] outage/recovery verified; HTTP 503 alone does not prove the breaker is OPEN", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", default="lab2", help="Docker Compose project name (default: lab2)")
    parser.add_argument("--gateway", default="http://localhost:8080")
    parser.add_argument("--eureka", default="http://localhost:8761")
    parser.add_argument("--check-failure", action="store_true", help="briefly stop and restart this project's menu-service")
    args = parser.parse_args()
    test = SmokeTest(args.project, args.gateway, args.eureka)
    print(f"Checking Compose project {args.project}; persistent test records use suffix {test.suffix}", flush=True)
    test.infrastructure()
    client_id = test.business()
    if args.check_failure:
        test.failure_recovery(client_id)
    print("All requested smoke checks passed. Test visits, orders and their referenced records remain in this project's volumes.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, subprocess.TimeoutExpired, KeyError, TypeError, ValueError) as error:
        print(f"[FAIL] {error}", file=sys.stderr)
        raise SystemExit(1)
