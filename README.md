# Kitten Care

Reliable kitten feed and pet reminders for RuneLite.

## Why this plugin

Older kitten timers can drift or break after client updates. This plugin uses confirmed game signals and the live follower state instead of assuming care timers across sessions:

- Starts feed and attention tracking only after a confirmed care action or a real warning message.
- Carries confirmed feed and attention state across world hops, but resets on logout or when your follower is no longer out.
- Uses the actual OSRS kitten windows:
	- Hunger at 24 minutes, then very hungry after 3 more, then runaway after another 3.
	- Attention warning after 25 minutes by default, 18 minutes after one stroke, 25 minutes after repeated strokes, and 51 minutes after wool play.
- Confirms actions from chat and direct interaction events.
- Tracks guessed age from the Guess-age interaction message and advances it while the follower remains out.

## Features

- Feed timer countdown.
- Pet timer countdown.
- Age and time-to-adult that continue updating after Guess-age.
- Exact hunger and attention stage tracking.
- Configurable reminder lead time.
- Repeating reminders while due/overdue.
- Optional chat + desktop notifications.
- Optional overlay with urgent red warning states and a follower outline that intensifies as care becomes urgent.
- Optional warning-message sync when game says kitten is hungry/needs attention.

## Configuration

- Feed reminder lead (seconds)
- Pet reminder lead (seconds)
- Repeat reminder every (seconds)
- Show game chat reminders
- Desktop notification mode
- Show countdown overlay
- Sync from warning messages
- Debug mode

## Development

- Java 11
- `./gradlew run` to launch RuneLite with this plugin
- `./gradlew build` to compile/package

## Plugin Hub submission notes

- Keep `runeLiteVersion = 'latest.release'` in `build.gradle`.
- Ensure repository has a `LICENSE` file (BSD 2-Clause recommended).
- In your Plugin Hub PR, add only one marker file under `plugins/` with:
	- `repository=https://github.com/<you>/<repo>.git`
	- `commit=<full 40-char hash>`