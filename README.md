# Deribit Starbase API

Java 23 components for Deribit's not-yet-launched Starbase interfaces. Maven coordinates:
`io.contek.invoker:invoker-deribit-starbase-api:0.1.0-SNAPSHOT`.

## Status

Development is paused (since 2026-07-31) for a corrected or clarified specification. This
is a locally tested, unintegrated prototype—not production-ready.

Reconnect recovery is blocked because SBE order-entry v11 identifies orders with signed
64-bit `orderId`/`clientOrderId`, while REST 2.0 exposes a UUID-style `order_id` and optional
`label`. No official exact bridge exists; approximate reconciliation could select the
wrong order, so trading readiness remains fail-closed.

Project context:

- [Status and restart handoff](docs/implementation-status.md)
- [Durable implementation contract](docs/implementation-contract.md)
- [Official-source review](docs/protocol-source-review.md)
- [Pinned schema/template manifest](docs/schema-manifest.md)
- [Portable environment setup](docs/codex-environment.md)

## Implemented components

- Bounds-checked absolute little-endian framing, market-data, and 25 order-entry layouts.
- UDP receive/sequencing, A/B arbitration, retransmit, snapshot, health, and diagnostics.
- Exact 64-bit registry/L3 book, aggregation, atomic snapshots, coherent publication, and
  cached primitive channels.
- TCP framing/write/lifecycle, authentication, heartbeat, sequencing, reconnect/readiness,
  correlation, order/fill state, client-ID mapping, and one-send A/B routing.
- Five REST utilities and an immutable, synchronous single-flight open-order cache with a
  one-minute minimum attempt interval.
- Deterministic official-PCAP replay and hot-path allocation checks.

They are not yet fully composed behind the public APIs; see the [known assembly and
validation gaps](docs/implementation-status.md#known-assembly-and-validation-gaps).

## Build

Use JDK 23+ and the Maven wrapper:

```text
mvnw clean test
```

Tests have no framework dependency. Surefire's auto-detected
`org.apache.maven.surefire.junit.JUnit3Provider` discovers public final classes and public
zero-argument `test*` methods; local `TestAssertions` supplies assertions. The baseline
test verifies Java assertions are enabled.

Two consecutive clean runs on 2026-07-31 passed all 292 tests (no failures, errors, or
skips). No private live Starbase environment was available, so this proves local behavior
only.

For machine-specific launch notes, copy
[`docs/local-environment.example.md`](docs/local-environment.example.md) to ignored
`docs/local-environment.md`; never store credentials there. See [environment
setup](docs/codex-environment.md) for Codex cloud use.

## Scope

No FIX/FIX Drop Copy, generated codecs, runtime XML parsing, standard Deribit
history/account APIs, or consumer-specific adapters. Standard REST/WebSocket functions
remain in sibling `deribit-api`.
