#!/usr/bin/env python3
"""
Automatic structural test of every /ee command.

This sandbox cannot boot Minecraft 1.20.1 Forge (needs Java 17 and ~4GB+ RAM).
Instead this script:

  1. Parses the live Brigadier tree in EternalEngineCommands.register()
  2. Builds a sample invocation for every executable leaf
  3. Resolves the Java execute* method
  4. Checks player-gating, OP requires, engine-init guards, success/failure paths
  5. Cross-checks /ee help coverage and the previous live audit JSON

Exit code 0 only if no FAIL findings.
"""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass, field, asdict
from typing import Optional

ROOT = os.path.dirname(os.path.abspath(__file__))
CMD_FILE = os.path.join(ROOT, "src/main/java/com/eternalengine/commands/EternalEngineCommands.java")
AUDIT_FILE = os.path.join(ROOT, "LIVE_COMMAND_AUDIT_REPORT.json")
OUT_JSON = os.path.join(ROOT, "AUTO_COMMAND_TEST_REPORT.json")
OUT_MD = os.path.join(ROOT, "AUTO_COMMAND_TEST_REPORT.md")

SAMPLE_VALUES = {
    "year": "-500",
    "action": "trade",
    "x": "0",
    "z": "0",
    "step": "4",
    "botId": "agent_alpha",
    "role": "villager",
    "id": "test_colosseum",
    "name": "Colosseum",
    "radiusMeters": "500",
    "percent": "25",
    "cause": "Siege",
    "type": "TEMPLE",
    "culture": "rome",
    "eventKey": "test_event",
    "eventId": "hist_rome_1_political",
    "tab": "atlas",
    "civId": "rome",
    "petition": "grain_subsidy",
    "civA": "rome",
    "civB": "carthage",
    "doctrine": "PHALANX",
    "rate": "15",
    "pretender": "Brutus",
    "count": "100",
    "mode": "STORY",
    "number": "2",
    "seconds": "120",
    "player": "TestPlayer",
    "system": "core",
    "value": "10",
    "outbreakId": "outbreak_1",
    "diseaseId": "bubonic_plague",
    "regionId": "rome",
    "warId": "war_1",
    "attacker": "rome",
    "defender": "carthage",
    "armyId": "legio_1",
    "formation": "SHIELD_WALL",
    "civ1": "rome",
    "civ2": "carthage",
    "routeId": "route_rome_carthage",
    "origin": "rome",
    "dest": "carthage",
    "transport": "overland",
    "amount": "5000",
    "modifierPercent": "120",
    "modifierTimes100": "150",
    "religionId": "christianity",
    "targetCivId": "carthage",
    "techId": "writing",
    "latTimes10000": "419000",
    "lonTimes10000": "125000",
    "territoryId": "terr_rome_latium",
    "method": "DIPLOMATIC",
    "vipId": "caesar",
    "radius": "64",
    "confidencePercent": "95",
}


@dataclass
class Leaf:
    path: list[str]
    sample: str
    execute: str
    permission: int
    arg_names: list[str] = field(default_factory=list)
    has_optional_default: bool = False


@dataclass
class Finding:
    command: str
    execute: str
    status: str
    notes: list[str]
    permission: int
    requires_player: bool
    engine_guard: bool
    has_success: bool
    has_failure: bool
    npe_risk: bool
    live_audit: Optional[str] = None


