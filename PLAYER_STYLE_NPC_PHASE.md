# Player-Style NPC Phase

## Requirement

User wants Eternal Engine people/NPCs to use player-style humanoid bodies, not vanilla villager bodies.

References read before coding:

- Eternal Engine villager/bot/current-affairs files
- CreatureChat README and source layout
- VillagerAgent README and source layout

## Reference interpretation

### CreatureChat
Use as reference for:

- entity conversation memory
- relationship/friendship state
- reaction triggers
- multiplayer-synced dialogue
- prompt style where NPC chat is generated from facts

Do not copy Fabric code directly into Forge.

### VillagerAgent
Use as reference for:

- multi-agent task graph
- task manager / controller / data manager pattern
- agent-to-agent collaboration
- task decomposition concepts

Do not let agents directly mutate the Minecraft world. In Eternal Engine, agent plans must become proposals validated by server systems.

## Implemented in this phase

Added a custom player-shaped entity body:

```text
src/main/java/com/eternalengine/entity/HumanNpcEntity.java
src/main/java/com/eternalengine/entity/EternalEngineEntities.java
src/main/java/com/eternalengine/client/HumanNpcRenderer.java
src/main/java/com/eternalengine/client/EternalEngineClientRenderers.java
```

Integrated it into:

```text
src/main/java/com/eternalengine/EternalEngineMod.java
src/main/java/com/eternalengine/villager/VillagerManager.java
src/main/java/com/eternalengine/villager/VillagerVirtualizer.java
src/main/java/com/eternalengine/commands/EternalEngineCommands.java
```

## Behavior now

Existing commands keep their names for compatibility:

```mcfunction
/ee villager spawn-test <civId> <role>
/ee villager vip-spawn-test <vipId>
/ee villager nearby
```

But physical spawns now use `HumanNpcEntity`, rendered with a Steve/Alex-style player model, not vanilla `Villager`.

## Current-affairs rule

NPC dialogue still comes from server facts via:

```text
CurrentAffairsEngine
BotCurrentAffairsService
VillagerBehaviorSystem
```

The NPC must explain year/area/region/country/village facts from server facts only.

## Later phases

- Add skin/profile data for historical figures and cultures.
- Add CreatureChat-style memory and relationships.
- Add VillagerAgent-style task graph and group coordination.
- Add chat bubbles/network sync if needed.
- Rename public commands from `villager` to `npc` while keeping aliases.
