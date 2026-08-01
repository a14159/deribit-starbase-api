# Deribit Starbase API

Standalone Java client components for Deribit's not-yet-launched Starbase interfaces.
The Maven coordinates are
`io.contek.invoker:invoker-deribit-starbase-api:0.1.0-SNAPSHOT`, targeting Java 23.

## Status

Development was paused on 2026-07-31 while waiting for a corrected or clarified Deribit
specification. The checked-in implementation is a locally tested prototype, not a
production-ready client and not yet integrated into a production consumer.

The blocking incompatibility is in reconnect recovery: SBE order-entry schema v11 uses
signed 64-bit `orderId` and `clientOrderId`, while Starbase REST 2.0 returns a UUID-style
string `order_id` and an optional `label`. No official exact mapping joins the REST
snapshot to SBE session state. Approximate matching could reconcile the wrong order, so
trading readiness deliberately remains fail-closed.

Start with:

- [Implementation status](docs/implementation-status.md) — completed components, known
  assembly gaps, blocker evidence, remaining work, and the exact restart procedure.
- [Implementation contract](docs/implementation-contract.md) — durable architecture,
  safety, scope, and performance requirements.
- [Protocol source review](docs/protocol-source-review.md) — reviewed Deribit sources,
  rollout assumptions, and current discrepancies.
- [Schema manifest](docs/schema-manifest.md) — pinned XML hashes and every implemented
  hardcoded wire template.
- [Environment setup](docs/codex-environment.md) — portable local, Codex worktree, and
  Codex cloud-container setup.

## Implemented so far

- Bounds-checked, absolute little-endian codecs for common framing, the required
  market-data subset, and 25 order-entry message layouts.
- UDP receiver, sequence, A/B arbitration, retransmit, snapshot synchronization, health,
  and diagnostic components.
- Primitive 64-bit instrument registry, L3 book, exact level aggregation, atomic snapshot
  state, coherent publication boundaries, and cached primitive channels.
- TCP framing/writing, connection lifecycle, authentication, heartbeat, sequence,
  reconnect/readiness, command correlation, local order state, fill de-duplication,
  client-ID mapping, and single-send A/B routing components.
- The five Starbase REST utilities plus a synchronous, immutable, single-flight
  open-order recovery cache with a minimum one-minute attempt interval.
- Deterministic official-PCAP replay and allocation checks for the implemented hot paths.

These pieces are not all assembled behind the public APIs. The exact integration state is
listed in [Implementation status](docs/implementation-status.md#known-assembly-and-validation-gaps).

## Build

Use a JDK 23 or newer installation. The checked-in Maven Wrapper pins Maven 3.9.16, so a
separate Maven or IDE installation is not required.

On Windows:

```powershell
.\mvnw.cmd clean test
```

On Linux or macOS:

```sh
./mvnw clean test
```

The tests have no framework dependency. Surefire auto-detects
`org.apache.maven.surefire.junit.JUnit3Provider`, which discovers each public final test
class and its public zero-argument `test*` methods. Test assertions come from the local
test-only `TestAssertions` utility. Surefire enables Java assertions by default, and the
artifact baseline test verifies that behavior at runtime.

Two consecutive clean local runs on 2026-07-31 each passed 292 tests with no failures,
errors, or skips. No live private Starbase environment was available, so this result
establishes local behavior only.

For optional machine-specific launch notes, copy
[`docs/local-environment.example.md`](docs/local-environment.example.md) to the ignored
`docs/local-environment.md`. Do not put credentials in that file. Codex cloud setup is
described in [Environment setup](docs/codex-environment.md).

## Scope boundaries

This artifact does not implement FIX, FIX Drop Copy, generated SBE codecs, runtime XML
parsing, standard Deribit history/account APIs, or consumer-specific adapters. Standard
REST and WebSocket functionality stays in the sibling `deribit-api` project.
