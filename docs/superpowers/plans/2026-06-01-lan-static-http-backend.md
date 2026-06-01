# LAN Static HTTP Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local LAN static HTTP backend so Android can keep using real Retrofit HTTP packets while receiving deterministic account, upload, and CGM responses.

**Architecture:** Keep Android `API_ENV=lan` on the existing `DefaultAuthService` and `DefaultCgmService` path. Add a standalone Python stdlib HTTP server under `tools/` that listens on `0.0.0.0:8080`, implements the app's current backend endpoints, consumes real request bodies, and returns fixed JSON. Keep app-internal `API_ENV=static` as an offline fallback only.

**Tech Stack:** Android Java, Retrofit 2, OkHttp, Python 3 standard library, `unittest`, PowerShell, adb, Gradle.

---

## Scope Check

This plan is focused on the LAN static backend and verification path. It does not change Android UI, does not fake Bluetooth packets, and does not replace the existing Retrofit services. The only production Android change expected is documentation/config verification unless implementation reveals an actual mismatch.

## File Map

- Create: `tools/lan_static_backend.py`
  - Standalone local HTTP server.
  - Provides deterministic responses for account, file upload, CGM test upload, and CGM job result endpoints.
  - Logs each request method, path, response status, and request body length.
- Create: `tools/test_lan_static_backend.py`
  - Python stdlib `unittest` smoke/contract tests for the local backend.
  - Starts the server on `127.0.0.1:0` and verifies JSON contracts.
- Modify: `docs/superpowers/specs/2026-06-01-lan-static-http-loop-design.md`
  - Mark the previous app-internal-static-first design as superseded for realistic LAN testing.
- Modify: `docs/superpowers/plans/2026-06-01-lan-static-http-loop.md`
  - Mark the previous app-internal-static-first implementation plan as superseded for realistic LAN testing.
- No planned Android source edits:
  - `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java` already routes `lan`, `dev`, and `prod` to real HTTP services.
  - `app/config/env.lan.properties` already points to `http://124.16.68.18:8080/`.

---

### Task 1: Add Failing Backend Contract Tests

**Files:**
- Create: `tools/test_lan_static_backend.py`

- [ ] **Step 1: Create the backend test file**

Create `tools/test_lan_static_backend.py`:

```python
import json
import threading
import unittest
import urllib.error
import urllib.request

from lan_static_backend import create_server


class LanStaticBackendTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = create_server("127.0.0.1", 0)
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.thread.join(timeout=3)
        cls.server.server_close()

    def request_json(self, method, path, body=None, headers=None):
        data = None
        request_headers = dict(headers or {})
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            request_headers.setdefault("Content-Type", "application/json")
        request = urllib.request.Request(
            f"http://127.0.0.1:{self.port}{path}",
            data=data,
            headers=request_headers,
            method=method,
        )
        with urllib.request.urlopen(request, timeout=3) as response:
            return response.status, json.loads(response.read().decode("utf-8"))

    def request_multipart(self, path):
        boundary = "----bioai-test-boundary"
        body = (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="file"; filename="cgm-cache.txt"\r\n'
            "Content-Type: text/plain\r\n"
            "\r\n"
            "Start Playback\nstatic payload\nPlayback all done\n"
            f"\r\n--{boundary}--\r\n"
        ).encode("utf-8")
        request = urllib.request.Request(
            f"http://127.0.0.1:{self.port}{path}",
            data=body,
            headers={
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "Content-Length": str(len(body)),
            },
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=3) as response:
            return response.status, json.loads(response.read().decode("utf-8"))

    def test_health_endpoint_reports_static_backend(self):
        status, payload = self.request_json("GET", "/healthz")

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual("lan-static-backend", payload["data"]["service"])
        self.assertEqual(64, payload["data"]["jobId"])

    def test_account_endpoints_return_success_account(self):
        for method, path, body in [
            ("POST", "/api/account/v1/register", {
                "username": "bioai-dev-user",
                "phone": "18800000001",
                "password": "123456",
            }),
            ("POST", "/api/account/v1/login", {
                "phone": "18800000001",
                "password": "123456",
            }),
            ("GET", "/api/account/v1/detail", None),
        ]:
            status, payload = self.request_json(method, path, body)

            self.assertEqual(200, status)
            self.assertTrue(payload["success"])
            self.assertEqual(0, payload["code"])
            self.assertEqual(10001, payload["data"]["accountId"])
            self.assertEqual("bioai-dev-user", payload["data"]["username"])
            self.assertEqual("18800000001", payload["data"]["phone"])
            self.assertEqual("static-token-job-64", payload["data"]["token"])

    def test_cgm_upload_returns_static_job_id(self):
        status, payload = self.request_multipart("/api/test/v1/upload")

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(200, payload["code"])
        self.assertEqual(64, payload["data"]["jobId"])

    def test_file_upload_returns_file_metadata(self):
        status, payload = self.request_multipart("/api/file/v1/upload")

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(200, payload["code"])
        self.assertEqual(64001, payload["data"]["fileId"])
        self.assertEqual("cgm-cache.txt", payload["data"]["fileName"])

    def test_cgm_job_64_returns_generated_points(self):
        status, payload = self.request_json("GET", "/api/cgm/v1/jobs/64")

        self.assertEqual(200, status)
        self.assertEqual(200, payload["code"])
        self.assertEqual("success", payload["message"])
        self.assertEqual(64, payload["data"]["jobId"])
        self.assertEqual("GENERATED", payload["data"]["status"])
        self.assertEqual(6, payload["data"]["pointCount"])
        self.assertEqual(1, payload["data"]["unitCount"])
        points = payload["data"]["summaryJson"]["units"][0]["points"]
        self.assertEqual(6, len(points))
        self.assertEqual(0, points[0]["index"])
        self.assertEqual(5.21, points[0]["predicted"])

    def test_unknown_path_returns_json_404(self):
        request = urllib.request.Request(
            f"http://127.0.0.1:{self.port}/missing",
            method="GET",
        )

        with self.assertRaises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(request, timeout=3)

        self.assertEqual(404, error.exception.code)
        payload = json.loads(error.exception.read().decode("utf-8"))
        self.assertFalse(payload["success"])
        self.assertEqual(404, payload["code"])
        self.assertEqual("not found", payload["msg"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
```

