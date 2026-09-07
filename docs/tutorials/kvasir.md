# Tutorial: KVASIR — the AI error explainer

KVASIR is a rack device that reads your last failed run and asks your
AI — Claude, ChatGPT or Gemini — what went wrong. It's AI assistance through the rack's
metaphor: one button, a clear consent gate, and an honest LCD — no
project files or secrets are sent, only the bounded failure context.

![KVASIR explaining a real failed run: the consent-gated diagnosis on the faceplate and the full fix steps in the viewer](../images/kvasir-explain.png)

## Before you start

You need an API key from one of the three providers KVASIR speaks:
Anthropic (Claude), OpenAI (ChatGPT) or Google (Gemini). Press **KEY…**
on the faceplate to pick the provider and store its key in the OS
keychain, or export the provider's environment variable —
`ANTHROPIC_API_KEY` / `CLAUDE_API_KEY`, `OPENAI_API_KEY` /
`CHATGPT_API_KEY`, or `GEMINI_API_KEY` / `GOOGLE_API_KEY`. The provider choice covers every
KVASIR face and also lives in Options ▸ Rack & Cloud.

## Steps

1. **Cause a failure.** Run something that fails — a build with a syntax
   error, a test that throws. The rack's flight recorder captures the
   command, exit code, and up to five sampled error lines.

2. **Mount KVASIR** from the palette (OBSERVE category) and press
   **EXPLAIN**.

3. **Grant consent (first time).** KVASIR has its own one-time consent
   dialog, per provider, naming the vendor that receives the data and
   spelling out exactly what leaves your machine: the failing
   command, its exit code, ≤5 error lines, the device name, and the
   project name — and nothing else (no source, no environment, no
   secrets). Workspace Trust guards *running* code; this outward data
   flow gets its own gate.

4. **Read the verdict.** A short diagnosis appears on the multi-line LCD;
   the full explanation opens in a popup. The **MODEL** knob picks
   FAST (default) or DEEP — Haiku / Sonnet, GPT-5 mini / GPT-5, or
   Gemini Flash / Pro, whichever provider you chose.

## What you just learned

- KVASIR costs nothing at boot and makes no network call without the
  button press — both the key gate and the consent gate are enforced.
- The key rides the provider's auth header only (`x-api-key`,
  `Authorization: Bearer`, `x-goog-api-key`) — never a URL, body, or log.
- Keys never cross providers, and consent is per provider: a yes for
  Anthropic is not a yes for Google or OpenAI.
- Degradation is honest: no-key, no-consent, nothing-to-explain, offline,
  and refusal each show a clear LCD message.

## Next

- Wire it hands-free: a `VERITAS FAIL → KVASIR EXPLAIN` cable
  auto-explains a failed test run (the cable path never prompts and
  rate-limits at 30s); its OUT feeds MONITOR/PHOSPHOR.
