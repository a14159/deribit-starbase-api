# Starbase implementation status and restart handoff

## Current checkpoint

| Field | Value |
| --- | --- |
| Overall state | **PAUSED — awaiting a corrected or clarified Deribit specification** |
| Pause date | 2026-07-31 |
| Active task | `DOC-PRIVACY`, portable local-environment guidance and Git-history sanitization |
| Blocking task | `ORD-07`, exact REST-to-SBE open-order reconciliation |
| Last completed task | `TEST-MIGRATION`, dependency-free Surefire POJO test migration |
| Local verification | Two consecutive `mvn clean test` runs: 292 tests, 0 failures, 0 errors, 0 skipped; Surefire 3.5.4 `JUnit3Provider` |
| Production readiness | **No** — component assembly, downstream consumer integration, joint builds, and private live validation remain |
| Exact next action | Complete the explicitly requested `DOC-PRIVACY` maintenance goal, then return to the paused specification-review checkpoint |

Only the explicitly requested `DOC-PRIVACY` maintenance goal is in progress. Do not start
downstream protocol work merely because the local suite is green. First resolve the
protocol identity gate described below, or get an explicit scope decision that changes
the recovery requirement. `DOC-PRIVACY` changes documentation, portable build tooling,
and repository history only; it must not resume protocol work or alter production
behavior.

The independently approved `TEST-MIGRATION` maintenance goal is complete. It did not
resume protocol work, alter production sources, or resolve `SPEC-01`/`ORD-07`.

## `DOC-PRIVACY` maintenance handoff

The user explicitly approved this repository-maintenance goal during the protocol pause.
It does not change production sources, protocol behavior, or the `SPEC-01`/`ORD-07` gate.

Work completed before the history rewrite:

- tracked documentation now uses the Maven Wrapper or portable commands instead of
  workstation-specific launchers;
- Maven Wrapper 3.3.4 pins Maven 3.9.16 and verifies the distribution SHA-256;
- `.gitignore` excludes `docs/local-environment.md`, while a tracked example documents
  its strictly non-authoritative scope;
- `.worktreeinclude` carries that ignored note into Codex-managed local worktrees;
- tracked Codex environment guidance configures JDK 23 and primes the wrapper during the
  cloud setup phase; and
- `AGENTS.md` forbids machine-specific paths in tracked documentation.

Verification on 2026-08-01:

- `.\mvnw.cmd -B -ntp clean test`: 292 tests, 0 failures, 0 errors, 0 skipped;
- the Maven 3.9.16 archive matched its published SHA-512 before its SHA-256 was pinned;
- all 21 local Markdown link targets resolved;
- `git diff --check` passed apart from line-ending conversion notices; and
- the public Markdown privacy scan found no home, IDE, local JDK, or local Maven paths.

Remaining maintenance action: commit the scoped files, rewrite every reachable commit
containing the audited machine-specific strings, push `main` with the recorded remote-SHA
lease, rescan the rewritten history, and record the final remote verification here.

## `TEST-MIGRATION` completion handoff

The suite now uses a dependency-free Maven Surefire POJO convention:

- `pom.xml` has no test dependency or JUnit property; it pins Surefire 3.5.4 and enables
  Java assertions;
- all 63 test classes are public and expose 292 public zero-argument `test*` methods;
- `TestAssertions` is a test-only typed assertion/exception utility, covered by 11
  deliberately added contract tests;
- all 281 baseline methods map exactly to their prefixed POJO names, with no original
  assertion invocation removed;
- the source-to-Surefire-XML inventory maps all 292 methods exactly; and
- the source/POM scans contain no `org.junit`, JUnit annotation, dependency, exclusion,
  skip, or discovery-canary residue.

RED evidence was preserved for the missing assertion helper, the required nullable
boxed-long overload, and the temporary discovery canary. The canary produced one expected
failure through `PojoTestSetExecutor` and was immediately removed. During repeated full
runs, `StarbaseMarketDataTransportLifecycleTest` exposed an observation race between the
aggregate receive count and the per-feed diagnostic count; its existing bounded wait now
observes both already-asserted states. The focused class then passed six consecutive runs.

Final verification on 2026-07-31:

```powershell
.\mvnw.cmd clean test
```

This is the current portable reproduction command for the recorded test run. The original
workstation-specific launcher is intentionally retained only in the ignored local
environment note.