- [ ] **Step 2: Run the test to verify it fails before implementation**

Run:

```powershell
python tools/test_lan_static_backend.py
```

Expected: FAIL before Task 2 with an import error like:

```text
ModuleNotFoundError: No module named 'lan_static_backend'
```

- [ ] **Step 3: Commit the failing test**

```powershell
git add tools/test_lan_static_backend.py
git commit -m "test: define lan static backend contract"
```

---

### Task 2: Implement The LAN Static Backend

**Files:**
- Create: `tools/lan_static_backend.py`
- Test: `tools/test_lan_static_backend.py`

- [ ] **Step 1: Create the backend script**

Create `tools/lan_static_backend.py`:

```python
import argparse
import json
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse


ACCOUNT = {
    "accountId": 10001,
    "username": "bioai-dev-user",
    "phone": "18800000001",
    "avatarUrl": None,
    "token": "static-token-job-64",
}

CGM_JOB_64 = {
    "code": 200,
    "message": "success",
    "data": {
        "resultId": 64001,
        "jobId": 64,
        "datasetId": 640,
        "pointCount": 6,
        "unitCount": 1,
        "predictionMin": 4.12,
        "predictionMax": 9.87,
        "predictionMean": 6.54,
        "predictionStd": 1.23,
        "avgMard": 16.3,
        "mardStd": None,
        "summaryJson": {
            "avg_mard": 16.3,
            "mard_std": None,
            "point_count": 6,
            "unit_count": 1,
            "prediction_stats": {
                "min": 4.12,
                "max": 9.87,
                "mean": 6.54,
                "std": 1.23,
            },
            "units": [
                {
                    "unit": 1,
                    "unit_title": "static-job-64",
                    "point_count": 6,
                    "mard": 16.3,
                    "points": [
                        {"index": 0, "time": 0, "predicted": 5.21, "actual": 5.0},
                        {"index": 1, "time": 60, "predicted": 6.18, "actual": 5.7},
                        {"index": 2, "time": 120, "predicted": 6.85, "actual": 6.4},
                        {"index": 3, "time": 180, "predicted": 7.43, "actual": 7.0},
                        {"index": 4, "time": 240, "predicted": 8.10, "actual": 7.8},
                        {"index": 5, "time": 300, "predicted": 9.02, "actual": 8.6},
                    ],
                }
            ],
        },
        "status": "GENERATED",
        "gmtCreate": "2026-06-01T10:00:00",
    },
}


def account_response():
    return {
        "code": 0,
        "success": True,
        "msg": "",
        "data": ACCOUNT,
    }


def upload_response():
    return {
        "code": 200,
        "success": True,
        "msg": "static upload ok",
        "data": {
            "jobId": 64,
        },
    }


def file_upload_response():
    return {
        "code": 200,
        "success": True,
        "msg": "static file upload ok",
        "data": {
            "fileId": 64001,
            "fileName": "cgm-cache.txt",
            "path": "/static/cgm-cache.txt",
            "url": "http://lan-static-backend/static/cgm-cache.txt",
        },
    }


def health_response():
    return {
        "code": 0,
        "success": True,
        "msg": "ok",
        "data": {
            "service": "lan-static-backend",
            "jobId": 64,
        },
    }


def error_response(status, message):
    return {
        "code": status,
        "success": False,
        "msg": message,
        "data": None,
    }


class LanStaticBackendHandler(BaseHTTPRequestHandler):
    server_version = "BioAIStaticBackend/1.0"

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/healthz":
            self.send_json(200, health_response())
        elif path == "/api/account/v1/detail":
            self.send_json(200, account_response())
        elif path == "/api/cgm/v1/jobs/64":
            self.send_json(200, CGM_JOB_64)
        elif path.startswith("/api/cgm/v1/jobs/"):
            self.send_json(404, {
                "code": 404,
                "message": "static CGM job not found",
                "data": None,
            })
        else:
            self.send_json(404, error_response(404, "not found"))

    def do_POST(self):
        path = urlparse(self.path).path
        body = self.read_body()
        if path in ("/api/account/v1/register", "/api/account/v1/login"):
            self.send_json(200, account_response(), request_body_length=len(body))
        elif path == "/api/test/v1/upload":
            self.send_json(200, upload_response(), request_body_length=len(body))
        elif path == "/api/file/v1/upload":
            self.send_json(200, file_upload_response(), request_body_length=len(body))
        else:
            self.send_json(404, error_response(404, "not found"), request_body_length=len(body))

    def read_body(self):
        raw_length = self.headers.get("Content-Length", "0")
        try:
            length = int(raw_length)
        except ValueError:
            length = 0
        if length <= 0:
            return b""
        return self.rfile.read(length)

    def send_json(self, status, payload, request_body_length=0):
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        self.log_request_line(status, request_body_length)

    def log_request_line(self, status, request_body_length):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        content_type = self.headers.get("Content-Type", "")
        print(
            f"{timestamp} {self.command} {self.path} -> {status} "
            f"requestBytes={request_body_length} contentType={content_type}",
            flush=True,
        )

    def log_message(self, format, *args):
        return


def create_server(host, port):
    return ThreadingHTTPServer((host, port), LanStaticBackendHandler)


def parse_args():
    parser = argparse.ArgumentParser(description="BioAI LAN static HTTP backend")
    parser.add_argument("--host", default="0.0.0.0", help="Bind host, default: 0.0.0.0")
    parser.add_argument("--port", default=8080, type=int, help="Bind port, default: 8080")
    return parser.parse_args()


def main():
    args = parse_args()
    server = create_server(args.host, args.port)
    print(
        f"LAN static backend listening on http://{args.host}:{args.port}",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Stopping LAN static backend", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run the backend contract tests**

Run:

```powershell
python tools/test_lan_static_backend.py
```

Expected:

```text
Ran 6 tests

