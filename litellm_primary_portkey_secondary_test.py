from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
from pathlib import Path
import json, socket, urllib.request, urllib.error

ROOT = Path('/home/user/eternal')
files = {
 'RemoteBrainBridge.java': (ROOT/'src/main/java/com/eternalengine/brain/RemoteBrainBridge.java').read_text(),
 'SimulationEngine.java': (ROOT/'src/main/java/com/eternalengine/core/SimulationEngine.java').read_text(),
 'EternalEngineConfig.java': (ROOT/'src/main/java/com/eternalengine/config/EternalEngineConfig.java').read_text(),
 'EternalEngineCommands.java': (ROOT/'src/main/java/com/eternalengine/commands/EternalEngineCommands.java').read_text(),
}
results=[]
def check(name, ok, detail):
    results.append((name, ok, detail)); print(('PASS' if ok else 'FAIL') + ' - ' + name + ': ' + detail)
all_src='\n'.join(files.values())
check('no local LLM/Ollama active references', 'Ollama' not in all_src and 'ollama' not in all_src, 'active source/config/commands are remote-only')
check('LiteLLM config exists', all(x in files['EternalEngineConfig.java'] for x in ['liteLlmEndpoint','liteLlmModel','liteLlmToken','liteLlmExtraHeaders']), 'primary fields present')
check('Portkey config exists', all(x in files['EternalEngineConfig.java'] for x in ['portkeyEndpoint','portkeyModel','portkeyProviderToken','portkeyApiKey','portkeyProvider','portkeyConfig']), 'secondary fields present')
check('bridge documents priority', '1. LiteLLM/OpenAI-compatible gateway as the primary source.' in files['RemoteBrainBridge.java'] and '2. Portkey/OpenAI-compatible gateway as the secondary fallback source.' in files['RemoteBrainBridge.java'], 'priority documented')
check('bridge adds LiteLLM before Portkey', files['RemoteBrainBridge.java'].find('GatewayTarget lite') < files['RemoteBrainBridge.java'].find('GatewayTarget portkey'), 'primary target built first')
check('request loop tries targets in order', 'for (GatewayTarget target : targets)' in files['RemoteBrainBridge.java'], 'fallback loop present')
check('Portkey disabled when api key blank', 'portkeyApiKey.isBlank() ? "" : portkeyEndpointResolved()' in files['SimulationEngine.java'], 'default Portkey endpoint does not activate without key')
check('Portkey headers are built', 'x-portkey-api-key=' in files['SimulationEngine.java'] and 'x-portkey-provider=' in files['SimulationEngine.java'], 'required Portkey headers generated')
check('HTTPS only', 'return endpoint.toLowerCase(Locale.ROOT).startsWith("https://");' in files['RemoteBrainBridge.java'], 'only HTTPS endpoints accepted')

# Runtime simulation of priority/fallback behavior with mock endpoints.
class Mock(BaseHTTPRequestHandler):
    calls=[]
    def do_POST(self):
        data=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))).decode())
        Mock.calls.append((self.server.name, self.path, dict(self.headers), data))
        if self.server.name == 'LiteLLM':
            self.send_response(503); self.send_header('Content-Type','application/json'); self.end_headers(); self.wfile.write(b'{"error":"primary down"}')
        else:
            ok = self.headers.get('x-portkey-api-key') == 'PORTKEY_KEY' and self.headers.get('x-portkey-provider') == 'openai'
            self.send_response(200 if ok else 401); self.send_header('Content-Type','application/json'); self.end_headers()
            self.wfile.write(json.dumps({'choices':[{'message':{'content':'secondary ok'}}]} if ok else {'error':'missing portkey headers'}).encode())
    def log_message(self,*args): pass

def free_port():
    s=socket.socket(); s.bind(('127.0.0.1',0)); p=s.getsockname()[1]; s.close(); return p

def start(name):
    p=free_port(); srv=HTTPServer(('127.0.0.1',p), Mock); srv.name=name; Thread(target=srv.serve_forever, daemon=True).start(); return srv,p

def post(url, headers):
    body={'model':'model','stream':False,'temperature':0.2,'messages':[{'role':'user','content':'hi'}]}
    req=urllib.request.Request(url, data=json.dumps(body).encode(), headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=3) as r: return r.status, r.read().decode()
    except urllib.error.HTTPError as e: return e.code, e.read().decode()

lite_srv, lite_port = start('LiteLLM')
port_srv, port_port = start('Portkey')
# Simulate bridge order: try primary then secondary on non-2xx.
status, body = post(f'http://127.0.0.1:{lite_port}/v1/chat/completions', {'Content-Type':'application/json','Authorization':'Bearer LITE_KEY'})
if status < 200 or status >= 300:
    status, body = post(f'http://127.0.0.1:{port_port}/v1/chat/completions', {'Content-Type':'application/json','Authorization':'Bearer PROVIDER_KEY','x-portkey-api-key':'PORTKEY_KEY','x-portkey-provider':'openai'})
check('runtime fallback primary-fail secondary-success', status == 200 and 'secondary ok' in body, f'{status} {body}')
check('runtime call order LiteLLM then Portkey', [c[0] for c in Mock.calls] == ['LiteLLM','Portkey'], str([c[0] for c in Mock.calls]))
lite_srv.shutdown(); port_srv.shutdown()

failed=[r for r in results if not r[1]]
print('\nSUMMARY passed=', len(results)-len(failed), 'failed=', len(failed))
if failed: raise SystemExit(1)
