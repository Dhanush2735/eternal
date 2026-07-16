# Eternal Engine Build Validation Notes

## Java requirement

Use Java 17 for all Forge 1.20.1 builds.

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew clean compileJava --no-daemon --max-workers=1
```

## Terra++ local dependency

The project expects one of these files in `libs/`:

- `terraplusplus-2.0.0-alpha.jar`
- `terraplusplus-2.0.0-alpha-slim.jar`
- `terraplusplus-2.0.0-alpha-all.jar`

`build.gradle` uses a `flatDir { dirs 'libs' }` repository and wraps the selected jar with `fg.deobf(...)`.

## Sandbox note

The Arena sandbox can install Java 17 temporarily, but full ForgeGradle/MCP remapping may exceed the sandbox memory limit. If the daemon disappears during `merge`, `rename`, or MCP setup, that is an environment limit rather than a confirmed Java source error.

## Phase-0 static checks done

- Java source brace balance checked.
- No Fabric imports found in project Java sources.
- No direct OpenAI/Claude/Gemini/API-key references found in project Java sources.
- `AsyncTaskManager` priority queue was hardened with comparable future tasks.
- `PersistenceManager` now backs up before overwrite, uses `FileLock` for reads, and returns async load callbacks to the main thread queue.

## Next local compile workflow

1. Run `./gradlew clean compileJava --no-daemon --max-workers=1` locally with Java 17.
2. Paste the first compile error block into the agent.
3. Fix compile errors in small groups, then rerun.
