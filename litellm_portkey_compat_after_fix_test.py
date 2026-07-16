from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
from pathlib import Path
import json, socket, urllib.request, urllib.error

ROOT = Path('/home/user/eternal')
remote_src = (ROOT/'src/main/java/com/eternalengine/brain/RemoteBrainBridge.java').read_text()
config_src = (ROOT/'src/main/java/com/eternalengine/config/EternalEngineConfig.java').read_text()
engine_src = (ROOT/'src/main/java/com/eternalengine/core/SimulationEngine.java').read_text()

results=[]
def check(name, ok, detail):
    results.append((name,ok,detail))
    print(('PASS' if ok else 'FAIL') + ' - ' + name + ': ' + detail)

# Static compatibility checks for new Portkey/LiteLLM support
check('OpenAI-compatible detection exists', 'contains("/v1/chat/completions")' in remote_src, 'LiteLLM/OpenAI endpoints use OpenAI request shape')
check('Ollama /api/chat detection exists', 'endsWith("/api/chat")' in remote_src, 'hosted Ollama endpoints use Ollama request shape')
check('extra header config exists', 'remoteBrainExtraHeaders' in config_src, 'server config supports gateway-specific headers')
check('RemoteBrainBridge parses extra headers', 'parseExtraHeaders' in remote_src and 'builder.header(header.getKey(), header.getValue())' in remote_src, 'extra headers are sent')
check('dangerous header override blocked', 'lower.equals("authorization")' in remote_src and 'lower.equals("host")' in remote_src, 'Authorization/Host/etc cannot be overridden by extra headers')
check('SimulationEngine passes extra headers', 'remoteBrainExtraHeaders.get()' in engine_src, 'config extra headers reach bridge constructor')

# Runtime mock: LiteLLM/OpenAI-compatible requires only Authorization + standard OpenAI body.
class Mock(BaseHTTPRequestHandler):
    mode='litellm'
    def do_POST(self):
        data=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))).decode())
        ok=True; reason=''
        if self.path != '/v1/chat/completions': ok=False; reason='wrong path'
        if not self.headers.get('Authorization','').startswith('Bearer '): ok=False; reason='missing auth'
        if 'model' not in data or 'messages' not in data: ok=False; reason='bad body'
        if self.mode == 'portkey':
            if not self.headers.get('x-portkey-api-key'): ok=False; reason='missing x-portkey-api-key'
            if not self.headers.get('x-portkey-provider'): ok=False; reason='missing x-portkey-provider'
        self.send_response(200 if ok else 401)
        self.send_header('Content-Type','application/json')
        self.end_headers()
        self.wfile.write(json.dumps({'choices':[{'message':{'content':'ok'}}]} if ok else {'error':reason}).encode())
    def log_message(self, *args): pass

def free_port():
    s=socket.socket(); s.bind(('127.0.0.1',0)); p=s.getsockname()[1]; s.close(); return p

def post(port, headers):
    body={'model':'test-model','stream':False,'temperature':0.2,'messages':[{'role':'user','content':'hi'}]}
    req=urllib.request.Request(f'http://127.0.0.1:{port}/v1/chat/completions', data=json.dumps(body).encode(), headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=3) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()

port=free_port(); srv=HTTPServer(('127.0.0.1',port), Mock); Thread(target=srv.serve_forever, daemon=True).start()
Mock.mode='litellm'
status, body=post(port, {'Content-Type':'application/json','Authorization':'Bearer LITELLM_KEY'})
check('LiteLLM/OpenAI mock accepts current base headers', status==200, f'{status} {body}')
Mock.mode='portkey'
status, body=post(port, {'Content-Type':'application/json','Authorization':'Bearer PROVIDER_KEY'})
check('Portkey mock rejects missing extra headers', status==401 and 'x-portkey' in body, f'{status} {body}')
status, body=post(port, {'Content-Type':'application/json','Authorization':'Bearer PROVIDER_KEY','x-portkey-api-key':'PORTKEY_KEY','x-portkey-provider':'openai'})
check('Portkey mock accepts configured extra headers', status==200, f'{status} {body}')
srv.shutdown()

failed=[r for r in results if not r[1]]
print('\nSUMMARY passed=', len(results)-len(failed), 'failed=', len(failed))
if failed: raise SystemExit(1)
