# CLAUDE.md — MentalCoach (Android)

This file tells Claude Code how to work on this Android app. Keep it up to date when architecture or conventions change.

## What the app is

MentalCoach is an on-site psychological **coaching companion** used on an Android tablet in a wellbeing lab. It is coaching, **not therapy**: it never diagnoses.

The coach is the Strata LLM (OpenAI-compatible) at `http://bonismp.ddns.net:47832/v1`, called **directly from the app** (see "LLM contract"). Use the selected Amazon tablet for debugging if required.

The visit flow (the product spec, do not change it without asking):

1. **Check-in**: the visitor enters a first name, takes a profile photo (used as the logbook cover), describes their current mood in their own words (typed or spoken), and confirms a consent note. They can optionally load the context `.md` file from a previous visit.
2. **Instructions**: the coach (the Strata LLM) reads the mood (through speech recognition) and shows a short summary, 2–4 feelings, and mood and energy scores from 1 to 10. It then presents the first exercise with an intro and steps. Everything the coach says is **spoken aloud** with text-to-speech.
3. **Exercise**: a timer, the steps, and a live camera. The visitor takes **1–5 photos**.
4. The visitor ends the exercise with a button.
5. **Reflection chat**: the coach asks how they feel now. The visitor answers by speech-to-text.
6. After about two answers the coach proposes a **follow-up exercise** (repeat steps 2–5), or proposes ending once **N** exercises are done (default N = 3, configurable). The visitor can end at any time.
7. **Logbook**: the coach writes a personal memory of the visit. For each exercise the visitor either picks up to 2 photos, or taps "Don't care" and the app picks 2 (the first and last; just one if only one was taken). The app exports:
   - the logbook as a **PDF** (and/or a self-contained HTML file) to take home
   - a **context markdown file** describing what they did and how they feel, to load at the next visit

**Later, not in the MVP:**
- Recognise returning visitors from their profile photo using face recognition. This is biometric data, so it must run **on the device only**, need explicit opt-in consent of its own, and allow deletion. Design it with the lab before building it.
- At home, the visitor describes their mood and the app suggests revisiting one of their recorded experiences.

**Crisis handling:** if the check-in or the chat shows signs of acute risk, the coach response contains `crisis: true`. The app must then stop the exercises, show a calm message and the help contacts from Settings, and tell the visitor to talk to lab staff. Never continue the flow after `crisis: true`.

## Reference implementation

A working web MVP exists in the sibling project `mentalcoach/` (Node/TypeScript server + vanilla JS front end). It is **not** used at runtime: port its prompts and response shapes from `server/coach.ts` and its exercise library into the app. Its front end (`public/app.js`) is the behavioural reference for the screens. When unsure how a step should behave, read `mentalcoach/public/app.js` and match it.

## Architecture

