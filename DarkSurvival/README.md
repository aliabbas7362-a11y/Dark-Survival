# Dark Survival

Blind-friendly audio-first battle game (PUBG / Free Fire style) — **native Android, Kotlin + Jetpack Compose**.
Designed by **Abbas Ali**. No HTML / CSS / JS.

## Stack
- Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00), Material 3
- MVVM: ViewModel + StateFlow
- Gradle Kotlin DSL, minSdk 24 / target 34, Java 17
- Audio: SoundPool (SFX) + dedicated MediaPlayers (ambience/siren/briefing) + TextToSpeech

## Current state — v2.9 (versionCode 20)

### 5-Zone Linear Map (day-to-night progression)
| Zone | Name | Progress | Ambience |
|---|---|---|---|
| 1 | Helicopter Drop Point (safe, no spawns) | 0–14% | Early-morning birds |
| 2 | River Bank | 15–39% | Mid-day stream (distance-faded by progress) |
| 3 | Deep Forest | 40–64% | Late-afternoon cicadas |
| 4 | Rocky Ridge | 65–84% | Dusk mountain wind |
| 5 | Enemy Camp | 85–100% | Midnight tension + INTRUDER SIREN (deep air-raid, distant/low) |

- PUBG-style helicopter + parachute intro at match start
- HQ walkie-talkie briefing on dedicated MediaPlayer, radio-filtered (bandpass + distortion + static)
- 3.0s smooth dual-channel ambient crossfade at every zone boundary
- River ambience volume attenuates with distance from the stream

### Combat (12 enemies, fixed zone table)
- Zone spawns: 1 patrol / 2 guards / 3 rushers / 3 tactical / 4 tank heavies
- AI state machine: PATROL → ALERT (hears footsteps) → HUNT (hears gunfire, 1.2x pursuit)
- Per-enemy random weapon: Knife / Axe / Bat / Pistol (unique range, damage, speed)
- AI archetypes: Rusher (1.6x speed), Tactical, Tank (2.2x HP)
- Grenade hazard: 8% chance when engaged — "Grenade out!" voice + pin + lane-panned bounce,
  pulsing haptic alert, 2.0s dodge window, 30 AOE damage + 3s tinnitus on failure
- Kill confirmation: "Enemy eliminated! N remaining." + death grunt + 120ms haptic pulse
- Win conditions: clear all 12 enemies OR reach extraction at Zone 5 end with camp guards down
- Out-of-bounds wall at 100% progress

### Controls (v2.9 — TalkBack-safe)
- LEFT 70% gesture area: `clearAndSetSemantics` (TalkBack never steals swipes, no focus rects)
  - Finger DOWN = walk forward immediately (no double-tap, no delay)
  - Quick 1-finger swipe left/right = lane change with WRAP-AROUND (L→C→R→L…)
  - Multi-part swipes: Right-then-Left = move left; Left-then-Right = move right;
    Down-then-Up & HOLD = walk forward; Up-then-Down & HOLD = walk backward
  - Finger UP = movement stops AND the in-flight footstep stream is cut instantly
  - Drag down = walk backward; drag up again = forward
- RIGHT 30%: FIRE (tap single / hold auto-fire), RELOAD (tap / hold = ration),
  MODE (weapon cycle), CROUCH (the ONLY crouch/stand toggle)
- Crouch locks all walking (speed 0) until explicit stand-up; sneak = 0.6x enemy detection
- Sensor Mode (Settings toggle, persisted): calibrated neutral position, 9-degree pitch
  deadzone for forward/back walking, 15-degree roll for lane changes,
  "Calibrate Sensor" button resets neutral; auto-calibrates when switched ON
- Root splitMotionEvents: hold movement with the left hand while tapping buttons with the right

### Audio
- Player footstep: single verified heavy-boot clip, strict 500ms cadence, instant stream cut on stop
- Enemy footsteps: heavy boot, lane-panned (left ear = left lane) + distance attenuation
- Enemy idle foley: hostile grunts, gear rustle, heavy breathing — panned + attenuated
- Full SFX set: pistol/shotgun/sniper/MG, reload, pickup, eliminate, death, heartbeat,
  win/lose, heli intro, grenade sequence (call/pin/bounce/explosion/tinnitus), crouch/stand
- Announcements master toggle (Settings, persisted) + TTS-level dedupe (no repeated lines)

## Build in Android Studio
1. Open this folder in Android Studio (Ladybug or newer).
2. Wait for Gradle sync.
3. Run ▶ on the `app` configuration, or: `./gradlew assembleDebug`

## Project structure
```
app/src/main/java/com/blindtechabbas/darksurvival/
├── MainActivity.kt        (sensors, briefing/siren/ambience wiring, lifecycle)
├── audio/
│   ├── SoundManager.kt    (SoundPool, crossfade, siren, briefing, footstep engine)
│   └── TtsManager.kt      (debounced announcements)
├── game/
│   ├── GameModels.kt      (weapons, lanes, zones, enemy AI archetypes, spawn tables)
│   ├── GameState.kt       (UI state: counters, grenade, sensor offsets)
│   └── GameViewModel.kt   (engine loop, waves, grenade machine, win conditions)
├── ui/
│   ├── components/ActionButton.kt
│   ├── navigation/AppNavHost.kt (back handling)
│   └── screens/ (MainMenu, ChapterSelect, Match, Settings)
└── util/ (Zone.kt, CrashLogger.kt)
app/src/main/res/raw/      (24 audio assets: footsteps, weapons, zones, grenade, voices)
```

## GitHub Actions
`.github/workflows/android-build.yml` builds a debug APK on every push.

## License / credits
Designed by Abbas Ali. SFX generated with ElevenLabs; radio filters and boosts via ffmpeg.