- Two consecutive clean runs: 292 tests, 0 failures, 0 errors, 0 skipped, using
  `org.apache.maven.surefire.junit.JUnit3Provider`.
- Final XML: 63 suites and 292 testcase elements, with exact source mapping.
- Allocation focus: 34 discovered allocation-sensitive classes, 189 tests, all passing.
- `mvn dependency:tree`: only the project artifact; no dependencies.
- `git diff --check`: passed, with only the pre-existing `AGENTS.md` line-ending warning.
- Documentation: all 19 local Markdown links resolved after the final handoff edits.

## Why work is paused

The local SBE order store is keyed by exact signed 64-bit `orderId` and `clientOrderId`.
The current official Starbase REST 2.0 `Order` response instead exposes:

- a required UUID-style string `order_id`;
- an optional/nullable string `label`; and
- no numeric SBE `orderId`, `clientOrderId`, or documented reversible equivalent.

The reviewed REST `Order` properties are:

```text
amount, api, average_price, commission, creation_timestamp, filled_amount,
instrument_name, label, last_update_timestamp, max_show, order_id, order_state,
order_type, post_only, price, profit_loss, reduce_only, side, time_in_force
```

Required fields are `order_id`, `instrument_name`, `side`, `price`, `amount`,
`filled_amount`, `order_state`, and `order_type`.

Matching on instrument, side, price, quantity, timestamps, or label is ambiguous. Doing so
could attach a REST order to the wrong SBE order and then incorrectly reopen trading after
a disconnect. The implementation contract forbids that approximation, so
`ReconnectReadiness.onReconciled()` cannot be called safely.

The blocker was rechecked three times on 2026-07-31 against:

- REST OpenAPI 2.0, SHA-256
  `F2F2DD44CC4ED63ACC8C4E30545B2829514BF20566EE0C6AEFBA16D0F6F267DB`;
- order-entry SBE schema 2101/v11/1.3, SHA-256
  `70B1B297A4D8472CA31C76E97613909B136C0CF4782CB858CAC306696C0C5A89`;
- official Starbase SDK 0.5.1, archive SHA-256
  `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`;
- the official binary documentation and the implemented local state models.

The SDK contains numeric SBE IDs but no REST client/model or UUID-to-SBE bridge. A safe
resolution requires Deribit to add a shared exact SBE identifier to REST, or formally
document another collision-free reversible mapping.

## Implemented and verified component inventory

The former execution ledger recorded 60 completed test-first tasks. They are consolidated
below; the tests and source are the detailed executable record, while
[schema-manifest.md](schema-manifest.md) is the byte-layout inventory.

| Completed IDs | Implemented behavior | Main proof |
| --- | --- | --- |
| `FND-01`–`FND-06` | Standalone Java 23 Maven artifact; pinned sources/resources; deterministic byte fixtures; validated contexts, wipeable credentials, factory, explicit lifecycle, and stable primitive channels | `ArtifactBaselineTest`, `ProtocolSchemasTest`, `WireTestSupportTest`, `ConfigurationTest`, `FactoryLifecycleTest`, `StarbaseLongChannelTest` |
| `COD-01`–`COD-06` | Bounds/unsigned/alignment primitives; 32-byte TCP, 24-byte UDP, and 16-byte MD headers; exact Price9/Decimal72; schema/template/version dispatch | `codec/common/*Test.java` |
| `MDC-01`–`MDC-07` | Hardcoded market-data reference, L3 mutation, trade, snapshot/cycle, and retransmit layouts; complete UDP packet validation; official PCAP replay | `codec/marketdata/*Test.java`, checked-in PCAP and golden trace |
| `MDT-01`–`MDT-06` | Configured reusable-buffer UDP receiver; feed sequence/heartbeat tracking; primitive A/B arbitration; retransmit paging/retry/reject transport; snapshot synchronization; bounded health/counters | `marketdata/MarketDataUdpReceiverTest`, `FeedSequenceTrackerTest`, `FeedArbitratorTest`, `RetransmitClientTest`, `UdpRetransmitTransportTest`, `SnapshotSynchronizationTest`, `FeedDiagnosticsTest` |
| `BOK-01`–`BOK-07` | Exact signed-64-bit instrument registry; fixed-capacity L3 store; put/reduce/delete; Price9 level aggregation and priority; coherent publication; double-buffered snapshot/replay; invariants and allocation checks | `book/*Test.java` |
| `MDA-01`–`MDA-04` | Fixed primitive channel caches; reference routing; reconstructed L3-to-level channel; trade-summary/trade channel; stateful PCAP-to-channel replay | `MarketDataChannelRoutingTest`, `OrderBookChannelTest`, `TradesChannelTest`, `PcapToChannelsReplayTest` |
| `OEC-01`–`OEC-06` | 25 hardcoded order-entry session, new/amend/cancel/mass-cancel, response/reject, fill, and unsolicited lifecycle layouts with fail-closed dispatch | `codec/orderentry/*Test.java` |
| `OET-01`–`OET-07` | Reusable TCP frame assembly; serialized partial-write handling; explicit connection loop; authentication; heartbeat/inactivity; sequence/resend; reconnect/backoff/readiness gates | `orderentry/connection/*Test.java` |
| `ORD-01`–`ORD-06` | Fixed correlation table; cross-session local order state; command encoder facade; exact-once fills; reversible label-to-int64 IDs with persistence hooks; deterministic one-send A/B routing | `orderentry/state/*Test.java`, `orderentry/command/*Test.java` |
| `RST-01`–`RST-05` | Configured bearer/no-auth HTTP transport; instruments and registry bootstrap; open-order parsing; cancel-all/lock/unlock; rate-limited recovery cache | `rest/*Test.java` |

