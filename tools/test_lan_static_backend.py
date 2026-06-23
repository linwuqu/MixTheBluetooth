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

    def test_register_returns_success_without_data(self):
        status, payload = self.request_json(
            "POST",
            "/api/account/v1/register",
            {
                "username": "bioai-dev-user",
                "phone": "18800000001",
                "password": "123456",
            },
        )

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(0, payload["code"])
        self.assertIsNone(payload["data"])

    def test_login_returns_token_string(self):
        status, payload = self.request_json(
            "POST",
            "/api/account/v1/login",
            {
                "phone": "18800000001",
                "password": "123456",
            },
        )

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(0, payload["code"])
        self.assertEqual("static-token-job-64", payload["data"])

    def test_account_detail_returns_current_account_shape(self):
        status, payload = self.request_json("GET", "/api/account/v1/detail")

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(0, payload["code"])
        self.assertEqual(10001, payload["data"]["id"])
        self.assertEqual("bioai-dev-user", payload["data"]["username"])
        self.assertEqual("18800000001", payload["data"]["phone"])
        self.assertNotIn("token", payload["data"])

    def test_cgm_dataset_upload_returns_valid_dataset_and_job(self):
        status, payload = self.request_multipart("/api/cgm/v1/dataset/upload")

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(200, payload["code"])
        self.assertEqual("VALID", payload["data"]["datasetStatus"])
        self.assertEqual(64, payload["data"]["jobId"])

    def test_file_upload_returns_file_metadata(self):
        status, payload = self.request_multipart("/api/file/v1/upload")

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(200, payload["code"])
        self.assertEqual(64001, payload["data"]["fileId"])
        self.assertEqual("cgm-cache.txt", payload["data"]["fileName"])

    def test_cgm_job_64_returns_successful_result_reference(self):
        status, payload = self.request_json("GET", "/api/cgm/v1/jobs/64")

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(200, payload["code"])
        self.assertEqual(64, payload["data"]["jobId"])
        self.assertEqual("SUCCESS", payload["data"]["status"])
        self.assertEqual(64001, payload["data"]["resultId"])

    def test_cgm_result_info_returns_summary_fields(self):
        status, payload = self.request_json(
            "GET", "/api/cgm/v1/predictions/64001/info?unit=1"
        )

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(200, payload["code"])
        self.assertEqual(4.12, payload["data"]["min"])
        self.assertEqual(9.87, payload["data"]["max"])
        self.assertEqual(6.54, payload["data"]["mean"])

    def test_cgm_result_curve_returns_units_and_points(self):
        status, payload = self.request_json(
            "GET", "/api/cgm/v1/predictions/64001/curve?unit=1"
        )

        self.assertEqual(200, status)
        self.assertTrue(payload["success"])
        self.assertEqual(200, payload["code"])
        self.assertEqual(64001, payload["data"]["resultId"])
        self.assertEqual(1, payload["data"]["unitCount"])
        points = payload["data"]["units"][0]["points"]
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
