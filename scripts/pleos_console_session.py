"""Keep the official Console browser session alive during automated verification.

Matches the JavaScript served by Pleos 2.1.0 at http://localhost:8088/:
POST /browser/session?client=<id> every second, closing=1 on exit.
No private activation endpoint, root access, or image modification is used.
"""
import atexit
import threading
import time
import urllib.parse
import urllib.request
import uuid


class ConsoleSession:
    def __init__(self):
        self.url = 'http://127.0.0.1:8088/browser/session?' + urllib.parse.urlencode(
            {'client': 'cabinos-verification-' + uuid.uuid4().hex})
        self.stop = threading.Event()
        self.thread = None
        self.last_error = None

    def heartbeat(self, closing=False):
        request = urllib.request.Request(self.url + ('&closing=1' if closing else ''), method='POST')
        with urllib.request.urlopen(request, timeout=5) as response:
            response.read()
        self.last_error = None

    def start(self):
        # Fail visibly if activation itself is rejected.
        self.heartbeat()
        def maintain():
            while not self.stop.wait(1):
                try:
                    self.heartbeat()
                except OSError as error:
                    self.last_error = str(error)
        self.thread = threading.Thread(target=maintain, daemon=True)
        self.thread.start()
        atexit.register(self.close)
        return self

    def close(self):
        if self.stop.is_set():
            return
        self.stop.set()
        if self.thread:
            self.thread.join(timeout=6)
        try:
            self.heartbeat(closing=True)
        except OSError:
            pass
