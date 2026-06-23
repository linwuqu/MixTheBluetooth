import argparse
import json
import re
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse


ACCOUNT = {
    "id": 10001,
    "username": "bioai-dev-user",
    "phone": "18800000001",
    "avatarUrl": None,
    "role": "USER",
    "rootFileId": 0,
    "rootFileName": "root",
}

TOKEN = "static-token-job-64"

CGM_POINTS = [
    {"index": 0, "time": 0, "predicted": 5.21, "actual": 5.0},
    {"index": 1, "time": 60, "predicted": 6.18, "actual": 5.7},
    {"index": 2, "time": 120, "predicted": 6.85, "actual": 6.4},
    {"index": 3, "time": 180, "predicted": 7.43, "actual": 7.0},
    {"index": 4, "time": 240, "predicted": 8.10, "actual": 7.8},
    {"index": 5, "time": 300, "predicted": 9.02, "actual": 8.6},
]


def success_response(data, code=0, msg=""):
    return {
        "code": code,
        "success": True,
        "msg": msg,
        "data": data,
    }


def account_response():
    return success_response(ACCOUNT)


def cgm_upload_response():
    return success_response(
        {
            "datasetId": 640,
            "datasetStatus": "VALID",
            "rawAccountFileId": 64001,
            "rowCount": 6,
            "unitCount": 1,
            "jobId": 64,
            "jobNo": "static-job-64",
            "jobStatus": "SUCCESS",
            "resultId": 64001,
            "errorMsg": None,
            "parseWarnings": [],
        },
        code=200,
        msg="static CGM upload ok",
    )


def cgm_job_response():
    return success_response(
        {
            "jobId": 64,
            "jobNo": "static-job-64",
            "status": "SUCCESS",
            "datasetId": 640,
            "resultId": 64001,
            "errorMsg": None,
            "startedAt": "2026-06-01T10:00:00",
            "finishedAt": "2026-06-01T10:00:01",
        },
        code=200,
        msg="success",
    )


def cgm_result_info_response():
    return success_response(
        {
            "min": 4.12,
            "max": 9.87,
            "mean": 6.54,
            "std": 1.23,
            "tir": 83.3,
            "tirLow": 0.0,
            "tirHigh": 16.7,
        },
        code=200,
        msg="success",
    )


def cgm_result_curve_response():
    return success_response(
        {
            "resultId": 64001,
            "unitCount": 1,
            "units": [
                {
                    "unit": 1,
                    "unit_title": "static-job-64",
                    "point_count": len(CGM_POINTS),
                    "mard": 16.3,
                    "points": CGM_POINTS,
                }
            ],
        },
        code=200,
        msg="success",
    )


def file_upload_response(file_name="cgm-cache.txt"):
    return success_response(
        {
            "fileId": 64001,
            "fileName": file_name,
            "path": f"/static/{file_name}",
            "url": f"http://lan-static-backend/static/{file_name}",
        },
        code=200,
        msg="static file upload ok",
    )


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
            self.send_json(200, cgm_job_response())
        elif path.startswith("/api/cgm/v1/jobs/"):
            self.send_json(404, error_response(404, "static CGM job not found"))
        elif path == "/api/cgm/v1/predictions/64001/info":
            self.send_json(200, cgm_result_info_response())
        elif path == "/api/cgm/v1/predictions/64001/curve":
            self.send_json(200, cgm_result_curve_response())
        else:
            self.send_json(404, error_response(404, "not found"))

    def do_POST(self):
        path = urlparse(self.path).path
        body = self.read_body()
        file_name = self.extract_file_name(body)
        if path == "/api/account/v1/register":
            self.send_json(200, success_response(None), request_body_length=len(body))
        elif path == "/api/account/v1/login":
            self.send_json(200, success_response(TOKEN), request_body_length=len(body))
        elif path == "/api/cgm/v1/dataset/upload":
            self.send_json(200, cgm_upload_response(), request_body_length=len(body))
        elif path == "/api/file/v1/upload":
            self.send_json(200, file_upload_response(file_name), request_body_length=len(body))
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

    def extract_file_name(self, body):
        match = re.search(rb'filename="([^"]+)"', body)
        if match:
            return match.group(1).decode("utf-8", errors="replace")
        return "cgm-cache.txt"

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
