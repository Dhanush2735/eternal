# Eternal Engine Foundation Data

These JSON files are starter data for testing the foundation systems.

They are not complete historical databases.

`history/indus.json` is the first sourced occupancy pack (sites/traits/architecture).
2244 template `hist_*` events and 528 template VIPs were removed as fiction.

Runtime commands currently load historical events from the world folder:

```text
<world>/eternalengine/events/
```

You can copy `events/foundation_events.json` there and run:

```text
/ee event load
```

Other data folders document the target formats for future loaders.
