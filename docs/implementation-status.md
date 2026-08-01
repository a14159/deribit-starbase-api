# Starbase implementation status and restart handoff

## Current checkpoint

| Field | Value |
| --- | --- |
| Overall state | **PAUSED — awaiting a corrected or clarified Deribit specification** |
| Pause date | 2026-07-31 |
| Active task | None |
| Blocking task | `ORD-07`, exact REST-to-SBE open-order reconciliation |
| Last completed task | `DOC-COMPACT`, repository Markdown context reduction |
| Local verification | Prior clean suite: 292/292. `DOC-COMPACT`: focused baseline 1/1; 21/21 relative links, preservation/fence audits, and `git diff --check` passed. |
| Production readiness | **No** — component assembly, downstream consumer integration, joint builds, and private live validation remain |
| Exact next action | Ask GitHub Support to purge the unreferenced pre-rewrite cached views. Protocol work remains paused pending the next Deribit specification review. |

There is no `IN_PROGRESS` implementation item: a green suite does not bypass the identity
gate. The explicitly requested `DOC-COMPACT` maintenance goal is complete without changing
protocol requirements/behavior, evidence, blockers, or restart state.

## Why work is paused

Local SBE state uses exact signed 64-bit `orderId`/`clientOrderId`; REST 2.0 `Order` has:

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

Tuple/label matching is ambiguous and could bind the wrong order, then reopen trading after
disconnect. The contract forbids it; `ReconnectReadiness.onReconciled()` is unsafe.

The blocker was rechecked three times on 2026-07-31 against:

- REST OpenAPI 2.0, SHA-256
  `F2F2DD44CC4ED63ACC8C4E30545B2829514BF20566EE0C6AEFBA16D0F6F267DB`;
- order-entry SBE schema 2101/v11/1.3, SHA-256
  `70B1B297A4D8472CA31C76E97613909B136C0CF4782CB858CAC306696C0C5A89`;
- official Starbase SDK 0.5.1, archive SHA-256
  `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`;
- official binary docs and local state models.

The SDK has numeric SBE IDs but no REST model/client or UUID bridge. Deribit must expose a
shared exact SBE identifier in REST or formally specify another collision-free reversible
mapping.

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

Checked-in PCAP: 5,344,700 bytes, SHA-256
`980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF`.
`OfficialPcapReplayTest`: 13,306 packets/37,745 messages. `PcapToChannelsReplayTest` on
channel 44853: 2,115 reference + 861 coherent book callbacks, hash
`7987323303025136854`, zero trades (none in capture), final book ready.

### Allocation evidence

Post-warm-up tests report zero bytes on implemented normal paths for:

- wire primitives, decimal codecs, template dispatch, and all implemented MD/OE decoders;
- feed sequence, A/B arbitration, snapshot synchronization, and diagnostics;
- instrument lookup, L3 mutation, aggregation, and coherent book replay;
- TCP frame assembly/write, heartbeat, and sequence state; and
- correlation/order lifecycle, command encode/send, fills, client-ID lookup, and routing.

The individual measured methods remain discoverable with:

```powershell
rg -n "[Aa]llocat(e|es|ion).*([Nn]othing|0 bytes)|0 allocated bytes" src/test/java
```

These unit assertions do not replace the pending end-to-end benchmark.

## `OpenOrderRecoveryCache` handoff

[`OpenOrderRecoveryCache`](../src/main/java/io/contek/invoker/deribit/starbase/rest/OpenOrderRecoveryCache.java)
is the latest production component, not reconciliation:

- rejects intervals below one minute; synchronized `get()`/`refresh()` permit one
  synchronous expired-snapshot load;
- publishes immutable `List.copyOf` results; every attempt (including failure) advances
  the rate window, which `invalidate()` cannot bypass;
- surfaces refresh failure while retaining last-good state; initial failure leaves
  `hasSnapshot()` false and rate-limited reads fail honestly; and
- counters/deadlines saturate.

[`OpenOrderRecoveryCacheTest`](../src/test/java/io/contek/invoker/deribit/starbase/rest/OpenOrderRecoveryCacheTest.java)
covers this. The cache never maps REST/SBE IDs, mutates `LocalOrderStateStore`, or marks
reconciled; that is `ORD-07`.

## Known assembly and validation gaps

Green component/replay tests do **not** make the public APIs a complete client:

