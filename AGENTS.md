# Deribit Starbase API repository guidance

These instructions apply to the entire `deribit-starbase-api` repository.

## Mandatory project context

Before planning, editing, or running implementation commands, read both files completely:

- `../deribit-api/STARBASE_IMPLEMENTATION_AGENT.md`
- `../deribit-api/STARBASE_IMPLEMENTATION_TRACKER.md`

Treat the first file as the stable technical specification. Treat the second as the
canonical mutable execution state. Do not create another independently maintained tracker.

When changing a downstream consumer, read and obey that repository's own guidance
before making changes there.

If any required file is unavailable, stop and report the missing path instead of guessing
at the architecture or current task.

## Persistent execution workflow

At the beginning of every implementation turn:

1. Inspect `git status --short` in this repository and any dependency or consumer repository explicitly placed in scope as
   applicable. Preserve unrelated and user-owned changes.
2. Read the tracker checkpoint.
3. Resume its single `IN_PROGRESS` task. If none exists, select the first listed `TODO`
   task whose dependencies are `DONE`.
4. Mark the selected task `IN_PROGRESS` and update the checkpoint before changing
   production code.
5. Work on only that task until it is `DONE` or genuinely `BLOCKED`.
6. Record test evidence, changed files, verification commands/results, blockers, and the
   exact next action in the tracker.
7. Continue to the next dependency-ready task while safe work remains.

Before ending a turn or approaching a context boundary, update the tracker. The filesystem
tracker, not the conversation summary, is the authoritative handoff.

Do not make Git commits, create remotes, or push changes unless the user asks.

## Test-first rule

For every new behavior:

1. Add the smallest deterministic test for the missing behavior.
2. Run it and observe failure for the intended reason.
3. Implement the smallest correct production change.
4. Run the focused test and affected regression tests.
5. Add relevant boundary, corrupt-input, lifecycle, and state-transition tests.
6. For a hot path, run the required post-warm-up allocation check.
7. Record concise RED and passing evidence in the tracker before marking the task `DONE`.

A successful compilation alone does not complete a behavioral task. Prefer byte fixtures,
fake clocks, scripted channels, and loopback peers before relying on a live Starbase
environment.

## Fixed implementation boundaries

- Keep this as a separate Maven artifact:
  `io.contek.invoker:invoker-deribit-starbase-api`.
- Target Java 23 bytecode unless the specification is explicitly revised.
- Do not implement FIX or FIX Drop Copy.
- Do not generate codecs from SBE XML and do not parse XML at runtime.
- Implement bounds-checked hardcoded codecs with fixed offsets and absolute
  little-endian `ByteBuffer` access.
- Do not allocate decoder/message/field objects per TCP message, UDP packet, or
  market-data event.
- Keep normal decode, dispatch, sequencing, L3 mutation, and consumer book-adaptation hot
  paths allocation-free after warm-up.
- Retain the factory -> API -> cached channel -> consumer architecture where applicable.
- Use explicit TCP order-entry connection lifetime; never auto-close an idle session,
  because disconnect can cancel orders.
- Implement Starbase market data independently over UDP with A/B, snapshot, retransmit,
  and L3 reconstruction.
- Keep standard history, account, positions, and appropriate ticker functionality in the
  existing `deribit-api`.
- Require downstream consumers to keep market-data and execution backend selection independent.
- Never truncate 64-bit Starbase identifiers or silently ignore/approximate unsupported
  order semantics.

## Protocol and safety rules

- Revalidate the current production documentation and downloaded schemas before hardcoding
  any layout.
- Pin and test every implemented schema version, template ID, block length, offset, enum,
  null value, flag, and padding rule.
- Unknown or unsupported state-changing messages must fail the affected feed/session
  closed; do not continue with apparently healthy stale state.
- Never merge a standard REST L2 snapshot into a Starbase L3 sequence domain.
- Never duplicate-send an order to A and B.
- Never commit credentials, private endpoints, tokens, or captured authenticated traffic.
  Redact secrets from logs and test fixtures.
- Preserve existing behavior in `deribit-api` and out-of-scope consumer code.

## Completion

The implementation is complete only when the tracker has no required `TODO`,
`IN_PROGRESS`, or `BLOCKED` tasks, the acceptance criteria in the technical specification
have been audited, the relevant builds/tests pass, and required live validation is either
recorded or explicitly reported as unavailable without claiming production readiness.
