import importlib.util
from pathlib import Path
import threading
import time
import unittest
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import urllib.request

spec = importlib.util.spec_from_file_location('session', Path(__file__).parents[1] / 'pleos_console_session.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ConsoleSessionTest(unittest.TestCase):
    def test_keeps_session_alive_and_closes_same_client(self):
        calls=[]
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                calls.append(self.path)
                self.send_response(200); self.end_headers(); self.wfile.write(b'{}')
            def log_message(self,*args):pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        original=urllib.request.urlopen
        def local(request,timeout):
            # Redirect only the test's HTTP transport; production remains loopback:8088.
            url=request.full_url.replace('127.0.0.1:8088','127.0.0.1:'+str(server.server_port))
            return original(urllib.request.Request(url,method=request.method),timeout=timeout)
        try:
            with patch.object(module.urllib.request,'urlopen',local):
                session=module.ConsoleSession().start()
                time.sleep(2.2)
                session.close();session.close()
            self.assertGreaterEqual(len(calls),4)
            self.assertTrue(calls[0].startswith('/browser/session?client=cabinos-verification-'))
            self.assertTrue(all(path==calls[0] for path in calls[:-1]))
            self.assertEqual(calls[-1],calls[0]+'&closing=1')
            self.assertFalse(session.thread.is_alive())
        finally:server.shutdown();server.server_close()

    def test_activation_failure_is_not_silenced(self):
        with patch.object(module.urllib.request,'urlopen',side_effect=OSError('HTTP unavailable')):
            with self.assertRaisesRegex(OSError,'HTTP unavailable'):
                module.ConsoleSession().start()

if __name__=='__main__':unittest.main()