### Official market-data fixture result

The checked-in 5,344,700-byte PCAP has SHA-256
`980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF`.
`OfficialPcapReplayTest` deterministically validates all 13,306 packets and 37,745
messages. `PcapToChannelsReplayTest` verifies channel 44853 with 2,115 reference
callbacks, 861 coherent book callbacks, book hash `7987323303025136854`, zero trade
callbacks (the capture has no trade templates), and final book readiness.

### Allocation evidence

Post-warm-up tests report zero allocated bytes for the implemented normal paths in these
areas:

- wire primitives, decimal codecs, template dispatch, and all implemented MD/OE decoders;
- feed sequence, A/B arbitration, snapshot synchronization, and diagnostics;
- instrument lookup, L3 mutation, aggregation, and coherent book replay;
- TCP frame assembly/write, heartbeat, and sequence state; and
- correlation, local order lifecycle, command encode/send, fill processing, client-ID
  lookup, and order routing.

The individual measured methods remain discoverable with:

```powershell
rg -n "[Aa]llocat(e|es|ion).*([Nn]othing|0 bytes)|0 allocated bytes" src/test/java
```

These are unit-level allocation assertions, not a substitute for the still-pending
end-to-end benchmark.

## `OpenOrderRecoveryCache` handoff

[`OpenOrderRecoveryCache`](../src/main/java/io/contek/invoker/deribit/starbase/rest/OpenOrderRecoveryCache.java)
is the last completed production component and is deliberately narrower than
reconciliation:

- construction rejects refresh intervals shorter than one minute;
- `get()` and `refresh()` are synchronized, so an expired snapshot has one synchronous
  in-flight load;
- each successful result is published as an immutable `List.copyOf` snapshot;
- refresh attempts, including failures, advance the next allowed attempt time;
- `invalidate()` expires the value locally but cannot bypass the server rate window;
- a refresh failure retains the last-good snapshot while surfacing the failure;
- an initial failure leaves `hasSnapshot()` false and later rate-limited reads fail
  honestly; and
- counters and deadlines saturate rather than overflow.

Its behavior is covered by
[`OpenOrderRecoveryCacheTest`](../src/test/java/io/contek/invoker/deribit/starbase/rest/OpenOrderRecoveryCacheTest.java).
The cache does not map REST orders to SBE IDs, mutate `LocalOrderStateStore`, or mark a
session reconciled. That missing step is exactly `ORD-07`.

## Known assembly and validation gaps

The green suite proves the components above in isolation and through selected replay
paths. It does **not** mean the public APIs form a complete client:

1. `StarbaseOrderEntryApi` is still a facade placeholder. `isAuthenticated()` always
   returns `false`; the tested connection, authentication, liveness, sequence, readiness,
   command, state, fill, mapping, and routing components are not composed behind it.
