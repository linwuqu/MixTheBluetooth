# LAN Static HTTP Backend Design

Date: 2026-06-01

Status: Supersedes the app-internal-static-first acceptance path for realistic LAN testing.

## 1. Background

The app already has two distinct ideas that both sound like "static":

1. An app-internal static loop, where Android services return fixed data without making HTTP calls.
2. A LAN static HTTP backend, where Android still uses Retrofit and OkHttp, but the backend on the local machine returns fixed responses.

For realistic integration testing, the second model is the important one. It exercises the real network path, multipart upload, request timeouts, host resolution, and error handling, while still keeping the backend behavior deterministic.

This spec defines the LAN static HTTP backend as the primary realistic test path. The app-internal static loop may remain as an offline fallback, but it is no longer the main acceptance route.

## 2. Goals

- Make `API_ENV=lan` use real HTTP requests from Android to a LAN-reachable backend.
- Keep backend responses deterministic and easy to swap later.
- Preserve the current endpoint shapes and `BioAI.Http` logging style.
- Allow later replacement of the static backend with a real backend by changing only `API_BASE_URL`.
- Keep the app-internal static loop available only as an optional offline fallback.

## 3. Non-Goals

- Do not add a new app screen or debug-only flow.
- Do not fake Bluetooth traffic.
- Do not require `adb reverse`.
- Do not make Android call `0.0.0.0`.
- Do not introduce a second Android networking stack.

## 4. Recommended Architecture

### Modes

| Mode | Android path | Backend target | Purpose |
|---|---|---|---|
| `lan` | `DefaultAuthService` / `DefaultCgmService` | Local static HTTP backend on LAN | Primary realistic integration path |
| `dev` / `prod` | `DefaultAuthService` / `DefaultCgmService` | Real backend | Future real deployment |
| `static` | `StaticAuthService` / `StaticCgmService` | No HTTP backend | Offline fallback only |

### Responsibility Split

- Android keeps the real Retrofit services for LAN and future backend work.
- The LAN backend is a small local HTTP server that returns fixed JSON.
- The Android app does not know whether the LAN backend is static or real.
- The backend host IP is the only thing that changes for new LAN machines.

## 5. Network Model

The backend listens on:

```text
0.0.0.0:8080
```

Android connects to a concrete LAN address, for example:

```text
http://124.16.68.18:8080/
```

Expected LAN env config:

```properties
API_ENV=lan
API_BASE_URL=http://124.16.68.18:8080/
```

`0.0.0.0` is only a listening address. It must never appear in Android `API_BASE_URL`.

If the backend machine changes, only `app/config/env.lan.properties` should change.

## 6. Backend Contract

The LAN static backend must implement the same paths the app already expects:

```text
POST /api/account/v1/register
POST /api/account/v1/login
GET  /api/account/v1/detail
POST /api/file/v1/upload
POST /api/test/v1/upload
GET  /api/cgm/v1/jobs/64
```

### Account responses

Register, login, and detail should return the same fixture user every time.

Suggested fixture values:

- `accountId = 10001`
- `username = bioai-dev-user`
- `phone = 18800000001`
- `token = static-token-job-64`

### Upload response

The upload endpoint should accept a real multipart request, consume the body, and return a fixed job id:

```json
{
  "code": 200,
  "success": true,
  "msg": "static upload ok",
  "data": {
    "jobId": 64
  }
}
```

The server does not need to compute CGM output from the uploaded file. It only needs to prove that the upload path is real.

### CGM response

`GET /api/cgm/v1/jobs/64` should return a fixed generated result with renderable summary data:

- `code = 200`
- `message = success`
- `data.jobId = 64`
- `data.status = GENERATED`
- `data.pointCount > 0`
- `data.unitCount >= 1`
- `data.summaryJson.units[].points[]` present

The response shape should match the existing `ServerModels.CgmJobResp` parser.

## 7. Android App Behavior

### Environment selection

The existing `API_ENV` mechanism remains the switch:

- `lan` uses the real Retrofit path to the LAN backend.
- `dev` and `prod` continue to use the same real Retrofit path.
- `static` remains a direct app-internal fallback.

### AppApiBootstrap

`AppApiBootstrap` should not add a separate LAN transport branch. The current behavior is already the correct shape:

- `lan`, `dev`, `prod` -> real HTTP services
- `static` -> internal static services

That keeps the Android code simple and makes the LAN backend the only thing that changes for realistic testing.

### Logging

The app should keep logging through `BioAI.Http` with the same request/response/failure style already used by the services.

Expected visible milestones:

- app startup config
- register request/response
- login request/response
- upload request/response
- CGM poll request/response
- render result

## 8. Backend Implementation Shape

The LAN backend should be a small standalone tool, not part of the Android module.

Recommended path:

```text
tools/lan_static_backend.py
```

Implementation constraints:

- Python 3 standard library is enough.
- No heavy framework is required.
- Multipart upload only needs to be accepted, not deeply parsed.
- The server should log request method, path, status, and important headers or sizes.

Suggested startup command:

```powershell
python tools/lan_static_backend.py --host 0.0.0.0 --port 8080
```

## 9. Error Handling

- If the backend is not listening, Android should surface the existing network failure path.
- If the backend returns non-200 or malformed JSON, the app should fail through the existing service error handling.
- If upload succeeds without a `jobId`, treat it as a controlled error.
- If CGM data is missing required fields, treat it as a controlled parsing or contract error.
- `static` fallback must not silently replace `lan` behavior.

## 10. Test Strategy

### Android-side tests

- Keep contract tests for `ServerEndpoints`.
- Keep parser tests for `ServerModels.CgmJobResp`.
- Keep service tests for `DefaultAuthService` and `DefaultCgmService` using `MockWebServer`.

These tests protect the endpoint contract independently of the local backend script.

### Backend smoke test

Add a simple smoke test or script check for the LAN backend that verifies:

- register/login/detail all respond
- multipart upload returns `jobId=64`
- `GET /api/cgm/v1/jobs/64` returns generated data

### Manual acceptance

1. Start the local backend on `0.0.0.0:8080`.
2. Set `API_ENV=lan`.
3. Build the Android app.
4. Run the real app flow on the device.
5. Confirm the app sends real HTTP requests and receives fixed LAN responses.

## 11. Success Criteria

- `API_ENV=lan` uses real HTTP packets to a local LAN backend.
- The backend returns deterministic account, upload, and CGM responses.
- The CGM response is renderable by the current UI.
- Changing `API_BASE_URL` is enough to move from local static backend to real backend later.
- The app-internal static loop remains optional and non-primary.

## 12. Open Follow-Ups

- Decide whether to keep `API_ENV=static` documented as a manual fallback or hide it from routine usage.
- Decide whether the LAN backend should also expose a small health endpoint for quick startup checks.
- Decide whether to store fixture JSON inline in the script or as separate files.

## 13. Self-Review

- No Android target URL uses `0.0.0.0`.
- The LAN path is real HTTP, not an in-app mock return.
- The local backend is deterministic but still exercises the network stack.
- The spec distinguishes LAN static backend from app-internal static services.
- The scope stays focused on integration realism, not UI changes or Bluetooth fakes.