1. `StarbaseOrderEntryApi` remains a facade; `isAuthenticated()` always returns `false`.
   Tested connection/auth/liveness/sequence/readiness/command/state/fill/mapping/routing
   pieces are not composed.
2. Each `StarbaseMarketDataApi` owns one `GatewaySide` and its incremental/snapshot
   receiver; live gaps are only recorded. Tested `FeedArbitrator`, `RetransmitClient`,
   `UdpRetransmitTransport`, and atomic recovery are unwired, so the API neither coordinates
   A/B nor recovers gaps end to end.
3. `IndexDefinition` is decoded but not applied/published. `BlockTrade` template 33 has no
   codec and fails closed.
4. Exact REST/SBE reconnect reconciliation is absent and readiness must remain closed.
5. No downstream Starbase dependency/backend choice, stream/execution adapter, or health
   integration is validated.
6. No joint dependency-graph build or private environment, credentials, authenticated
   flow, deployment configuration, or rollback validation exists.

## Remaining work

| ID | State | Work | Dependency |
| --- | --- | --- | --- |
| `SPEC-01` | WAITING | Obtain and revalidate a newer official OpenAPI/XML/docs/SDK release with an exact REST/SBE identity bridge | Deribit release or formal clarification |
| `ORD-07` | BLOCKED | Reconcile missing, extra, matching, duplicate, and ambiguous REST/SBE orders before restoring readiness | `SPEC-01` |
| `ASM-MD` | TODO | Compose both A/B feed instances, arbitration, retransmit, snapshot fallback, atomic books, and health into the public market-data lifecycle | New schema review; existing MD components |
| `ASM-OE` | TODO | Compose TCP connection/session, dispatcher, state, commands, routing, events, recovery, and readiness into `StarbaseOrderEntryApi` | `ORD-07`; existing OE components |
| `CON-01`–`CON-08` | TODO | Validate a representative consumer dependency, independent backend selection, lifecycle holder, book/trade adapters, execution/amend/cancel/open-order paths, and health/rollback behavior | `ASM-MD`, `ASM-OE`; follow the consumer repository's own guidance |
| `VAL-01`–`VAL-07` | TODO | Full artifact and consumer-graph builds, replay/recovery/TCP scenarios, end-to-end allocations, private smoke tests, and operations/configuration/rollback audit | All integration work |

Do not silently downgrade `ORD-07`, remove its readiness gate, or treat it as optional to
unblock integration.

## Exact restart procedure

1. Inspect `git status --short` in this repository and any dependency or consumer
   repository explicitly placed in scope. Preserve user and unrelated changes.
2. Read [implementation-contract.md](implementation-contract.md), this file,
   [protocol-source-review.md](protocol-source-review.md), and
   [schema-manifest.md](schema-manifest.md).
3. Download the current official SBE XML bundle, REST OpenAPI, binary reference/changelog,
   and SDK. Compute hashes and compare them with the pins in this repository.
4. If the versions are unchanged or still contain no exact REST/SBE identity bridge,
   record the new audit date/evidence here and stop. Do not begin downstream integration
   work.
5. If the specification changed, review every affected schema ID, version, template ID,
   block length, offset, enum, null, flag, and padding rule. Update checked-in reference
   resources, manifest, codecs, and golden tests test-first; do not generate or runtime
   parse XML.
6. Mark `ORD-07` active and add `OrderStateReconciliationTest` first. Cover exact matches,
   REST-only orders, SBE-only orders, terminal orders, duplicates, null/invalid identity,
   ambiguity rejection, snapshot failure/age, disconnect, and the readiness transition.
7. Implement only the officially supported exact mapping, run focused and full tests, and
   record the RED/green evidence here.
8. Complete `ASM-MD` and `ASM-OE` with deterministic integration tests before implementing
   consumer adapters. Then work through the remaining integration and validation rows one
   at a time.

## Reproducible local verification

Use JDK 23+; the checksum-verified wrapper pins Maven 3.9.16 and targets Java 23:

```text
Windows:      .\mvnw.cmd clean test
Linux/macOS:  ./mvnw clean test
```

Focused Windows example:

```powershell
.\mvnw.cmd "-Dtest=OpenOrderRecoveryCacheTest,StarbaseOpenOrdersEndpointTest,StarbaseRestTransportTest" test
```

On resumption inspect branch/worktree/remotes directly; historical machine-local Git state
is not part of this checkpoint.
