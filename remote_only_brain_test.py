from pathlib import Path
import json, urllib.request

ROOT=Path('/home/user/eternal')
files={p: (ROOT/p).read_text() for p in [
    Path('src/main/java/com/eternalengine/core/SimulationEngine.java'),
    Path('src/main/java/com/eternalengine/commands/EternalEngineCommands.java'),
    Path('src/main/java/com/eternalengine/brain/RemoteBrainBridge.java'),
    Path('src/main/java/com/eternalengine/brain/NoopLlmBrainBridge.java'),
    Path('src/main/java/com/eternalengine/config/EternalEngineConfig.java'),
]}
results=[]
def check(name, ok, detail):
    results.append((name,ok,detail)); print(('PASS' if ok else 'FAIL')+' - '+name+': '+detail)
all_src='\n'.join(files.values())
check('no Ollama references in active brain/config/commands/core', 'Ollama' not in all_src and 'ollama' not in all_src, 'active code no longer exposes local LLM path')
check('OllamaBrainBridge file removed', not (ROOT/'src/main/java/com/eternalengine/brain/OllamaBrainBridge.java').exists(), 'local bridge class deleted')
engine=files[Path('src/main/java/com/eternalengine/core/SimulationEngine.java')]
check('engine falls back only remote-or-noop', 'new RemoteBrainBridge' in engine and 'new NoopLlmBrainBridge' in engine and 'OllamaBrainBridge' not in engine, 'no local bridge selection')
commands=files[Path('src/main/java/com/eternalengine/commands/EternalEngineCommands.java')]
check('no ollama command', 'ollama-start' not in commands and 'executeBrainOllamaStart' not in commands, 'only remote-start remains')
remote=files[Path('src/main/java/com/eternalengine/brain/RemoteBrainBridge.java')]
check('remote allows only https', 'return endpoint.toLowerCase(Locale.ROOT).startsWith("https://");' in remote, 'localhost/http rejected by bridge')
check('OpenAI-compatible route retained', 'contains("/v1/chat/completions")' in remote and 'buildOpenAiRequest' in remote, 'LiteLLM/Portkey compatible')
check('extra headers retained for Portkey', ('liteLlmExtraHeaders' in files[Path('src/main/java/com/eternalengine/config/EternalEngineConfig.java')] or 'remoteBrainExtraHeaders' in files[Path('src/main/java/com/eternalengine/config/EternalEngineConfig.java')]) and 'builder.header(header.getKey(), header.getValue())' in remote, 'Portkey headers supported')

# Real HTTPS transport test to echo service, no secret. Try more than one public echo service.
payload={'model':'test-model','stream':False,'messages':[{'role':'user','content':'hi'}]}
https_ok=False; last_error=''
for url in ['https://postman-echo.com/post', 'https://httpbin.org/post']:
    req=urllib.request.Request(url, data=json.dumps(payload).encode(), headers={'Content-Type':'application/json','Authorization':'Bearer REDACTED'}, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            body=r.read(160).decode('utf-8','replace').replace('\n',' ')
            if r.status==200:
                https_ok=True; last_error=f'{url} status={r.status} sample={body[:80]}'; break
    except Exception as e:
        last_error=f'{url} {type(e).__name__}: {e}'
print(('PASS' if https_ok else 'SKIP') + ' - sandbox HTTPS POST echo: ' + last_error)
# Do not fail build on public echo service timeout; source-level HTTPS-only policy is tested above.
failed=[r for r in results if not r[1]]
print('\nSUMMARY passed=', len(results)-len(failed), 'failed=', len(failed))
if failed: raise SystemExit(1)
