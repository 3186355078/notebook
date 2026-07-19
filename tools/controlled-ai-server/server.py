"""Local Stage 9 OpenAI-compatible test server.

The server intentionally records only scenario names, status codes, request counts,
and timestamps. It never logs authorization headers or request prompts.
"""

from __future__ import annotations

import argparse
import json
import threading
import time
from collections import defaultdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


COUNTS: dict[str, int] = defaultdict(int)
COUNTS_LOCK = threading.Lock()
MAX_REQUEST_BYTES = 2 * 1024 * 1024


def summary_content() -> str:
    return json.dumps(
        {
            "title": "Stage 9 controlled summary",
            "overview": "Controlled non-sensitive device test result.",
            "completedItems": [],
            "inProgressItems": [],
            "problemsAndSolutions": [],
            "keyDecisions": [],
            "metrics": [],
            "unfinishedItems": [],
            "nextActions": [],
            "risks": [],
            "highlights": [],
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )


class ControlledAiHandler(BaseHTTPRequestHandler):
    server_version = "WorkLogControlledAI/1"

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path != "/__status":
            self.send_error(404)
            return
        with COUNTS_LOCK:
            payload = json.dumps(dict(COUNTS), sort_keys=True).encode("utf-8")
        self._write(200, payload)

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path == "/__reset":
            with COUNTS_LOCK:
                COUNTS.clear()
            self._write(204, b"")
            return
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > MAX_REQUEST_BYTES:
            self._write(400, b'{"error":"invalid request"}')
            return
        try:
            request = json.loads(self.rfile.read(length))
            scenario = str(request.get("model", "test-success"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._write(400, b'{"error":"invalid request"}')
            return

        with COUNTS_LOCK:
            COUNTS[scenario] += 1
            attempt = COUNTS[scenario]
        status, payload = self._response_for(scenario, attempt)
        print(
            json.dumps(
                {
                    "scenario": scenario,
                    "status": status,
                    "requestCount": attempt,
                    "time": int(time.time()),
                },
                sort_keys=True,
            ),
            flush=True,
        )
        try:
            self._write(status, payload)
        except (BrokenPipeError, ConnectionResetError):
            # Expected when the device-side timeout cancels the request.
            return

    def _response_for(self, scenario: str, attempt: int) -> tuple[int, bytes]:
        if scenario == "test-401":
            return 401, b'{"error":{"message":"controlled unauthorized"}}'
        if scenario == "test-403":
            return 403, b'{"error":{"message":"controlled forbidden"}}'
        if scenario == "test-model-not-found":
            return 404, b'{"error":{"message":"controlled missing model"}}'
        if scenario in {"test-429", "test-429-recover"} and (
            scenario == "test-429" or attempt == 1
        ):
            return 429, b'{"error":{"message":"controlled rate limit"}}'
        if scenario in {"test-500", "test-500-recover"} and (
            scenario == "test-500" or attempt == 1
        ):
            return 500, b'{"error":{"message":"controlled server error"}}'
        if scenario in {"test-timeout", "test-timeout-recover"} and (
            scenario == "test-timeout" or attempt == 1
        ):
            time.sleep(12)
        if scenario in {"test-invalid-json", "test-repair-invalid-json"} and (
            scenario == "test-invalid-json" or attempt == 1
        ):
            return 200, self._success_payload("{")
        return 200, self._success_payload(summary_content())

    @staticmethod
    def _success_payload(content: str) -> bytes:
        return json.dumps(
            {
                "model": "stage9-controlled",
                "choices": [{"message": {"content": content}}],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ).encode("utf-8")

    def _write(self, status: int, payload: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if payload:
            self.wfile.write(payload)

    def log_message(self, _format: str, *_args: object) -> None:
        return


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18080)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), ControlledAiHandler)
    print(json.dumps({"listening": f"{args.host}:{args.port}"}), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