- **Project setup:** created in Android Studio as **New Project → Empty Activity** (Kotlin, Jetpack Compose, Material 3), minimum SDK API 26, with the **Kotlin DSL** (`build.gradle.kts`). Dependencies go in the version catalog `gradle/libs.versions.toml`. Do not add Groovy build files.
- **Native Kotlin app, Jetpack Compose, single Activity.** minSdk 26, targetSdk/compileSdk = latest stable. The device is a tablet in landscape and portrait, so design for ≥ 600dp width but keep phone widths usable.
- **MVVM + unidirectional data flow:** `ViewModel` holds a `StateFlow<VisitUiState>`, and screens are stateless composables. Navigation uses Navigation Compose with one route per flow step: `checkin`, `intro`, `exercise`, `chat`, `logbook`, `crisis`, `settings`.
- **DI:** Hilt.
- **Networking:** Retrofit + OkHttp + kotlinx.serialization, talking to the Strata LLM (see "LLM contract"). Use timeouts of at least 60 s, because LLM calls can take several seconds.
- **Camera:** CameraX (`Preview` + `ImageCapture`), with a front/back toggle. Downscale photos to max 1024 px on the long side, JPEG quality ~82, and store them in app-private storage (`filesDir/visits/<visitId>/`).
- **Speech out:** `android.speech.tts.TextToSpeech`. Wrap it in a `Speaker` class with `speak(text)` and `stop()`, a locale from the visit language, and a user-selectable voice and rate. Queue sentence by sentence and stop speaking when the user starts talking or leaves a screen.
- **Speech in:** `SpeechRecognizer` with `RecognizerIntent`. Prefer on-device recognition (`EXTRA_PREFER_OFFLINE`, or `SpeechRecognizer.createOnDeviceSpeechRecognizer` on API 31+) for privacy. Typing must always work as an alternative.
- **Persistence:** Room for the current and past visits (visit, exercises, chat turns, photo file paths, selected photos). A visit must survive process death and resume on the screen where it stopped. Settings live in DataStore.
- **Export:** generate the logbook PDF with `android.graphics.pdf.PdfDocument`, or render HTML and print via `PrintManager`. Write the context `.md` and share or save both through the Storage Access Framework (`ACTION_CREATE_DOCUMENT`) or a share sheet (`FileProvider`). Import the previous context `.md` with `ACTION_OPEN_DOCUMENT`.
- **i18n:** English and German (informal *du*) via `values/strings.xml` and `values-de/strings.xml`. Never hard-code user-facing strings. Coach speech arrives from the LLM already in the visit language (the prompt asks for it).

Suggested package layout (`com.mentalcoach`):

```
data/      api/ (Retrofit service + DTOs), db/ (Room), repo/ (VisitRepository), settings/
domain/    models (Visit, ExerciseRun, ChatTurn, Exercise), use cases if logic grows
ui/        checkin/, intro/, exercise/, chat/, logbook/, crisis/, settings/, components/, theme/
media/     CameraController, Speaker (TTS), Listener (STT), PdfExporter, MarkdownExporter
```

## LLM contract

The app calls the Strata LLM directly. There is no backend server.

- **Endpoint:** `POST {STRATA_BASE_URL}/chat/completions` (OpenAI-compatible), with `Authorization: Bearer <key>`. Put the base URL in `BuildConfig.STRATA_BASE_URL`, and inject the key at build time into `BuildConfig.STRATA_API_KEY` from the `STRATA_API_KEY` env var or from `local.properties`. Never commit the key.
- **Known risk (accepted):** anyone with the APK can extract the key. That is acceptable for a single lab tablet only. Do not distribute the APK.
- The host is plain HTTP, so allow cleartext **for this domain only** in `network_security_config`.
- Use timeouts of at least 60 s.
- Requests are **text only: never send photos**.

`CoachApi` (in `data/api/`) has three operations. Each builds its prompt on the device (ported from `mentalcoach/server/coach.ts`), asks for JSON output, and parses the reply with kotlinx.serialization. Each takes the session state:

```jsonc
{
  "name": "Ana", "lang": "en" | "de", "maxExercises": 3,
  "mood": "free text from check-in",
  "previousContext": "optional .md text from last visit",
  "checkin": { "summary": "...", "energy": 5, "mood": 5, "feelings": ["tense"] },
  "exercises": [ { "id": "box-breathing", "title": "Box breathing", "startedAt": "ISO", "endedAt": "ISO",
                   "chat": [ { "role": "coach" | "user", "text": "..." } ], "moodNow": 6 } ]
}
```

| Operation | Returns |
|---|---|
| `checkin` | `{ summary, feelings[], energy, mood, crisis, greeting, exerciseId, exerciseIntro }` |
| `reflect` | `{ reply, crisis, moodNow \| null, suggestion: null \| { action: "next" \| "end", exerciseId \| null, why } }`. Call it after appending the user's chat turn. |
| `logbook` | `{ title, opening, entries: [{ exerciseIndex, narrative, takeaway }], closing, context: { summary, whatHelped[], howTheyFeelNow, suggestionsNextSession[], cautions[] } }` |

