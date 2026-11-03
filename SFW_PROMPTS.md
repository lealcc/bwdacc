# Silent SFW prompt filtering

Android 0.11.1-dev (version code 13) adds a case-insensitive literal word list in
`SfwPromptFilter.WORDS` and `SfwPromptFilter.CJK`. It runs only when the existing
private/NSFW mode is off. Unlocking the owl alone does not enable NSFW mode.

The composer and saved user draft are unchanged. There is no toast, warning or
extra setting. Only text consumed by the generator is filtered. Original prompt
recipes remain available for editing/reuse; the next submission follows the mode
active at that time.

- Positive CLIP text and H3 image/video prompts are filtered at submission.
- Expanded Fragment text and descriptions assembled by the box workflow pass
  through the same positive prompt input.
- Agent input is filtered before rewriting. Linked text, including Agent output,
  uses ComfyUI's native `RegexReplace` node immediately before positive text
  encoding/H3 conditioning. No additional model, custom node or server restart
  is required on the current server. Older ComfyUI versions need this native node.
- Negative prompts, asset/model filenames, LoRA identifiers and metadata are not
  scrubbed. A shared text source is wrapped only at its positive consumer.
  Ambiguous imported encoders that also feed a nonzero negative branch are
  excluded to preserve their negative conditioning.
- Owned runtime filter nodes are removed when rebuilding recipes or submitting
  with NSFW mode enabled. Repeated submissions do not accumulate filter nodes.

This is lexical removal, not semantic moderation or image/video classification.
It can have false positives, miss unlisted terms and cannot guarantee SFW output.
It does not inspect reference images, audio or generated media.

Validation covers whole-word boundaries, case, accents, CJK terms, complete
bypass in NSFW mode, negative/shared-source preservation, media filenames,
Agent/box links, idempotence, recipe cleanup and the included Moss, Klein and H3
workflows. The exact runtime pattern is also evaluated with Python `re.sub`, as
used by ComfyUI's native node.
