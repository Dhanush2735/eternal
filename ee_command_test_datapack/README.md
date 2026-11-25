# The `/ee` command test datapack

Ten functions that run every `/ee` command in the game. Copy this folder into
`<world>/datapacks/` and run `function eternalengine:test_all` (423 commands), or the six parts one
at a time.

    test_all      423 commands: every command, no reset
    test_part1..6 81, 81, 81, 81, 81, 23 commands
    test_reset    /ee reset confirm — DESTRUCTIVE

**One setting is required.** `test_reset` runs `/ee reset confirm`, and that command requires
permission level 4. A datapack function is parsed at `function-permission-level`, which is 2 by
default, so on a default server the server drops the **whole** `test_reset` function at load and
logs only:

    [ServerFunctionLibrary]: Failed to load function eternalengine:test_reset
    java.lang.IllegalArgumentException: Whilst parsing command on line 3:
        Incorrect argument for command at position 3: ee <--[HERE]

Put this in `server.properties` (found on 2026-10-23 by running the pack on a real headless server,
marking board 10):

    function-permission-level=4

The other nine functions run on a default server.
