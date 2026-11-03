# Contributor guidance

- Read README.md and docs/SETUP.md before changing build or connection behavior.
- Keep credentials, signing keys, model weights, runtime folders and user media out of Git.
- Preserve authenticated pairing, certificate pinning, scoped gateway routes and revocation.
- Use VaultFiles for app-local media; do not introduce plaintext playback caches.
- Do not start, stop or modify a user's ComfyUI as part of ordinary build/test commands.
- Keep managed and externally owned ComfyUI installations separate.
- Use synthetic fixtures. Live generation tests are opt-in and must not cancel unrelated jobs.
- Preserve the existing unified private-mode/cover behavior; do not build reviewer-specific behavior.
- Maintain workflow hashes, upstream sources and third-party license notices when changing catalogs.
- Run checks relevant to the change and document their limits.
