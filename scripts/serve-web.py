#!/usr/bin/env python3
"""Internal preview server with byte ranges, matching static hosts' MP4 seeking."""
import argparse
import functools
import io
import re
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

class Handler(SimpleHTTPRequestHandler):
    def send_head(self):
        target = Path(self.translate_path(self.path))
        value = self.headers.get('Range')
        if not value or not target.is_file():
            return super().send_head()
        match = re.fullmatch(r'bytes=(\d*)-(\d*)', value)
        size = target.stat().st_size
        if not match or not any(match.groups()):
            self.send_error(400, 'Invalid byte range')
            return None
        first, last = match.groups()
        start = int(first) if first else max(0, size-int(last))
        end = min(size-1, int(last)) if first and last else size-1
        if start >= size or start > end:
            self.send_response(416)
            self.send_header('Content-Range', f'bytes */{size}')
            self.send_header('Content-Length', '0')
            self.end_headers()
            return None
        with target.open('rb') as stream:
            stream.seek(start)
            data = stream.read(end-start+1)
        self.send_response(206)
        self.send_header('Content-Type', self.guess_type(str(target)))
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        return io.BytesIO(data)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', default='build/site')
    parser.add_argument('--port', type=int, default=4173)
    args = parser.parse_args()
    server = ThreadingHTTPServer(('127.0.0.1', args.port), functools.partial(Handler, directory=args.directory))
    print(f'Internal preview server ready on port {args.port}', flush=True)
    server.serve_forever()
