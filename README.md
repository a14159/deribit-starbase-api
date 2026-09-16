# Deribit Starbase API

Java 23 components for Deribit's Starbase interfaces. Maven coordinates:
`io.contek.invoker:invoker-deribit-starbase-api:0.1.0-SNAPSHOT`.

## Status

The local implementation is current through order-entry schema v16 and market-data schema
v1. It remains an unintegrated prototype—not production-ready—until downstream integration,
private-environment validation, and operational/rollback validation complete. Reconnect
recovery uses Deribit's clarified exact mapping from the Starbase REST decimal-string
`order_id` to the SBE signed 64-bit `orderId`; malformed or ambiguous identities fail
closed.

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
  correlation, order/fill state, native-long and canonical bidirectional String client
  IDs, and one-send A/B routing.
- Five REST utilities and an immutable, synchronous single-flight open-order cache with a
  one-minute minimum attempt interval.
- Deterministic official-PCAP replay and hot-path allocation checks.

The redundant order-entry and market-data paths are composed behind public APIs. See the
[canonical implementation status](docs/implementation-status.md) for remaining validation
and integration gaps.

## Build

Use JDK 23+ and the Maven wrapper:

```text
./mvnw clean test
```

Tests have no framework dependency. Surefire's auto-detected
`org.apache.maven.surefire.junit.JUnit3Provider` discovers public final classes and public
zero-argument `test*` methods; local `TestAssertions` supplies assertions. The baseline
test verifies Java assertions are enabled.

The 2026-09-16 clean run passed 352/352 tests. This proves local behavior only; it does not
establish production readiness or replace the credential-safe private-environment
validator.

For machine-specific launch notes, copy
[`docs/local-environment.example.md`](docs/local-environment.example.md) to ignored
`docs/local-environment.md`; never store credentials there. See [environment
setup](docs/codex-environment.md) for Codex cloud use.

## Scope

No FIX/FIX Drop Copy, generated codecs, runtime XML parsing, standard Deribit
history/account APIs, or consumer-specific adapters. Standard REST/WebSocket functions
remain in sibling `deribit-api`.