OK
```

- [ ] **Step 3: Run a local manual health check**

In one terminal:

```powershell
python tools/lan_static_backend.py --host 127.0.0.1 --port 8080
```

In another terminal:

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8080/healthz"
```

Expected response includes:

```text
success : True
data    : @{service=lan-static-backend; jobId=64}
```

- [ ] **Step 4: Commit the backend implementation**

```powershell
git add tools/lan_static_backend.py tools/test_lan_static_backend.py
git commit -m "feat: add lan static http backend"
```

---

### Task 3: Mark The Older Static-Loop Docs As Secondary

**Files:**
- Modify: `docs/superpowers/specs/2026-06-01-lan-static-http-loop-design.md`
- Modify: `docs/superpowers/plans/2026-06-01-lan-static-http-loop.md`

- [ ] **Step 1: Add a superseded notice to the old design**

In `docs/superpowers/specs/2026-06-01-lan-static-http-loop-design.md`, add this directly after the existing date line:

```markdown
Status: Superseded for realistic LAN testing by `docs/superpowers/specs/2026-06-01-lan-static-http-backend-design.md`. The app-internal static loop remains useful only as an offline fallback.
```

- [ ] **Step 2: Add a superseded notice to the old plan**

In `docs/superpowers/plans/2026-06-01-lan-static-http-loop.md`, add this directly after the top-level heading:

```markdown
> **Status:** Superseded for realistic LAN testing by `docs/superpowers/plans/2026-06-01-lan-static-http-backend.md`. Use the LAN static HTTP backend plan when the goal is to exercise real Android HTTP packets.
```

- [ ] **Step 3: Verify the notices are present**

Run:

