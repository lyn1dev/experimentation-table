# Experimentation Table - (Works 26.1.2 and 26.2)

![Version](https://img.shields.io/badge/version-0.1.0-2ea44f)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-e34c26)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3--0.154.2%2B26.2-dbd0b4)
![Java](https://img.shields.io/badge/Java-25-f89820)
![License](https://img.shields.io/badge/license-MIT-blue)

An independent **Auto Experiment** mod for **Minecraft 26.2 / Fabric** (client-side
only) It solves all three Experimentation Table games automatically — **Chronomatron,
Ultrasequencer and Superpairs** — with no GUI of its own. Everything is configured
in-game via `/experiment` commands.

## Features

- **Chronomatron** — records the revealed (foil) note each round, then replays the
  whole sequence in order.
- **Ultrasequencer** — reads the revealed notes (dyes / lapis / bone meal), orders
  them by their count label, and replays them in order.
- **Superpairs** — tracks revealed pairs (by item + name) and clicks both slots of
  each match — can be disabled with `/experiment superpairs off` while
  Chronomatron / Ultrasequencer keep running.
- **Human-like Timing** — automated Gaussian intervals, 2D cursor distance scaling, and reaction delays.
- **No GUI** — the feature is toggled and configured entirely through
  `/experiment` commands; settings persist to `config/lyn1-experiment.json`.
- Game identification is purely client-side: the open screen title selects the
  solver, and clicks use standard **PICKUP (Left Click)** container interactions.

## Commands

| Command | Effect |
|---|---|
| `/experiment` | Show current status and settings |
| `/experiment on` / `off` | Enable / disable the solver |
| `/experiment superpairs on` / `off` | Enable / disable Superpairs auto-matching |

Confirmation example (green in chat):

```
[Lyn1-Experiment] Auto Experiment enabled        (in-game, shown in green)
```

## Configuration

Settings are saved to `config/lyn1-experiment.json`:

```json
{
  "enabled": true,
  "superpairs": true
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | true | Solver on/off |
| `superpairs` | boolean | true | Superpairs auto-matching on/off |

## Usage

1. Install [Fabric Loader 0.19.3+](https://fabricmc.net/use/) for Minecraft 26.1.2 or 26.2
   and drop `lyn1-experiment-0.1.0.jar` plus [Fabric API](https://modrinth.com/mod/fabric-api)
   into your `mods/` folder.
2. Launch the game, join Hypixel SkyBlock and place your Experimentation Table on
   your island (or use the Community Center one).
3. Open the table — the mod starts solving automatically (it is enabled by
   default; disable with `/experiment off` if needed).

## Building from source

Requirements: **JDK 25** and an internet connection.

```bash
git clone <repo-url>
cd lyn1-experiment
./gradlew build
```

The built jar is at `build/libs/lyn1-experiment-0.1.0.jar`.

## How it works

1. Every client tick, if the open screen is a `ContainerScreen`, its title is read
   and mapped to a game mode (`Chronomatron` / `Ultrasequencer` / `Superpairs (…`);
   `… Stats` titles are ignored.
2. A snapshot of all container slots (item id, count, foil, name) feeds the solver.
3. The solver decides the next click:
   - **Chronomatron**: slot 49 control; `glowstone` = preparation (latch reset),
     `clock` = reveal: a foil note within slots 10–43 is appended to the memory,
     then the whole memorized sequence is clicked back in order.
   - **Ultrasequencer**: slot 49 control; `glowstone` = reveal: notes in slots
     9–44 are sorted by their count label (count − 1 = replay index); `clock` =
     replay: click the mapped slots in order.
   - **Superpairs**: revealed items (not glass / clock / glowstone / black dye,
     with a name) are paired by item+name; both slots of each pair are clicked.
4. Clicks are spaced by dynamic humanized delays and sent via
   `gameMode.handleContainerInput(..., ContainerInput.PICKUP, ...)`.

## Human-like Timing & Click Randomization

The mod features a behavioral simulation engine to avoid metronome-like inputs:

- **Standard Input Packets**: Clicks use standard `ContainerInput.PICKUP` Left Click packets (`button = 0`), matching normal human player interactions.
- **Glowstone $\rightarrow$ Clock Reaction Delay**: When the game transitions from the reveal phase (`glowstone`) to the input phase (`clock`), the mod simulates human perception time using a Gaussian curve ($\mu = 330\text{ ms}, \sigma = 40\text{ ms}$, clamped to $[250, 480]\text{ ms}$).
- **Spatial Distance Scaling (Fitts's Law)**: Calculates the 2D Euclidean distance between successive slots on the $9 \times 6$ inventory grid, adding $16\text{–}24\text{ ms}$ per grid unit so distant slots take naturally longer to reach than adjacent ones.
- **Gaussian Jitter**: Every click interval incorporates standard normal variance ($\mathcal{N}(0, 25^2)\text{ ms}$, clamped to $[-45, +65]\text{ ms}$) on top of the configured base delay.
- **Contextual Hesitation**:
  - **Superpairs**: Introduces a $160\text{–}290\text{ ms}$ visual search pause when locating a new matching pair, followed by rapid pair confirmation.
  - **Sequence Recall**: Adds a 10% chance of a micro-hesitation ($45\text{–}95\text{ ms}$) during extended sequence playback.
- **Screen Open Orientation**: Pauses $300\text{–}500\text{ ms}$ upon opening any table GUI before initiating inputs.

## Project structure

```
src/main/java/dev/lyn1/experiment/
├── Lyn1ExperimentMod.java       # entrypoint: config load, tick hook, /experiment commands
├── AutoExperimentFeature.java   # tick pipeline: screen -> solver -> click
├── ExperimentConfig.java        # delay / enabled + JSON persistence
└── ExperimentSolver.java        # pure solver: Chronomatron / Ultrasequencer / Superpairs
src/test/java/dev/lyn1/experiment/  # solver unit tests (7 tests)
```

## Verification baseline

- 6 unit tests pass (`ExperimentSolverTest`).
- Release jar: `build/libs/lyn1-experiment-0.1.0.jar`
- SHA-256: `0CFFE52DF06E21B6A5BA736D0D58C303EBF106760AA33317F689BABF08111102`

## Disclaimer

This mod automates in-game input for the Experimentation Table minigames.
Automated play may be against the rules of Hypixel or other servers — check the
server rules and use it at your own risk. This is an independent educational
project, not affiliated with Hypixel Inc. or Mojang Studios.

## License

[MIT](LICENSE)
