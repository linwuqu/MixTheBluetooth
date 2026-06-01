import argparse
import json
import re
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


def upload_response(file_name="cgm-cache.txt"):
    return {
        "code": 200,
        "success": True,
        "msg": "static upload ok",
        "data": {
            "jobId": 64,
            "fileName": file_name,
        },
    }


def file_upload_response(file_name="cgm-cache.txt"):
    return {
        "code": 200,
        "success": True,
        "msg": "static file upload ok",
        "data": {
            "fileId": 64001,
            "fileName": file_name,
            "path": f"/static/{file_name}",
            "url": f"http://lan-static-backend/static/{file_name}",
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
        file_name = self.extract_file_name(body)
        if path in ("/api/account/v1/register", "/api/account/v1/login"):
            self.send_json(200, account_response(), request_body_length=len(body))
        elif path == "/api/test/v1/upload":
            self.send_json(200, upload_response(file_name), request_body_length=len(body))
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
