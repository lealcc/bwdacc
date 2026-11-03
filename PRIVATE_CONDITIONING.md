# Local image conditioning profiles

The owl enables per-request profiles for Ideogram 4, Ideogram 4 TurboTime, Krea 2
RAW, Krea 2 Turbo, and Krea 2 Turbo 4-step. Ideogram uses the installed Snofs V1
LoRA; Krea uses Rebalance. Public/SFW requests retain their original graphs.
These adjustments target local models; they do not remove the prompt agent's
format validator or change an online provider's moderation settings.

## Implementation

`PrivateConditioning.apply` runs after the agent, boxes and memory profile are
prepared. Krea receives a positive-conditioning adapter. Ideogram receives a
model-only Snofs loader at weight 1.0 on both the conditional and unconditional
guider inputs; TurboTime has only the conditional branch. Existing active Snofs
on a branch is respected, including its user-selected weight, without stacking
another copy. Text encoders, negative/zero conditioning, CFG, steps, seeds,
structural acceleration adapters and other user LoRAs remain unchanged. The
final POST reapplies the current owl state so an already prepared graph cannot
retain an owned adapter after switching back to public mode.

Insertion is idempotent. Removal follows the adapter's original source and only
removes nodes bearing the app's ownership marker. Legacy recipe extraction strips
the adapter; reusing a generation selects the profile again from the current mode.

The existing `ConditioningKrea2Rebalance` node at revision
`9ab5315e6aa8ec8cd14cc5354739d938c6fb101b` uses the author's 12-tap Krea preset.
The package and the Ideogram LoRA are recorded as optional catalog dependencies.
The installed `ideogram4/ideogramSnofsV1.safetensors` is 1,123,105,160 bytes and
matches SHA-256 `e8fd6d2431d55f6d1efde56af5f54e7a24fd66837762dbdf471a2d79ef159b0b`.
The mirror download is pinned; redistribution terms still require original-author
verification rather than relying on a mirror's generic model-card license.

Krea's global multiplier is explicitly `1.0`, rather than the upstream default of
`4.0`. The first real tests exposed excessive saturation/artifacts at 4x with
our existing CFG settings. Layer ratios supply the adjustment; it
does not force users' CFG or sampler controls to a different value.

## Sources and limitations

- [Rebalance-Pack](https://github.com/nova452/Rebalance-Pack), preset source revision
  `53c147c72c2fbd9444765af87118caee81a26d01`:
  [Krea weights](https://github.com/nova452/Rebalance-Pack/blob/53c147c72c2fbd9444765af87118caee81a26d01/krea2.py).
- [Snofs file and checksum](https://huggingface.co/Giovska97/lorahot1/blob/e67730e025204e27febf3b9ed8c41a7cb34b7fc7/ideogramSnofsV1.safetensors).
- [ComfyUI's Ideogram explanation](https://blog.comfy.org/p/ideogram-4-day-0-support-in-comfyui)
  distinguishes model-level refusal from a ComfyUI output filter. Rebalancing is
  not a guarantee that every prompt will be accepted.
- [Official workflow](https://github.com/Comfy-Org/workflow_templates/blob/main/templates/image_ideogram4_t2i.json)
  documents higher false-positive rates with plain-text prompts. The app already
  uses KJ's structured JSON builder even when no boxes are selected; smoke tests
  must preserve that path.

The tested Ideogram alternative is Snofs, not Rebalance or sigma interpolation.
Neither of those resolved the fixed-seed, non-sensitive teapot fixture with the
normal model. Realism Engine V2/V4 also failed that case. Snofs on both branches
resolved it without changing the sampler or adding steps. This does not prove
that all future model refusals are eliminated.

## Validation

`tests/PrivateConditioningTest.java` covers five variants, both supported memory
profiles and both agent states (20 combinations), including mode reversal,
recipe reuse, negative paths, structural LoRAs, ID collisions and live dependency
schemas. It also checks manually selected Snofs weights, duplicate prevention,
public-mode LoRA removal and idempotence after Windows path normalization.
Existing Ideogram, Krea and prompt agent regression suites also pass.

`tests/live_private_conditioning.py` runs only synthetic teapot fixtures, one job
at a time, and refuses to start with another queued job. It neither restarts the
server nor reads user media. Artifacts are under
`desktop/artifacts/private-conditioning/`; completed execution is distinct from
visual correctness, so generated images must also be inspected.

Real renders validated on the existing local ComfyUI, 2026-11-02:

| Workflow | Prompt ID | Result |
| --- | --- | --- |
| Ideogram 4 + Snofs | `67dbb7cf-8ab2-4e62-9e99-9963eee2ca29` | Image generated; previous refusal absent |
| Ideogram 4 TurboTime + Snofs | `883cd82a-d9ac-499e-b08d-c4367f5d3fcf` | Image generated |
| Krea 2 RAW + Rebalance 1x | `8e48f6d7-b148-4f13-9150-1993085c696b` | Image generated; no 4x saturation artifacts |
| Krea 2 Turbo + Rebalance 1x | `12333c32-f5b9-40b9-919f-7e20e1a683db` | Image generated |
| Krea 2 Turbo 4-step + Rebalance 1x | `4fa1700c-3111-49a8-8f7b-0c7ce0ddc179` | Image generated |

All fixtures were non-sensitive product photographs. These are integration and
visual smoke tests, not a comprehensive content acceptance benchmark.
The exported release graphs were compared recursively with these rendered
graphs: execution trees match for all five variants, ignoring node IDs, titles
and the QA output prefix. APK version: `0.11.6-dev` (18).
