# Mobile introduction and tool guides

The commercial Android app includes a five-page, skippable introduction and eleven
offline tool guides under **Settings > App > Tutorials**. The guides cover pairing,
generation, Fragments, composition/control maps, editing, video tools, MuseTalk lipsync, LoRAs and
the prompt agent, Prompter, library/projects, and storage/backups.

The introduction appears once after this feature is installed, including for
existing users. Completing or explicitly skipping it records a non-sensitive UI
flag in the existing `interface` preferences. Unfinished introductions resume at
their last page; rotating/recreating the activity also restores an open guide and
its search. Replaying the introduction from Tutorials never resets completion.
Reading guides does not change workflows, pairing, privacy controls, or media.
The optional Connect action opens connection settings without pairing on its own.

Content is bundled in `app/src/main/assets/tutorials/` for all seven app languages.
Guides describe capabilities that exist and call out server dependencies. Control
blocks are explicitly documented as not yet connected to generation workflows.
No personal media, remote screenshots, network requests, analytics, or permissions
are used by the help UI. System bars remain visible; transitions respect Android's
animation preference, and the body scrolls independently of navigation buttons.

Validation: the disposable Android visual fixture checks all language packs,
accent-insensitive search, unfinished progress, completion, replay, state
restoration, and language-settings precedence. The UI was visually checked on the
API 36 emulator with synthetic content only.