2. One `StarbaseMarketDataApi` instance owns one configured `GatewaySide` with one
   incremental and one snapshot receiver. Its live gap path records the gap and returns.
   `FeedArbitrator`, `RetransmitClient`, `UdpRetransmitTransport`, and the atomic recovery
   components are tested but are not wired into that live API, so it does not yet join and
   coordinate both A/B sides or recover live gaps end to end.
3. `IndexDefinition` has a hardcoded decoder but is not applied/published by the public
   market-data API. Schema-known `BlockTrade` (template 33) has no hardcoded decoder and
   fails closed in packet dispatch.
4. Exact REST/SBE reconnect reconciliation is absent and readiness must remain closed.
5. No Starbase dependency, backend enums, stream adapters, execution connector, or health
   integration has been validated in a downstream consumer.
6. The three repositories have not been built together, and no private Starbase test
   environment, credentials, authenticated traffic, deployment configuration, or rollback
   exercise has been validated.

## Remaining work

| ID | State | Work | Dependency |
| --- | --- | --- | --- |
| `SPEC-01` | WAITING | Obtain and revalidate a newer official OpenAPI/XML/docs/SDK release with an exact REST/SBE identity bridge | Deribit release or formal clarification |
| `ORD-07` | BLOCKED | Reconcile missing, extra, matching, duplicate, and ambiguous REST/SBE orders before restoring readiness | `SPEC-01` |
| `ASM-MD` | TODO | Compose both A/B feed instances, arbitration, retransmit, snapshot fallback, atomic books, and health into the public market-data lifecycle | New schema review; existing MD components |
| `ASM-OE` | TODO | Compose TCP connection/session, dispatcher, state, commands, routing, events, recovery, and readiness into `StarbaseOrderEntryApi` | `ORD-07`; existing OE components |
| `CON-01`–`CON-08` | TODO | Add dependency, independent backend selection, lifecycle holder, book/trade adapters, execution/amend/cancel/open-order paths, and health/rollback behavior | `ASM-MD`, `ASM-OE`; read the consumer repository's own guidance first |
| `VAL-01`–`VAL-07` | TODO | Full artifact and joint builds, replay/recovery/TCP scenarios, end-to-end allocations, private smoke tests, and operations/configuration/rollback audit | All integration work |

Do not silently downgrade `ORD-07`, remove its readiness gate, or treat it as optional to
unblock integration.

## Exact restart procedure

1. Inspect `git status --short` in this repository and any dependency or consumer repository explicitly placed in scope as
   applicable. Preserve user and unrelated changes.
2. Read [implementation-contract.md](implementation-contract.md), this file,
   [protocol-source-review.md](protocol-source-review.md), and
   [schema-manifest.md](schema-manifest.md).
3. Download the current official SBE XML bundle, REST OpenAPI, binary reference/changelog,
   and SDK. Compute hashes and compare them with the pins in this repository.
4. If the versions are unchanged or still contain no exact REST/SBE identity bridge,
   record the new audit date/evidence here and stop. Do not begin downstream integration work.
5. If the specification changed, review every affected schema ID, version, template ID,
   block length, offset, enum, null, flag, and padding rule. Update checked-in reference
   resources, manifest, codecs, and golden tests test-first; do not generate or runtime
   parse XML.
6. Mark `ORD-07` active and add `OrderStateReconciliationTest` first. Cover exact matches,
   REST-only orders, SBE-only orders, terminal orders, duplicates, null/invalid identity,
   ambiguity rejection, snapshot failure/age, disconnect, and the readiness transition.
7. Implement only the officially supported exact mapping, run focused and full tests, and
   record the RED/green evidence here.
8. Complete `ASM-MD` and `ASM-OE` with deterministic integration tests before touching
   consumer adapters. Then work through the remaining integration and validation rows one at a time.

## Reproducible local verification

Use JDK 23 or newer. The Maven Wrapper pins Maven 3.9.16 and verifies the downloaded
distribution checksum.

Windows:

```powershell
.\mvnw.cmd clean test
```

Linux or macOS:

```sh
./mvnw clean test
```

The source targets Java 23 bytecode when Maven runs on any compatible JDK. Focused
Windows example:

```powershell
.\mvnw.cmd "-Dtest=OpenOrderRecoveryCacheTest,StarbaseOpenOrdersEndpointTest,StarbaseRestTransportTest" test
```

Always inspect the current branch, worktree, and remotes directly when resuming; historical
machine-local Git state is not part of this checkpoint.
