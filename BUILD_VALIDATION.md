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

### Archived generated reference reports (2026-11-03)
For workspace space, the earlier `AUTO_COMMAND_TEST_REPORT.json` and `field_dossier.json` are stored as `.json.xz`, with their exact byte hashes and guarded read/restore utility in `archived_generated_reports.py`. Their Markdown companions remain in place. A restore refuses to overwrite a newly generated original and requires space under the 125 MB guard. `test_all_ee_commands.py` still writes its original JSON path; its report was structural, not a live Minecraft test.

Storage note: the historical generated Windows folder listing `structure.txt` is stored losslessly as `structure.txt.xz`. Verify using `python3 eternal/archived_generated_reports.py structure.txt`, or add `--restore` when space permits; the helper refuses overwrite. It is not a build or gameplay dependency.

Additional generated historical references `FIELD_DOSSIER.md` and `field_spec.json` are losslessly archived as `.xz` and verified/restored with `archived_generated_reports.py`; original paths remain the generator output paths. The archived files are historical references, not active game data.

Storage note (2026-11-03): the historical generated `LIVE_COMMAND_AUDIT_REPORT.json` is preserved as `LIVE_COMMAND_AUDIT_REPORT.json.xz`, verified/restorable with `archived_generated_reports.py` subject to its guard. Its generator still writes the original path.
