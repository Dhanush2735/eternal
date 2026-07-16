from pathlib import Path
import re, socket

ROOT = Path('/home/user/eternal')

config = (ROOT/'src/main/java/com/eternalengine/config/EternalEngineConfig.java').read_text()
engine = (ROOT/'src/main/java/com/eternalengine/core/SimulationEngine.java').read_text()
remote = (ROOT/'src/main/java/com/eternalengine/brain/RemoteBrainBridge.java').read_text()
noop = (ROOT/'src/main/java/com/eternalengine/brain/NoopLlmBrainBridge.java').read_text()
web = (ROOT/'src/main/java/com/eternalengine/web/WebConsoleService.java').read_text()
commands = (ROOT/'src/main/java/com/eternalengine/commands/EternalEngineCommands.java').read_text()

results = []

def check(name, ok, detail):
    results.append((name, ok, detail))
    print(('PASS' if ok else 'FAIL') + ' - ' + name + ': ' + detail)

# Test 1: default server config has remote disabled and blank endpoint.
check('remote disabled by default', 'define("remoteBrainEnabled", false)' in config,
      'remoteBrainEnabled default is false')
check('remote endpoint blank by default', 'define("remoteBrainEndpoint", "")' in config,
      'remoteBrainEndpoint default is empty string')

# Test 2: SimulationEngine selection order requires enabled=true AND nonblank endpoint.
check('engine requires remote enabled and endpoint nonblank',
      'remoteBrainEnabled.get()' in engine and '!EternalEngineConfig.SERVER.remoteBrainEndpoint.get().isBlank()' in engine,
      'createConfiguredBrainBridge only chooses RemoteBrainBridge when both conditions are true')
check('engine falls back to Noop when no remote/ollama',
      ': new NoopLlmBrainBridge()' in engine,
      'Noop is expected default if neither remote nor ollama is configured')

# Test 3: /ee brain remote-start does not provide endpoint; it just reloads config values.
check('remote-start command exists', 'Commands.literal("remote-start")' in commands,
      '/ee brain remote-start is registered')
check('remote-start reads config, not command endpoint', 'enableRemoteBrainBridge()' in commands and 'remoteBrainEndpoint' not in commands[commands.find('executeBrainRemoteStart'):commands.find('executeBrainRemoteStart')+700],
      'runtime command cannot magically create an endpoint; server config must contain it')

# Test 4: RemoteBrainBridge configured=false when endpoint is blank or non-HTTPS remote.
check('remote bridge rejects blank endpoint', 'endpoint == null || endpoint.isBlank()' in remote and 'return false;' in remote,
      'blank endpoint makes configured=false')
check('remote bridge accepts HTTPS endpoint', 'lower.startsWith("https://")' in remote,
      'non-local endpoint must be HTTPS')
check('remote not configured message matches observed problem', 'Remote brain gateway is not configured' in remote,
      'observed web response comes directly from RemoteBrainBridge.chat() configured=false branch')

# Test 5: Web chat calls active SimulationEngine brain bridge.
check('web uses active brain bridge', 'SimulationEngine.getInstance().getBrainBridge()' in web and '.chat(sessionId, prompt, List.of())' in web,
      'web console forwards chat to whichever bridge is active')

# Test 6: Noop message matches observed first state.
check('noop message matches observed problem', 'Brain chat is currently using NoopLlmBrainBridge' in noop,
      'observed Noop response comes directly from NoopLlmBrainBridge.chat()')

# Test 7: Local Ollama port is not reachable in this sandbox either.
ollama_reachable = False
try:
    with socket.create_connection(('127.0.0.1', 11434), timeout=1):
        ollama_reachable = True
except OSError as e:
    ollama_error = type(e).__name__ + ': ' + str(e)
check('ollama localhost:11434 unreachable in sandbox', not ollama_reachable,
      'connect test result: ' + ('reachable' if ollama_reachable else ollama_error))

failed = [r for r in results if not r[1]]
print('\nSUMMARY')
print('passed=', len(results)-len(failed), 'failed=', len(failed))
if failed:
    raise SystemExit(1)
