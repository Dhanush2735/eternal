#!/usr/bin/env bash
# Headless command-path test against minimal stand-ins, not a Forge or live-game test.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/src/com/mojang/brigadier/builder" "$OUT/src/com/mojang/brigadier/context" "$OUT/src/net/minecraft/commands" "$OUT/src/net/minecraft/network/chat" "$OUT/src/com/eternalengine/clock" "$OUT/src/com/eternalengine/terrabridge" "$OUT/classes"
cat >"$OUT/src/com/mojang/brigadier/context/CommandContext.java" <<'JAVA'
package com.mojang.brigadier.context;
public class CommandContext<S> { private final S source; public CommandContext(S s) {source=s;} public S getSource(){return source;} }
JAVA
cat >"$OUT/src/com/mojang/brigadier/builder/LiteralArgumentBuilder.java" <<'JAVA'
package com.mojang.brigadier.builder;
import java.util.function.*;
import com.mojang.brigadier.context.CommandContext;
public class LiteralArgumentBuilder<S> {
 public String name; public LiteralArgumentBuilder<S> child; private Predicate<S> permission=s->true; private ToIntFunction<CommandContext<S>> action;
 public LiteralArgumentBuilder(String n){name=n;}
 public LiteralArgumentBuilder<S> then(LiteralArgumentBuilder<S> c){child=c;return this;}
 public LiteralArgumentBuilder<S> requires(Predicate<S> p){permission=p;return this;}
 public LiteralArgumentBuilder<S> executes(ToIntFunction<CommandContext<S>> c){action=c;return this;}
 public int run(S source){if(!permission.test(source))return -1;return action.applyAsInt(new CommandContext<>(source));}
}
JAVA
cat >"$OUT/src/net/minecraft/commands/Commands.java" <<'JAVA'
package net.minecraft.commands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
public class Commands { public static LiteralArgumentBuilder<CommandSourceStack> literal(String s){return new LiteralArgumentBuilder<>(s);} }
JAVA
cat >"$OUT/src/net/minecraft/network/chat/Component.java" <<'JAVA'
package net.minecraft.network.chat;
public class Component { public final String text; private Component(String t){text=t;} public static Component literal(String t){return new Component(t);} }
JAVA
cat >"$OUT/src/net/minecraft/commands/CommandSourceStack.java" <<'JAVA'
package net.minecraft.commands;
import java.util.*; import java.util.function.Supplier; import net.minecraft.network.chat.Component;
public class CommandSourceStack {
 public boolean operator; public List<String> successes=new ArrayList<>(), failures=new ArrayList<>();
 public boolean hasPermission(int n){return operator;}
 public Object getLevel(){return new Object();}
 public void sendSuccess(Supplier<Component> c, boolean broadcast){successes.add(c.get().text);}
 public void sendFailure(Component c){failures.add(c.text);}
}
JAVA
cat >"$OUT/src/com/eternalengine/clock/SimulationClockManager.java" <<'JAVA'
package com.eternalengine.clock;
public class SimulationClockManager {
 public static boolean initialized=true; public static int year=2026;
 public static boolean isInitialized(){return initialized;} public static SimulationClockManager getInstance(){return new SimulationClockManager();} public int getCurrentYear(){return year;}
}
JAVA
cat >"$OUT/src/com/eternalengine/terrabridge/TerraBridge.java" <<'JAVA'
package com.eternalengine.terrabridge;
public class TerraBridge {
 public static boolean available=true, roundTrip=true, bound=true; public static String mode="world"; public static int calls=0; private static final TerraBridge INSTANCE=new TerraBridge();
 public static TerraBridge getInstance(){return INSTANCE;} public static boolean isAvailable(){return available;}
 public boolean isBoundTo(Object level){return bound;}
 public double[] fromGeo(double lat,double lon){calls++;return new double[]{-lat*1000,lon*1000};}
 public String fromGeoSource(){return mode;} public boolean roundTrips(double[] p,double lat,double lon){return roundTrip;}
}
JAVA
cat >"$OUT/src/HyderabadCommandMockTest.java" <<'JAVA'
import com.eternalengine.hyderabad.HyderabadPilotCommand;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.terrabridge.TerraBridge;
import net.minecraft.commands.CommandSourceStack;
public class HyderabadCommandMockTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static CommandSourceStack operator(){CommandSourceStack s=new CommandSourceStack();s.operator=true;return s;}
 public static void main(String[] args){
  var root=HyderabadPilotCommand.node();check(root.name.equals("hyderabad")&&root.child.name.equals("preview"),"tree");
  var guest=new CommandSourceStack();check(root.child.run(guest)==-1&&TerraBridge.calls==0,"permission");
  var s=operator();check(root.child.run(s)==1&&s.failures.isEmpty()&&s.successes.size()==4&&TerraBridge.calls==212,"success");
  check(s.successes.get(0).contains("22 OSM ways")&&s.successes.get(1).contains("no blocks")&&s.successes.get(3).contains("OpenStreetMap"),"truthful output");
  SimulationClockManager.year=1591;var old=operator();check(root.child.run(old)==0&&old.failures.get(0).contains("2026")&&TerraBridge.calls==212,"historical year refused before projection");
  SimulationClockManager.year=2027;var future=operator();check(root.child.run(future)==0&&future.failures.get(0).contains("2026"),"future refused");
  SimulationClockManager.year=2026;TerraBridge.available=false;var noTerra=operator();check(root.child.run(noTerra)==0&&noTerra.failures.get(0).contains("Terra++"),"no Terra");
  TerraBridge.available=true;TerraBridge.bound=false;var wrongLevel=operator();check(root.child.run(wrongLevel)==0&&wrongLevel.failures.get(0).contains("level"),"reject wrong level");
  TerraBridge.bound=true;TerraBridge.mode="api (checked)";var api=operator();check(root.child.run(api)==0&&api.successes.isEmpty(),"reject public API fallback");
  TerraBridge.mode="world";TerraBridge.roundTrip=false;var failed=operator();check(root.child.run(failed)==0&&failed.successes.isEmpty(),"reject failed roundtrip");
  SimulationClockManager.initialized=false;var noClock=operator();check(root.child.run(noClock)==0&&noClock.failures.get(0).contains("clock"),"no clock");
  System.out.println("Mock command integration passed (permissions, year, world-only projection, failure paths)");
 }
}
JAVA
mapfile -d '' STUBS < <(find "$OUT/src" -name '*.java' -print0)
javac -encoding UTF-8 -d "$OUT/classes" "${STUBS[@]}" eternal/src/main/java/com/eternalengine/hyderabad/{PilotOsmSnapshot,PilotProjectedPreview,HyderabadPilotCommand}.java
java -cp "$OUT/classes:eternal/src/main/resources" HyderabadCommandMockTest
