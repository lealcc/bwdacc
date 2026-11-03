# Mobile vault preview

Implemented only in the commercial fork. Android 8 / API 26 or newer is required
for the seekable media path. Currently tested on the API 36 emulator, not on the
physical phone. This is development code, not an independent security assessment.

## Stored data and keys

- Originals, videos, thumbnails, character/style/location reference pictures,
  custom covers, imported workflows, recipes, deletion markers and library
  indexes use Tink 1.24.0 Streaming AEAD, AES256_GCM_HKDF_1MB.
- Drafts, reference descriptions, projects and generation settings use typed JSON
  in the same authenticated vault. Only language/reopen-settings flags remain in
  ordinary preferences.
- A random Tink keyset is wrapped with an AES-256 Android Keystore key, without a
  plaintext fallback. The wrapped keyset lives in noBackupFilesDir. Hardware-backed
  storage depends on the device; this implementation does not claim StrongBox.
- Associated data binds each file to its canonical relative path. Android's
  /data/data and /data/user/0 aliases are handled without allowing symlinks below
  the private files directory. Video .mp4.part staging shares the final video's
  identity so validation can precede its rename.
- Bitmaps decrypt from streams. MediaPlayer, MediaExtractor and
  MediaMetadataRetriever use a seekable MediaDataSource which authenticates
  segments on demand. There is no decrypted temporary movie on disk. The first
  and final segments are authenticated when opening a movie; every subsequently
  read segment is authenticated before returning its plaintext.
- AtomicFile protects replacements. Missing or corrupt keys block library access
  and preserve existing files. Corrupt library/deletion metadata cannot be treated
  as an empty library and then overwritten by the UI.

File names/IDs, ciphertext sizes and timestamps remain observable to an actor who
can read the app's sandbox. RAM and the Android media stack receive plaintext.
This does not defend against a compromised OS, a rooted live-device attacker,
malicious accessibility software, or an attacker executing code as the app.

## Migration and interruption

The app displays a gate while preparing storage on a worker thread, before
loading the library or allowing generation/cleanup. Migration first seals a
journal of legacy paths and SHA-256 hashes. Each original is encrypted atomically
and verified. On resume, completed ciphertext is authenticated and untouched
plaintext must still match its original hash. The app does not silently
reinterpret corrupt ciphertext as new plaintext.

Both legacy Activity preference names are handled: MainActivity and
com.leal.comfyremote.MainActivity. The latter is used by the commercial package
because its application ID differs from the Java namespace. Their plaintext XML
is removed only after authenticating the encrypted replacement. The completion
marker is committed before the journal is retired.

Migration needs free space for the largest file's encrypted replacement plus a
small journal. Lack of space or damaged source data blocks migration while
preserving files. Interrupted initialization before a wrapped keyset is committed
may require diagnosis; it never silently replaces an existing Keystore key.
Flash storage does not provide a reliable secure-erasure guarantee for previously
written plaintext. Existing pre-vault copies are outside this vault's protection.

## Recovery and server limits

[Portable encrypted backup](BACKUP.md) is now available in App settings. It uses
an independent random recovery code and reencrypts into an empty destination's
existing vault key. It never exports the source device key or Desktop credentials.
Without a valid backup and its code, uninstall, app-data clear, device loss or
Keystore loss can still make the library unrecoverable. There is no biometric
lock yet. Do not recommend clearing app data to fix a vault error. Automatic OS
cloud backup and device transfer remain excluded in both legacy and Android 12+
backup rules; the new backup is user-initiated.

The vault does **not** remove ComfyUI inputs/outputs, encrypt the Desktop model
store, or prevent a generation server from reading references while processing.
Acknowledged delivery and server retention/cleanup still need implementation.
Existing explicit reference uploads
decrypt in memory directly into the already authenticated Desktop connection.

## Validation

Run `tests/android-vault/run.ps1` with ANDROID_HOME and JAVA_HOME configured. It
refuses physical phones and uses a separate synthetic package/Keystore UID.
It builds the production Java sources, runs ten phases across fresh processes,
and exercises:

- Legacy pictures, prompts, settings, workflow and recipe migration; no plaintext
  fallback; typed preferences and ordered apply/commit.
- Image decoding; movie metadata/frame extraction; MediaPlayer autoplay and seek;
  MediaExtractor seek; random reads across 1 MB segment boundaries and EOF,
  including empty files.
- Corruption, truncation, swapped-file AAD, path escape and symlink refusal.
- Failed atomic replacement, recipe authentication, durable deletion and sanitize,
  including encrypted .new/.bak remnants.
- Corrupt keyset and missing Keystore key: refusal without replacing media/keys.
- Killing the process during a 128 MB replacement, then resuming and checking the
  complete plaintext digest, preferences, staging cleanup and journal retirement.

Results: `desktop/artifacts/android-vault-tests.json`. The complete run passed
74 assertions/aggregate checks; one aggregate runs the existing generation-storage
regression suite. This count is not a security certification.

Actual commercial APK upgrade was also exercised on the emulator: imported a
synthetic image using the old build's Android photo picker, selected Klein, then
installed the vault build over it. The image, selected workflow and reference
tray survived migration. Entered a synthetic draft in the vault build, restarted
the process and verified it returned; opened the full image viewer successfully.
Screenshots/XML evidence are in `desktop/artifacts/vault-ui-*`.

The physical phone and per-user Windows Desktop were left on the previous pairing
build for Leal's next test. Their preserved artifacts are in
`preview-baselines/pairing-6e9ad66/`, ignored by Git. Downgrading a populated vault
to the old plaintext app is unsupported; use a separate test device/profile.

## Primary implementation references

- [Tink Streaming AEAD](https://developers.google.com/tink/streaming-aead)
- [Tink Android setup](https://developers.google.com/tink/setup/java)
- [Tink seekable channel contract](https://github.com/tink-crypto/tink-java/blob/v1.24.0/src/main/java/com/google/crypto/tink/StreamingAead.java)
- [Android MediaDataSource](https://developer.android.com/reference/android/media/MediaDataSource)
- [Android backup rules](https://developer.android.com/identity/data/autobackup)

Tink and Gson are pinned in `libs/dependencies.json`, checked by the APK build,
and accompanied by their Apache-2.0 license files. Test video is generated using
FFmpeg testsrc2, 320x180, 15 fps, 2 seconds, H.264, no audio; it contains no user media.
