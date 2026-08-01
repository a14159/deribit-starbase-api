# Deribit Starbase API repository guidance

These rules apply to the whole repository.

## Required context

Before planning, editing, or running implementation commands, read in full:

- `docs/implementation-contract.md` (stable technical contract)
- `docs/implementation-status.md` (canonical mutable state and restart procedure)

Do not create another tracker. Before changing wire layout or protocol behavior, also read
`docs/protocol-source-review.md` and `docs/schema-manifest.md` in full and revalidate the
current official sources. Stop and report any missing required file instead of guessing.

Then read the optional ignored `docs/local-environment.md` in full if present. It contains
workstation launch details only, cannot override project state, and must not be copied into
tracked files.

Tracked docs/examples may use repository-relative paths, `PATH` commands, the Maven
Wrapper, portable environment variables, versions, and placeholder paths. Never record an
absolute home, IDE, JDK, Maven, or temporary path. Describe downstream users generically
as consumers/integrations; never expose private consumer names, paths, internal types, or
consumer-specific task IDs.

## Execution workflow

At every implementation turn:

1. Run `git status --short` here and in each explicitly scoped dependency/consumer repo;
   preserve unrelated and user-owned changes.
2. Read the checkpoint and exact restart procedure.
3. While `PAUSED`, first revalidate the upstream specification. If it still lacks an exact
   REST/SBE order-identity bridge, record that evidence and stop before downstream work.
   Only an explicitly requested, separately documented maintenance goal may proceed; it
   must neither change protocol behavior nor claim to resolve the pause.
4. After the gate is truly resolved, resume the one active task or choose the first
   dependency-ready task. Record it as active before changing production behavior.
5. Work on one task until done or genuinely blocked. Record RED/pass evidence, changed
   files, verification commands/results, blockers, and the exact next action in
   `docs/implementation-status.md`; continue only while safe dependency-ready work remains.

Update the status file before ending or nearing a context boundary; its filesystem state,
not chat history, is the handoff. Do not commit, create remotes, or push unless asked.

## Test-first behavior changes

1. Add the smallest deterministic test and observe the intended failure.
2. Make the smallest correct production change.
3. Run the focused and affected regression tests.
4. Add applicable boundary, corrupt-input, lifecycle, and state-transition coverage.
5. For hot paths, run the post-warm-up allocation check.
6. Record concise RED/pass evidence before completion.

Compilation alone is insufficient. Prefer byte fixtures, fake clocks, scripted channels,
loopback peers, and checked-in PCAP data before live infrastructure.

## Fixed boundaries

- Artifact: `io.contek.invoker:invoker-deribit-starbase-api`; Java 23 bytecode unless the
  specification is explicitly revised.
- No FIX, FIX Drop Copy, generated SBE codecs, or runtime XML parsing.
- Use bounds-checked hardcoded codecs, fixed offsets, and absolute little-endian
  `ByteBuffer` access. Pin/test every schema version, template ID, block length, offset,
  enum, null, flag, and padding rule after revalidating current production docs/schemas.
- Allocate no decoder/message/field object per TCP message, UDP packet, or market-data
  event; normal decode, dispatch, sequencing, L3 mutation, and consumer book adaptation
  must be allocation-free after warm-up.
- Retain factory -> API -> cached channel -> consumer where applicable.
- TCP order-entry lifetime is explicit; never auto-close an idle session because
  disconnect can cancel orders. Send each order to exactly one of A/B.
- Starbase market data is independent UDP A/B + snapshot + retransmit + L3. Never mix a
  standard REST L2 snapshot into its sequence domain.
- Standard history, account, positions, and suitable ticker functions stay in
  `deribit-api`; consumer market-data and execution backends remain independently
  selectable.
- Never truncate 64-bit Starbase IDs or silently ignore/approximate unsupported semantics.
  Unknown/unsupported state-changing messages fail the affected feed/session closed.
- Never commit credentials, private endpoints, tokens, or authenticated captures; redact
  secrets from logs/fixtures. Preserve `deribit-api` and out-of-scope consumer behavior.

## Completion

Completion requires no required waiting/active/TODO/blocked status work, an audited
contract, passing relevant builds/tests, and recorded live validation—or an explicit note
that it was unavailable, without claiming production readiness.