def extract_register_body(src: str) -> str:
    m = re.search(r"public static void register\(CommandDispatcher<CommandSourceStack> dispatcher\) \{", src)
    if not m:
        raise SystemExit("register() not found")
    start = src.find("{", m.end() - 1)
    depth = 0
    for i in range(start, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[start + 1 : i]
    raise SystemExit("unbalanced register()")


def extract_methods(src: str) -> dict[str, str]:
    methods = {}
    pattern = re.compile(
        r"(?:private|public) static int (execute\w+)\((.*?)\) \{",
        re.S,
    )
    for m in pattern.finditer(src):
        name = m.group(1)
        body_start = m.end() - 1
        depth = 0
        for i in range(body_start, len(src)):
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
                if depth == 0:
                    methods[name] = src[body_start + 1 : i]
                    break
    return methods


TOKEN_RE = re.compile(
    r"Commands\.literal\(\"(?P<lit>[^\"]+)\"\)"
    r"|Commands\.argument\(\"(?P<arg>[^\"]+)\""
    r"|\.requires\(src -> src\.hasPermission\((?P<perm>\d+)\)\)"
    r"|\.executes\(ctx -> (?P<exec>execute\w+)\("
    r"|(?P<then>\.then\()"
    r"|(?P<rp>\))"
)


def parse_tree(register_body: str) -> list[Leaf]:
    tokens = list(TOKEN_RE.finditer(register_body))
    leaves: list[Leaf] = []
    stack: list[dict] = [{"lit": "ee", "perm": 0, "args": []}]
    pending_exec = None
    i = 0
    # Walk tokens with a paren depth that tracks .then( groups
    # Simpler approach: maintain a node stack of literals/args.
    node_stack: list[dict] = []
    perm_stack: list[int] = [0]
    current_perm = 0

    # We'll parse by tracking depth of .then(
    class Node:
        def __init__(self, kind, name, perm):
            self.kind = kind  # lit or arg
            self.name = name
            self.perm = perm
            self.executes: list[str] = []
            self.children: list[Node] = []

    root = Node("lit", "ee", 0)
    path = [root]
    then_depth_at_push = []
    then_depth = 0

    # Rebuild using a character scanner that is more reliable
    # Fall back: sequential token machine
    nodes_at_depth: dict[int, Node] = {0: root}
    current = root
    depth = 0
    last_kind = None

    # Use a stack of current node at each .then nesting
    stack_nodes = [root]
    stack_then = [0]
    pending_perm = 0

    # Manual scan of the chained builder is messy because .then( adds a child
    # and extra ) closes it. Track:
    #   then(  -> push new anonymous slot, next literal/argument fills it
    idx = 0
    current_node = root
    node_stack = [root]
    expect_child = False

    for t in tokens:
        if t.group("then"):
            expect_child = True
            node_stack.append(None)  # placeholder for child
        elif t.group("lit"):
            node = Node("lit", t.group("lit"), pending_perm or current_perm_of(node_stack))
            pending_perm = 0
            attach(node_stack, node, expect_child)
            expect_child = False
        elif t.group("arg"):
            node = Node("arg", t.group("arg"), pending_perm or current_perm_of(node_stack))
            pending_perm = 0
            attach(node_stack, node, expect_child)
            expect_child = False
        elif t.group("perm"):
            pending_perm = int(t.group("perm"))
            # apply to current node if already created
            cur = top_node(node_stack)
            if cur is not None:
                cur.perm = max(cur.perm, pending_perm)
        elif t.group("exec"):
            cur = top_node(node_stack)
            if cur is not None:
                if pending_perm:
                    cur.perm = max(cur.perm, pending_perm)
                cur.executes.append(t.group("exec"))
        elif t.group("rp"):
            # closing paren of .then( or argument/literal/executes
            # Pop placeholders / finished children when a .then group ends.
            # Too many ) in executes(...) too. Only pop when top is a real child
            # and we are closing its .then(.
            pass

    # The token machine above is incomplete because ) is overloaded.
    # Use indentation-based reconstruction from the source lines instead.
    return parse_tree_by_lines(register_body)


def current_perm_of(node_stack):
    for n in reversed(node_stack):
        if n is not None:
            return n.perm
    return 0


def top_node(node_stack):
    for n in reversed(node_stack):
        if n is not None:
            return n
    return None


def attach(node_stack, node, expect_child):
    # find parent
    parent = None
    for n in reversed(node_stack[:-1] if expect_child or node_stack[-1] is None else node_stack):
        if n is not None:
            parent = n
            break
    if node_stack and node_stack[-1] is None:
        node_stack[-1] = node
    else:
        node_stack.append(node)
    if parent is not None and parent is not node:
        parent.children.append(node)


def parse_tree_by_lines(register_body: str) -> list[Leaf]:
    """
    Parse the fluent builder using .then( nesting via parentheses counts per
    structural token, keeping a stack of path segments.
    """
    # Work on a compressed token stream with paren depth.
    s = register_body
    leaves: list[Leaf] = []

    class Seg:
        def __init__(self, kind, name, depth, perm=0):
            self.kind = kind
            self.name = name
            self.depth = depth
            self.perm = perm
            self.execs: list[tuple[str, int]] = []  # (method, perm)

    # Scan with regex and a running paren depth
    token_re = re.compile(
        r"Commands\.literal\(\"([^\"]+)\"\)"
        r"|Commands\.argument\(\"([^\"]+)\""
        r"|\.requires\(src -> src\.hasPermission\((\d+)\)\)"
        r"|\.executes\(ctx -> (execute\w+)\("
        r"|(\.then\()"
        r"|(\))"
    )

    # We need accurate paren depth. Walk the register body char by char for
    # parens, and overlay token matches.
    segs: list[Seg] = []
    stack: list[Seg] = []
    current_perm = 0
    perm_at_depth: dict[int, int] = {0: 0}

    # Simpler reliable method: split into statements of .then(Commands.literal
    # by tracking depth of parentheses starting after Commands.literal("ee")
    depth = 0
    i = 0
    matches = list(token_re.finditer(s))

    # Reconstruct using depth of .then(
    # Each .then( increases a "then-depth". Matching ) decreases it.
    # Problem: executes(ctx -> foo(src, a, b)) has extra parens.
    # Count parens globally and associate tokens with depth at token start.

    events = []
    pos = 0
    d = 0
    while pos < len(s):
        m = token_re.match(s, pos)
        if m:
            events.append((d, m))
            # still need to count parens in the matched text except we will
            # also scan char by char... fall through
        ch = s[pos]
        if ch == "(":
            d += 1
        elif ch == ")":
            d -= 1
        pos += 1

    # Re-scan properly: record depth BEFORE consuming token text via char scan,
    # but detect tokens at current pos.
    events = []
    d = 0
    pos = 0
    n = len(s)
    while pos < n:
        m = token_re.match(s, pos)
        if m:
            events.append((d, m))
            # consume token without double-counting? we'll still walk chars
        ch = s[pos]
        if ch == "(":
            d += 1
        elif ch == ")":
            d -= 1
        pos += 1

    # Convert events to a stack of segments keyed by then-depth.
    # Commands.literal and argument at depth D attach to parent at smaller depth.
    # .then( itself increases depth immediately after because of '('.
    # So a child literal's depth is parent_then_depth+1 or more.

    # Filter to useful events
    useful = []
    for d, m in events:
        if m.group(1) or m.group(2) or m.group(3) or m.group(4):
            useful.append((d, m))

    # Parent of a node is the latest literal/arg with smaller depth
    nodes: list[tuple[int, Seg]] = []
    pending_perm_by_depth: dict[int, int] = {}

    def ancestor_chain(index: int, depth: int) -> list:
        ancestors = []
        for pd, pseg in nodes[:index]:
            while ancestors and ancestors[-1][0] >= pd:
                ancestors.pop()
            ancestors.append((pd, pseg))
        while ancestors and ancestors[-1][0] >= depth:
            ancestors.pop()
        return ancestors

    def inherited_perm(index: int, depth: int) -> int:
        best = 0
        for _pd, pseg in ancestor_chain(index, depth):
            best = max(best, pseg.perm)
        return best

    pending_perm_for_next = 0
    for d, m in useful:
        if m.group(3):  # permission — applies to the current/next node at this depth
            p = int(m.group(3))
            pending_perm_for_next = p
            if nodes:
                nd, seg = nodes[-1]
                if nd <= d:
                    seg.perm = max(seg.perm, p)
        elif m.group(1):  # literal
            perm = max(inherited_perm(len(nodes), d), pending_perm_for_next)
            pending_perm_for_next = 0
            seg = Seg("lit", m.group(1), d, perm)
            nodes.append((d, seg))
        elif m.group(2):  # argument
            perm = max(inherited_perm(len(nodes), d), pending_perm_for_next)
            pending_perm_for_next = 0
            seg = Seg("arg", m.group(2), d, perm)
            nodes.append((d, seg))
        elif m.group(4):  # executes
            exec_name = m.group(4)
            perm = inherited_perm(len(nodes), d)
            if nodes:
                nd, seg = nodes[-1]
                if nd <= d:
                    if pending_perm_for_next:
                        seg.perm = max(seg.perm, pending_perm_for_next)
                    seg.perm = max(seg.perm, perm)
                    seg.execs.append((exec_name, seg.perm))
            pending_perm_for_next = 0

    # For each node with execs, build path from ancestors
    leaves = []
    seen = set()
    for idx, (d, seg) in enumerate(nodes):
        if not seg.execs:
            continue
        path_segs = []
        for pd, pseg in nodes[: idx + 1]:
            if pd < d:
                # ancestor if no intervening node with pd < mid < d that isn't ancestor
                path_segs.append((pd, pseg))
        # keep increasing depths only (longest chain of increasing depth)
        chain = []
        last_depth = -1
        # We need the ancestor chain: nodes that enclose this node.
        # A previous node is ancestor if its depth is < d and there is no
        # later previous node with depth <= that ancestor that closed.
        ancestors = []
        for pd, pseg in nodes[:idx]:
            while ancestors and ancestors[-1][0] >= pd:
                ancestors.pop()
            ancestors.append((pd, pseg))
        while ancestors and ancestors[-1][0] >= d:
            ancestors.pop()
        path = ["ee"]
        args = []
        perm = 0
        for pd, pseg in ancestors + [(d, seg)]:
            perm = max(perm, pseg.perm)
            if pseg.kind == "lit":
                if pseg.name != "ee":
                    path.append(pseg.name)
            else:
                path.append("<" + pseg.name + ">")
                args.append(pseg.name)
        for exec_name, eperm in seg.execs:
            sample_parts = ["ee"]
            arg_names = []
            for pd, pseg in ancestors + [(d, seg)]:
                if pseg.kind == "lit":
                    if pseg.name != "ee":
                        sample_parts.append(pseg.name)
                else:
                    arg_names.append(pseg.name)
                    sample_parts.append(SAMPLE_VALUES.get(pseg.name, "test"))
            sample = "/" + " ".join(sample_parts)
            key = (sample, exec_name, tuple(arg_names))
            if key in seen:
                continue
            seen.add(key)
            leaves.append(
                Leaf(
                    path=path,
                    sample=sample,
                    execute=exec_name,
                    permission=max(perm, eperm),
                    arg_names=arg_names,
                )
            )
    return leaves


def analyze_method(name: str, body: str) -> dict:
    player = "instanceof ServerPlayer" in body or "source.getPlayerOrException" in body
    engine_guard = (
        "SimulationEngine.isInitialized()" in body
        or "getInstance()" not in body
    )
    # more precise NPE: getInstance without isInitialized
    uses_engine = "SimulationEngine.getInstance()" in body
    npe = uses_engine and "SimulationEngine.isInitialized()" not in body
    has_success = "sendSuccess" in body
    has_failure = "sendFailure" in body
    empty = body.strip() in {"return 1;", "return 0;"}
    swallow = "catch (" in body and "return 0" in body
    todo = bool(re.search(r"TODO|FIXME|not implemented|UnsupportedOperation", body, re.I))
    return {
        "requires_player": player,
        "engine_guard": (not uses_engine) or ("SimulationEngine.isInitialized()" in body),
        "npe_risk": npe,
        "has_success": has_success,
        "has_failure": has_failure,
        "empty": empty,
        "todo": todo,
        "swallow": swallow,
        "lines": body.count("\n") + 1,
    }


HELP_ROOTS_EXPECTED = {
    "status", "year", "rep", "probe", "blockqueue", "building", "scan", "anomaly",
    "affairs", "timeline", "event", "data", "disease", "war", "trade", "economy",
    "agriculture", "climate", "famine", "population", "migration", "class", "urban",
    "religion", "tech", "civ", "vip", "villager", "relation", "territory",
    "incident", "vehicle", "brain", "web", "bot", "actions", "divergence",
}


def main() -> int:
    src = open(CMD_FILE, encoding="utf-8").read()
    register_body = extract_register_body(src)
    methods = extract_methods(src)
    leaves = parse_tree(register_body)

    live = {}
    if os.path.exists(AUDIT_FILE):
        for row in json.load(open(AUDIT_FILE, encoding="utf-8")):
            live[row.get("command", "")] = row.get("status", "")

    help_body = methods.get("executeHelp", "")
    help_roots = set(re.findall(r"/ee ([a-z]+)", help_body))

    findings: list[Finding] = []
    fail = 0
    warn = 0
    passed = 0

    missing_methods = []
    for leaf in leaves:
        body = methods.get(leaf.execute)
        notes = []
        status = "PASS"
        if body is None:
            notes.append(f"execute method {leaf.execute} not found")
            status = "FAIL"
            missing_methods.append(leaf.execute)
            analysis = {
                "requires_player": False,
                "engine_guard": False,
                "npe_risk": False,
                "has_success": False,
                "has_failure": False,
                "empty": True,
                "todo": False,
            }
        else:
            analysis = analyze_method(leaf.execute, body)
            if analysis["empty"]:
                notes.append("handler body is empty")
                status = "FAIL"
            if analysis["todo"]:
                notes.append("TODO/FIXME/not implemented")
                status = "FAIL"
            if analysis["npe_risk"]:
                notes.append("SimulationEngine.getInstance() without isInitialized() guard")
            if not analysis["has_success"] and not analysis["has_failure"]:
                notes.append("no sendSuccess/sendFailure — player gets no chat feedback")
                if status != "FAIL":
                    status = "WARN"
            if analysis["requires_player"]:
                notes.append("PLAYER required (console will fail)")
            if leaf.permission:
                notes.append(f"OP {leaf.permission}+")

        live_status = None
        # fuzzy match live audit
        if leaf.sample in live:
            live_status = live[leaf.sample]
        else:
            # match by prefix of literals only
            for cmd, st in live.items():
                if cmd.split()[:3] == leaf.sample.split()[:3]:
                    live_status = st
                    break

        if status == "FAIL":
            fail += 1
        elif status == "WARN":
            warn += 1
        else:
            passed += 1
            if not notes:
                notes.append("handler present, feedback path exists")

        findings.append(
            Finding(
                command=leaf.sample,
                execute=leaf.execute,
                status=status,
                notes=notes,
                permission=leaf.permission,
                requires_player=analysis["requires_player"],
                engine_guard=analysis["engine_guard"],
                has_success=analysis["has_success"],
                has_failure=analysis["has_failure"],
                npe_risk=analysis["npe_risk"],
                live_audit=live_status,
            )
        )

    # Tree roots vs help
    roots = sorted({leaf.path[1] if len(leaf.path) > 1 else leaf.path[0] for leaf in leaves})
    help_missing = [r for r in roots if r not in help_roots and r not in {"help", "speed", "observer", "override", "slowdown", "debug", "performance", "reload", "backup", "historian", "reset", "npc", "map", "audio", "heraldry", "chrono", "ecs", "morph", "codex", "sovereign", "disaster", "achievements"}]
    extra_help = sorted(help_roots - set(roots) - {"help"})

    # Unreferenced execute methods
    used = {leaf.execute for leaf in leaves}
    unused = sorted(set(methods) - used - {"executeHelp"})

    summary = {
        "environment": {
            "live_minecraft": False,
            "reason": "Sandbox has Java 11 and 1.9Gi RAM; Forge 1.20.1 needs Java 17 and more memory. Commands were tested structurally against EternalEngineCommands.java.",
        },
        "counts": {
            "executable_leaves": len(leaves),
            "execute_methods": len(methods),
            "pass": passed,
            "warn": warn,
            "fail": fail,
            "unique_roots": len(roots),
            "live_audit_commands": len(live),
        },
        "roots": roots,
        "help_missing_roots": help_missing,
        "help_extra_roots": extra_help,
        "unreferenced_execute_methods": unused,
        "findings": [asdict(f) for f in findings],
    }

    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)

    lines = []
    lines.append("# Eternal Engine automatic command test report")
    lines.append("")
    lines.append("**Live Minecraft execution: NO.** This sandbox cannot boot Forge 1.20.1.")
    lines.append("Every `/ee` leaf was parsed from `EternalEngineCommands.register()` and its Java handler was checked.")
    lines.append("")
    lines.append(f"| Leaves | Methods | PASS | WARN | FAIL |")
    lines.append(f"|---:|---:|---:|---:|---:|")
    lines.append(f"| {len(leaves)} | {len(methods)} | {passed} | {warn} | {fail} |")
    lines.append("")
    lines.append("## Command roots")
    lines.append(", ".join(f"`{r}`" for r in roots))
    lines.append("")
    if help_missing:
        lines.append("## `/ee help` missing roots")
        lines.append(", ".join(f"`{r}`" for r in help_missing))
        lines.append("")
    if unused:
        lines.append("## Execute methods not wired to the tree")
        for u in unused:
            lines.append(f"- `{u}`")
        lines.append("")
    lines.append("## Results")
    lines.append("")
    lines.append("| # | Command | Handler | OP | Player | Status | Notes |")
    lines.append("|---:|---|---|---:|:---:|:---:|---|")
    for i, fnd in enumerate(findings, 1):
        notes = "; ".join(fnd.notes)
        player = "yes" if fnd.requires_player else ""
        lines.append(
            f"| {i} | `{fnd.command}` | `{fnd.execute}` | {fnd.permission} | {player} | **{fnd.status}** | {notes} |"
        )
    lines.append("")
    lines.append("## How to run this live on your game")
    lines.append("")
    lines.append("On a running OP 4 client, paste blocks from `EE_COMMAND_TEST_ORDER.md`.")
    lines.append("This script is: `python3 test_all_ee_commands.py`")
    lines.append("")

    open(OUT_MD, "w", encoding="utf-8").write("\n".join(lines))

    print("=" * 64)
    print("  ETERNAL ENGINE AUTOMATIC /ee COMMAND TEST")
    print("=" * 64)
    print(f"  Leaves parsed     : {len(leaves)}")
    print(f"  Execute methods   : {len(methods)}")
    print(f"  PASS / WARN / FAIL: {passed} / {warn} / {fail}")
    print(f"  Roots             : {len(roots)}")
    print(f"  Help missing roots: {help_missing}")
    print(f"  Unreferenced exec : {unused}")
    print("-" * 64)
    for fnd in findings:
        if fnd.status != "PASS":
            print(f"  [{fnd.status:4}] {fnd.command:55}  {fnd.execute}  {'; '.join(fnd.notes)}")
    print("-" * 64)
    print(f"  Wrote {OUT_MD}")
    print(f"  Wrote {OUT_JSON}")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