- **Exercise library:** bundle it as `assets/exercises.json` (id, minutes, title/steps/photoPrompts per language). Reject any `exerciseId` the LLM returns that isn't in the library: fall back to a valid one.
- **Crisis:** the prompts must ask for a `crisis` flag. Also run a simple on-device keyword check on the visitor's text as a backstop. If either one triggers, go to the crisis screen.
- **Errors:** network errors, HTTP errors and unparseable JSON show a message, keep the user's input, and let them retry. Retry once automatically on invalid JSON.
- **Demo mode:** a debug setting that swaps in a `FakeCoachApi` with scripted replies (the same fake the tests use) and shows a "Demo mode" badge.

## Context markdown format

Build it on the device, mirroring `contextMarkdown()` in `mentalcoach/public/app.js`. It contains a header list (visitor, date, language, exercises done/planned, logbook title), then Summary, How they feel now, What helped, Suggestions for the next session, Cautions (if any), Check-in (the visitor's words as a quote, plus the coach's reading), and Exercises (per exercise: time, photo counts, mood afterwards, full chat). The next check-in sends this file back as `previousContext`.

## Privacy and safety rules (must not be broken)

- Photos stay on the device. They are not uploaded, not put in logs, and not added to the context `.md`. The profile photo only appears on the logbook cover of exported files.
- Request the `CAMERA` and `RECORD_AUDIO` permissions at the point of use, with a clear explanation. The app must work, minus that feature, if a permission is denied.
- Delete a visit's data (Room rows and photo files) when the visitor taps "Finish and start a new visit", after a confirmation. Do not keep visits around silently.
- No analytics or crash reporting that could capture visitor text or images.
- Never use exercises outside the bundled library, and never skip the crisis screen.
- Backups are disabled: `android:allowBackup="false"`, and `data_extraction_rules.xml` / `backup_rules.xml` exclude everything. Keep it that way.

## Build and test

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # install on the connected tablet/emulator
./gradlew testDebugUnitTest      # JVM unit tests (ViewModels, exporters, DTO parsing)
./gradlew connectedDebugAndroidTest   # instrumented/Compose UI tests (needs a device)
./gradlew lint ktlintCheck       # static checks
STRATA_LIVE=1 ./gradlew testDebugUnitTest --tests '*StrataLiveTest*'   # opt-in: real calls to Strata (invented text only)
```

Set `STRATA_API_KEY` (env var or `local.properties`) before building for real coaching. Without it, use demo mode.

Testing expectations:
- Unit-test ViewModel state transitions for the whole flow against `FakeCoachApi`: check-in → intro → exercise → chat → next/end → logbook, and the crisis branch from both check-in and chat.
- Unit-test the markdown exporter (golden file) and the photo selection rules (max 2; defaults to the first and last photo).
- After a test failure, re-run only the failing tests while fixing them.

## Conventions

- Kotlin official style, ktlint. Use coroutines and Flow; no RxJava, and no `GlobalScope`.
- Composables take state and lambdas only, with previews for each screen in EN and DE.
- Large touch targets (≥ 56dp primary buttons) and a calm palette (deep green `#2F5D50`, warm accent `#D98C5F`, paper background `#F4F1EA`). Support dark theme and respect the system font scale and reduced motion.
- Keep coach copy out of the UI code: everything the coach says comes from the LLM (prompts live in `data/api/`), and the UI copy lives in string resources.

## Exercises
List of example exercises to propose:
- 1. Yoga
- 2. Box Breathing
- 3. Push-Up
- 4. Power Posen
- 5. Silent Disco
- 6. Applausraum
- 7. Spiegel
- 8. Fragen Stellen

## Ziele
Die Ziele der Erfahrung. Die Zahlen geben die Erlebnisse an die diesen Zielen zugeordnet sind.
- Positive Lebenseinstellung
  - 5
- Beziehung stärken
  - 8
- Entspannen
  - 1, 2
- Visualisieren
  - 6
- Bewegung
  - 4
- Emotionen kennenlernen
  - 7


## Open questions

- None at the moment.
