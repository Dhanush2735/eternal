from pathlib import Path
src=(Path('/home/user/eternal/src/main/java/com/eternalengine/core/SimulationEngine.java')).read_text()
cmd=(Path('/home/user/eternal/src/main/java/com/eternalengine/commands/EternalEngineCommands.java')).read_text()
config=(Path('/home/user/eternal/src/main/java/com/eternalengine/config/EternalEngineConfig.java')).read_text()
checks=[]
def check(name, ok, detail):
    checks.append((name,ok,detail)); print(('PASS' if ok else 'FAIL')+' - '+name+': '+detail)
check('env enabled override exists', 'EE_BRAIN_ENABLED' in src and 'ee.brain.enabled' in src, 'remote enable can come from env/JVM')
check('LiteLLM env overrides exist', all(x in src for x in ['EE_LITELLM_ENDPOINT','EE_LITELLM_MODEL','EE_LITELLM_TOKEN','EE_LITELLM_EXTRA_HEADERS']), 'LiteLLM values can come from env')
check('Portkey env overrides exist', all(x in src for x in ['EE_PORTKEY_API_KEY','EE_PORTKEY_PROVIDER_TOKEN','EE_PORTKEY_MODEL','EE_PORTKEY_PROVIDER','EE_PORTKEY_CONFIG','EE_PORTKEY_ENDPOINT']), 'Portkey values can come from env')
check('timeout env override exists', 'EE_BRAIN_TIMEOUT_MS' in src and 'ee.brain.timeoutMs' in src, 'timeout can come from env/JVM')
check('auto enable on env endpoint/key', '|| !overrideString("EE_LITELLM_ENDPOINT"' in src and '|| !overrideString("EE_PORTKEY_API_KEY"' in src, 'credentials auto-enable remote bridge')
check('safe config command exists', 'Commands.literal("config")' in cmd and 'executeBrainConfig' in cmd and 'getBrainConfigSummary' in src, '/ee brain config added')
check('config summary does not print token values', 'token configured=' in src and 'liteLlmTokenResolved() +' not in src and 'portkeyProviderTokenResolved() +' not in src, 'only booleans for token presence')
check('config comments mention env', 'EE_LITELLM_ENDPOINT' in config and 'EE_PORTKEY_API_KEY' in config, 'TOML comments include env alternatives')
failed=[c for c in checks if not c[1]]
print('\nSUMMARY passed=', len(checks)-len(failed), 'failed=', len(failed))
if failed: raise SystemExit(1)
