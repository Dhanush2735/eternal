from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
import json, socket, urllib.request, urllib.error
from pathlib import Path

ROOT = Path('/home/user/eternal')

class MockGateway(BaseHTTPRequestHandler):
    seen = []
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        data = json.loads(body.decode('utf-8'))
        MockGateway.seen.append((self.path, dict(self.headers), data))
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps({'reply': 'mock gateway ok'}).encode('utf-8'))
    def log_message(self, fmt, *args):
        pass

# Find free local port
sock = socket.socket(); sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]; sock.close()
server = HTTPServer(('127.0.0.1', port), MockGateway)
thread = Thread(target=server.serve_forever, daemon=True)
thread.start()

results = []
def check(name, ok, detail):
    results.append((name, ok, detail))
    print(('PASS' if ok else 'FAIL') + ' - ' + name + ': ' + detail)

# 1. Confirm there is no generated Forge server config in this workspace.
configs = list(ROOT.rglob('*eternalengine-server.toml'))
check('no runtime server config in workspace', len(configs) == 0, f'found={configs}')

# 2. Confirm default source config leaves endpoint blank.
config_src = (ROOT/'src/main/java/com/eternalengine/config/EternalEngineConfig.java').read_text()
check('source default remoteBrainEnabled false', 'define("remoteBrainEnabled", false)' in config_src, 'default disabled')
check('source default remoteBrainEndpoint blank', 'define("remoteBrainEndpoint", "")' in config_src, 'default blank endpoint')

# 3. Simulate allowed endpoint decisions from RemoteBrainBridge.isAllowedEndpoint.
def is_allowed(endpoint):
    if endpoint is None or not endpoint.strip(): return False
    lower = endpoint.lower()
    return lower.startswith('https://') or lower.startswith('http://127.0.0.1') or lower.startswith('http://localhost')

check('blank endpoint rejected', not is_allowed(''), 'configured=false')
check('remote http endpoint rejected', not is_allowed('http://example.com/chat'), 'nonlocal plain HTTP rejected')
check('remote https endpoint accepted', is_allowed('https://example.com/v1/chat/completions'), 'HTTPS accepted')
check('local http endpoint accepted for dev/mock', is_allowed(f'http://127.0.0.1:{port}/chat'), 'localhost accepted')

# 4. Hit mock gateway using same simple-gateway JSON shape that RemoteBrainBridge uses.
request = {
    'sessionId': 'minecraft',
    'model': 'test-model',
    'stream': False,
    'messages': [
        {'role': 'system', 'content': 'test system'},
        {'role': 'user', 'content': 'hi'}
    ]
}
req = urllib.request.Request(
    f'http://127.0.0.1:{port}/chat',
    data=json.dumps(request).encode('utf-8'),
    headers={'Content-Type': 'application/json', 'Authorization': 'Bearer TEST'},
    method='POST'
)
try:
    with urllib.request.urlopen(req, timeout=3) as resp:
        body = json.loads(resp.read().decode('utf-8'))
    check('mock remote gateway responds', body.get('reply') == 'mock gateway ok', str(body))
except Exception as e:
    check('mock remote gateway responds', False, repr(e))

# 5. Verify gateway received expected fields.
if MockGateway.seen:
    path, headers, data = MockGateway.seen[0]
    check('gateway request path ok', path == '/chat', path)
    check('gateway request has sessionId', data.get('sessionId') == 'minecraft', str(data.get('sessionId')))
    check('gateway request has messages', isinstance(data.get('messages'), list) and len(data['messages']) == 2, str(data.get('messages')))
    check('gateway authorization bearer sent', headers.get('Authorization') == 'Bearer TEST', headers.get('Authorization'))
else:
    check('gateway received request', False, 'no request captured')

# 6. Confirm Ollama is not reachable on local sandbox either.
try:
    with socket.create_connection(('127.0.0.1', 11434), timeout=1):
        ollama = True
except OSError as e:
    ollama = False
    ollama_err = type(e).__name__ + ': ' + str(e)
check('ollama localhost refused', not ollama, 'reachable' if ollama else ollama_err)

server.shutdown()
failed = [x for x in results if not x[1]]
print('\nSUMMARY passed=', len(results)-len(failed), 'failed=', len(failed))
if failed:
    raise SystemExit(1)
