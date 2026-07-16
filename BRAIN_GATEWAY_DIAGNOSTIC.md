# Brain Gateway Diagnostic

## Test run

Date: 2026-07-15
Workspace: `/home/user/eternal`

Ran source-level diagnostics for the observed web-console messages:

1. `Brain chat is currently using NoopLlmBrainBridge...`
2. `Server-local Ollama chat failed: java.net.ConnectException...`
3. `Remote brain gateway is not configured...`

## Results

All 12 checks passed.

## Root cause

No actual LLM backend is configured/reachable.

The three messages correspond to three different active bridge states:

### Noop state

`NoopLlmBrainBridge.chat(...)` returns the Noop message when neither remote brain nor Ollama is configured.

`SimulationEngine.createConfiguredBrainBridge()` chooses Noop when:

- `SERVER.remoteBrainEnabled=false` or endpoint blank, and
- `COMMON.ollamaEnabled=false`

### Ollama state

`OllamaBrainBridge.chat(...)` attempts to connect to localhost `:11434` and fails with `ConnectException` when Ollama is not running/installed on the Minecraft server machine.

The sandbox socket test to `127.0.0.1:11434` also produced connection refused, matching the user-side symptom.

### Remote state

`RemoteBrainBridge.chat(...)` returns `Remote brain gateway is not configured...` when `remoteBrainEndpoint` is blank or not an allowed endpoint.

`RemoteBrainBridge.isAllowedEndpoint(...)` accepts:

- `https://...`
- `http://127.0.0.1...`
- `http://localhost...`

It rejects blank endpoints and non-local plain HTTP endpoints.

## Important behavior found

`/ee brain remote-start` does not accept an endpoint argument. It only switches the active bridge to values already present in the Forge server config:

- `SERVER.remoteBrainEndpoint`
- `SERVER.remoteBrainModel`
- `SERVER.remoteBrainToken`
- `SERVER.remoteBrainTimeoutMs`

Therefore running `/ee brain remote-start` without editing `serverconfig/eternalengine-server.toml` cannot make the remote gateway work.

## Correct fix

Edit the server-side Forge config, usually under the world folder:

```text
<world>/serverconfig/eternalengine-server.toml
```

Set:

```toml
[brainGateway]
remoteBrainEnabled = true
remoteBrainEndpoint = "https://YOUR_GATEWAY_OR_PROVIDER/v1/chat/completions"
remoteBrainModel = "YOUR_MODEL"
remoteBrainToken = "YOUR_SERVER_SIDE_TOKEN"
remoteBrainTimeoutMs = 30000
```

Then run in Minecraft:

```mcfunction
/ee brain remote-start
/ee brain availability
/ee web url
```

## Recommendation

For better UX, add a command that validates and displays the remote brain configuration status without exposing the token, e.g.:

```mcfunction
/ee brain config
```

It should show:

- active bridge class
- remote enabled true/false
- endpoint configured true/false
- endpoint scheme valid true/false
- model configured
- token configured true/false
- timeout

Do not print the actual token.
