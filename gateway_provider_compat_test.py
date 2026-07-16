from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
import json, socket, urllib.request, urllib.error

class GatewayMock(BaseHTTPRequestHandler):
    mode = 'litellm'
    seen = []
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        data = json.loads(body.decode('utf-8'))
        GatewayMock.seen.append((self.path, dict(self.headers), data))
        ok = True
        reason = ''
        if self.path != '/v1/chat/completions':
            ok = False; reason = 'wrong path'
        if not self.headers.get('Authorization', '').startswith('Bearer '):
            ok = False; reason = 'missing Authorization Bearer'
        if GatewayMock.mode == 'portkey':
            if not self.headers.get('x-portkey-api-key'):
                ok = False; reason = 'missing x-portkey-api-key'
            if not self.headers.get('x-portkey-provider'):
                ok = False; reason = 'missing x-portkey-provider'
        if 'model' not in data or 'messages' not in data:
            ok = False; reason = 'missing model/messages'
        if ok:
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'choices':[{'message':{'content':'ok'}}]}).encode())
        else:
            self.send_response(401)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'error': reason}).encode())
    def log_message(self, fmt, *args): pass

def free_port():
    s=socket.socket(); s.bind(('127.0.0.1',0)); p=s.getsockname()[1]; s.close(); return p

def request(port, headers):
    body = {'model':'test-model','stream':False,'temperature':0.2,'messages':[{'role':'user','content':'hi'}]}
    req = urllib.request.Request(f'http://127.0.0.1:{port}/v1/chat/completions', data=json.dumps(body).encode(), headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=3) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()

port = free_port()
server = HTTPServer(('127.0.0.1', port), GatewayMock)
Thread(target=server.serve_forever, daemon=True).start()

# Simulate current RemoteBrainBridge headers: Content-Type + Authorization only.
current_headers = {'Content-Type':'application/json','Authorization':'Bearer TEST_TOKEN'}

GatewayMock.mode = 'litellm'
status, body = request(port, current_headers)
print(('PASS' if status == 200 else 'FAIL'), '- LiteLLM/OpenAI-compatible current headers:', status, body)

GatewayMock.mode = 'portkey'
status, body = request(port, current_headers)
print(('PASS_EXPECTED_FAIL' if status == 401 and 'x-portkey' in body else 'UNEXPECTED'), '- Portkey hosted current headers:', status, body)

# Simulate needed Portkey headers.
portkey_headers = {'Content-Type':'application/json','Authorization':'Bearer PROVIDER_KEY','x-portkey-api-key':'PORTKEY_KEY','x-portkey-provider':'openai'}
status, body = request(port, portkey_headers)
print(('PASS' if status == 200 else 'FAIL'), '- Portkey hosted with required headers:', status, body)

server.shutdown()
