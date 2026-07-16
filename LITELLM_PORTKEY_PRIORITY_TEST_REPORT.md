# LiteLLM Primary + Portkey Secondary Brain Gateway Test Report

## Requirement

User requirement:

```text
LiteLLM = primary source
Portkey = secondary source
No local LLMs
HTTPS only
```

## Implemented design

`RemoteBrainBridge` now uses a fixed gateway priority:

1. `LiteLLM` target is built and tried first.
2. `Portkey` target is built and tried second as fallback.

The bridge loops through configured targets in order. If LiteLLM returns non-2xx, times out, or fails, the request falls back to Portkey.

## Config fields

Server config `[brainGateway]` now includes:

```toml
remoteBrainEnabled = true

# Primary
liteLlmEndpoint = "https://your-litellm/v1/chat/completions"
liteLlmModel = "default"
liteLlmToken = ""
liteLlmExtraHeaders = ""

# Secondary fallback
portkeyEndpoint = "https://api.portkey.ai/v1/chat/completions"
portkeyModel = "default"
portkeyProviderToken = ""
portkeyApiKey = ""
portkeyProvider = "openai"
portkeyConfig = ""

remoteBrainTimeoutMs = 10000
```

Portkey fallback is disabled unless `portkeyApiKey` is non-blank.

## Test file

```text
/home/user/eternal/litellm_primary_portkey_secondary_test.py
```

## Test results

```text
PASS - no local LLM/Ollama active references
PASS - LiteLLM config exists
PASS - Portkey config exists
PASS - bridge documents priority
PASS - bridge adds LiteLLM before Portkey
PASS - request loop tries targets in order
PASS - Portkey disabled when api key blank
PASS - Portkey headers are built
PASS - HTTPS only
PASS - runtime fallback primary-fail secondary-success
PASS - runtime call order LiteLLM then Portkey
SUMMARY passed=11 failed=0
```

## Verdict

The active brain design is now:

```text
Minecraft server
  -> HTTPS LiteLLM primary
  -> if LiteLLM fails, HTTPS Portkey secondary
  -> advisory response/proposal
  -> Eternal Engine validation
  -> server-authoritative execution only
```

No local LLM path is exposed in active engine selection or commands.