```powershell
rg -n "Superseded for realistic LAN testing|LAN static HTTP backend" docs/superpowers/specs/2026-06-01-lan-static-http-loop-design.md docs/superpowers/plans/2026-06-01-lan-static-http-loop.md docs/superpowers/specs/2026-06-01-lan-static-http-backend-design.md
```

Expected: matches in all three files.

- [ ] **Step 4: Commit the documentation alignment**

```powershell
git add docs/superpowers/specs/2026-06-01-lan-static-http-loop-design.md docs/superpowers/plans/2026-06-01-lan-static-http-loop.md
git commit -m "docs: mark app static loop as fallback"
```

---

### Task 4: Verify LAN Backend And Android Real HTTP Path

**Files:**
- No planned source edits.

- [ ] **Step 1: Start the LAN backend on all interfaces**

Run:

```powershell
python tools/lan_static_backend.py --host 0.0.0.0 --port 8080
```

Expected:

```text
LAN static backend listening on http://0.0.0.0:8080
```

- [ ] **Step 2: Verify Windows can reach the backend through the configured LAN URL**

Run in another terminal:

```powershell
Invoke-RestMethod -Uri "http://124.16.68.18:8080/healthz"
```

Expected response includes:

```text
success : True
data    : @{service=lan-static-backend; jobId=64}
```

- [ ] **Step 3: Verify the Android device can reach the backend port**

Run:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell "printf 'GET /healthz HTTP/1.1\r\nHost: 124.16.68.18\r\nConnection: close\r\n\r\n' | nc -w 3 124.16.68.18 8080"
```

Expected output contains:

```text
HTTP/1.0 200 OK
Content-Type: application/json; charset=utf-8
```

and JSON containing:

```json
{"code":0,"success":true,"msg":"ok","data":{"service":"lan-static-backend","jobId":64}}
```

- [ ] **Step 4: Verify LAN env still points at real HTTP**

Run:

```powershell
Get-Content -LiteralPath "app\config\env.lan.properties"
```

Expected:

```properties
API_ENV=lan
API_BASE_URL=http://124.16.68.18:8080/
```

- [ ] **Step 5: Run backend tests and Android contract tests**

Run:

```powershell
python tools/test_lan_static_backend.py
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.auth.DefaultAuthServiceTest" --tests "com.hc.mixthebluetooth.impl.cgm.DefaultCgmServiceTest" --tests "com.hc.mixthebluetooth.remote.CgmJobRespParsingTest" --tests "com.hc.mixthebluetooth.remote.ServerEndpointsContractTest"
```

Expected:

```text
python: Ran 6 tests, OK
gradle: BUILD SUCCESSFUL
```

- [ ] **Step 6: Build the LAN APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=lan
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Manual app acceptance with real HTTP packets**

Keep the backend running on `0.0.0.0:8080`, install/run the LAN APK, and collect:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -v time -s BioAI.Http
```

Expected Android log milestones:

```text
AppApiBootstrap API ENV config
env=lan
remote=RetrofitServer(http://124.16.68.18:8080/)
networkEnabled=true
DefaultAuthService API POST /api/account/v1/register request
DefaultAuthService API POST /api/account/v1/register response
DefaultAuthService API POST /api/account/v1/login response
DefaultCgmService API POST /api/test/v1/upload response
DefaultCgmService API GET /api/cgm/v1/jobs/64 response attempt=1
jobId=64
status=GENERATED
```

Expected backend terminal milestones:

```text
POST /api/account/v1/register -> 200
POST /api/account/v1/login -> 200
POST /api/test/v1/upload -> 200
GET /api/cgm/v1/jobs/64 -> 200
```

- [ ] **Step 8: Commit verification-only fixes if needed**

If verification required source or doc fixes, commit them:

```powershell
git add tools docs app/config app/src/test app/src/main
git commit -m "fix: verify lan static backend path"
```

If no files changed during verification, do not create an empty commit.

---

## Self-Review

- Spec coverage: `API_ENV=lan` remains real HTTP, the backend listens on `0.0.0.0:8080`, Android uses concrete `124.16.68.18:8080`, account/upload/CGM endpoints are covered, and `API_ENV=static` is documented as fallback only.
- Placeholder scan: no task contains unresolved placeholder wording, and every code-changing step includes concrete content.
- Type consistency: JSON fields match `ServerResponse`, `ServerModels.AccountResp`, `ServerModels.FileResp`, and `ServerModels.CgmJobResp`.
- Testing: Python backend contract tests run independently; Android tests continue to protect Retrofit endpoint contracts; manual adb/nc check proves device-to-backend reachability.
