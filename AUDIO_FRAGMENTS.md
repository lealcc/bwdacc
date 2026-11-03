# Connection UI, audio fragments and media actions

Android 0.11.0-dev (version code 12) and Desktop 0.7.0.

## Audio fragments

Open **Fragmentos > Áudios > Novo áudio**. Import an audio file or record with the
microphone, preview it, name it and save it. Categories expose previous/next
arrows and a **Ver todos** chooser. Audio fragments can be renamed, duplicated,
deleted and selected from the Lipsync audio picker. The fragment action can also
choose an existing image/video to start Lipsync.

- Audio duration: 0.2–30 seconds; imported files: at most 50 MB.
- Capture requires an explicit microphone tap and Android runtime permission.
- Recording is limited to foreground UI, stops at 30 seconds, and releases the
  microphone on leaving the app or dismissing the recording sheet.
- Capture buffers PCM in memory, then writes a WAV through `VaultFiles.output`.
  Imports, previews, saved fragments and recipe copies use authenticated vault
  APIs; no plaintext audio file or file-path playback is introduced.
- A submitted Lipsync recipe owns an independent encrypted audio copy. Deleting
  its original fragment does not break reuse. Duplicate fragments retain shared
  audio until its last owner is removed. Audio is covered by encrypted backup.
- Audio fragments do not expand into text tags. Private fragments follow the
  existing private-mode visibility rules.
- Server-side files are outside the mobile vault's protection, as with existing
  generation inputs. Recording alone never uploads audio to the server.

## Media menus and video LoRAs

Feed and gallery menus expose reuse, edit, animate (completed images), add to
project and delete. The private marking action appears only in private mode.
Extraction, character creation, recovery, Lipsync, upscale and extension live in
the media editor. Gallery deletion keeps its existing whole-set/all-app scope.

The editor exposes the LoRA chip for compatible image and video workflows.
Animation, video edits and extension use their own template's model family and
selection key. Choices and weights are applied before submission, captured in
the reusable recipe, and filtered when private mode is off. Installed adapters
still require the matching model folder (for H3: `h3/`). Standard H3 graphs gain a
model-only rgthree Power LoRA slot; existing Turbo/Ref2VA slots and structural
adapters remain intact.

## Visual changes

Pairing and Tailscale help use the rounded charcoal surfaces, Syne headings and
consistent action icons of the mobile app. Pairing retains certificate pinning,
PC approval and the existing camera/manual-code paths. Desktop uses matching
fonts, vector navigation icons, rounded controls and restrained neutral depth.
Pairing identity, devices, server configuration and network exposure are not
changed by this visual update.

## Validation

Synthetic Android fixture checks cover encrypted audio, metadata/backup rules,
reference ownership, H3 LoRA selection/weights/family filtering and preservation
of structural Turbo adapters. Microphone capture and release are exercised on
an Android 36 emulator. The pairing, Tailscale, audio editor, media menus and video
LoRA surfaces have dedicated visual fixture phases; user media is never copied
into these fixtures. Seven language packs and offline guides include the new UI.

Desktop pages are rendered with `--render` and the installed manager is reopened
to verify the existing ComfyUI and paired devices. These UI tests do not submit a
new GPU generation.
