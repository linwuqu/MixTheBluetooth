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
