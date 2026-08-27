# Starbase implementation status and restart handoff

## Current checkpoint

| Field | Value |
| --- | --- |
| Overall state | **READY TO RESUME — the external identity gate is resolved; implementation remains inactive for a separate restart session** |
| Gate resolution date | 2026-08-27 |
| Active task | None — do not start implementation in this documentation-only handoff session |
| Blocking task | None external; `SPEC-02` is the first dependency-ready internal task |
| Last completed implementation task | `CLIENT-ID-01`, native-long and canonical bidirectional String client-order IDs |
| Local verification | 2026-08-27: formal Deribit support clarification establishes Starbase REST `order_id` as the decimal string serialization of SBE `orderId`. Current public OpenAPI still has erroneous UUID wording. Current production order-entry XML is v15/1.5; market data remains v1/1.0. Last clean suite: 2026-08-03, 300/300. |
| Production readiness | **No** — component assembly, downstream consumer integration, joint builds, and private live validation remain |
| Exact next action | In the separate restart session, re-download the required sources, mark `SPEC-02` active, and adopt/review the current v15/REST/market-data deltas test-first. Then mark `ORD-07` active, add the RED reconciliation/parser tests, and implement only the clarified decimal-string mapping. |

There is no active implementation item in this session. `SPEC-01` is resolved by the
formal 2026-08-27 clarification, but readiness remains closed until the current source
delta, `ORD-07`, and the later assembly/validation work are completed. The separately
requested `CLIENT-ID-01` API/correlation maintenance goal remains the last completed
implementation task. No production source, schema resource, or test was changed while
preparing this restart handoff.

## `CLIENT-ID-01` completion handoff

The user requested two client-order-ID paths on 2026-08-03:

- accept schema-native long IDs without a String mapping; and
- retain String IDs through deterministic positional encoding instead of a bounded table,
  including an allocating numeric-to-String reverse conversion.

`ClientOrderIdMap` is now stateless. Native IDs pass through across the complete signed SBE
domain except `Long.MIN_VALUE`, which is the schema null sentinel. String IDs use the
pinned generator alphabet `0-9a-zA-Z-_`, positional base 64, and reduction modulo
`2^64 - 1`. The odd modulus prevents a long fixed suffix from discarding all earlier
counter digits, as radix-64 signed-long overflow would do. Residues map bijectively onto
the non-null signed-long domain, so every valid numeric ID has a canonical String and
`map(externalId(id)) == id`.

The inverse allocates its returned String, as explicitly accepted. The original input is
not recoverable after a collision: leading-zero inputs and positional values separated by
the modulus normalize to the same canonical String. Callers must use a generation scheme
that is collision-free for their emitted set; live-state duplicate checks remain
fail-closed. A deterministic 100,000-ID sample from the referenced
`counter + run + market` generator shape had no collisions.

`OrderCommandFacade` retains the native-long overloads and adds String overloads for limit,
market, amend, and cancel commands. New/amend/cancel request validation and A/B session
routing now accept negative schema-native client IDs while still rejecting the null
sentinel. No wire offsets or field widths changed.

Required upstream revalidation on 2026-08-03 found no new REST/SBE identity bridge and no
schema change:

- XML bundle SHA-256
  `D36FEDB7AEB2FC5418FBFCFA9FBA80762E865689198B34A64DAEC8DB6D6FB425`;
- REST OpenAPI SHA-256
  `F2F2DD44CC4ED63ACC8C4E30545B2829514BF20566EE0C6AEFBA16D0F6F267DB`;
- SDK 0.5.1 archive SHA-256
  `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`;
- current binary-reference Markdown SHA-256
  `908CF0464BD0A065C2851B7085D9ED5657740C9541F0F857D105600844008369`;
  and
- current Starbase changelog Markdown SHA-256
  `A9F2BC9F7921A6639855BCAD075A49BF4748612729679A2513C86504C6BE52F3`.

Test-first evidence:

- RED: initial mapping tests failed compilation with 13 errors against the old bounded
  constructors/API; signed-native tests then exposed two non-negative validation failures;
  facade String-overload tests failed compilation with four errors; reverse-conversion
  tests failed compilation with 13 errors; and the full-domain inverse boundary tests
  produced three expected failures against the interim `Long.MAX_VALUE` modulus.
- PASS: focused mapping/facade tests, 16/16; affected mapping/facade/router/request-codec
  tests, 46/46; and `mvnw clean test`, 300/300 with no failures, errors, or skips.
- Post-warm-up native and String forward conversion allocated zero bytes. The canonical
  reverse conversion intentionally allocates.

Changed production files: `ClientOrderIdMap`, `OrderCommandFacade`,
`OrderSessionRouter`, and the new/amend/cancel request encoder/decoder pairs. Changed tests:
`ClientOrderIdMapTest`, `OrderCommandFacadeTest`, `OrderSessionRouterTest`, and the three
request-codec tests. Private live validation was unavailable; this maintenance task does
not claim production readiness.

## Resolved external identity gate

The original gate was valid: local SBE state uses exact signed 64-bit `orderId`, while the
public Starbase REST OpenAPI described `Order.order_id` as a UUID-style string and supplied
no numeric bridge. Tuple/label matching could bind the wrong order and reopen trading after
disconnect, so `ReconnectReadiness.onReconciled()` had to remain unavailable.

On 2026-08-27, Deribit support answered an explicit question about the portfolio-scoped
Starbase REST `GET /api/v2/private/get_open_orders` endpoint. The clarification states:

- the endpoint's `order_id` is not a UUID;
- it is the exact SBE `orderId`, serialized as a base-10 JSON string;
- Java `Long.parseLong(order_id)` yields the value used for SBE reconciliation; and
- the currency-prefixed legacy `order_id` is separate and has no conversion to the SBE ID.

Support supplied a representative Starbase REST order whose `order_id` is
`"215074398825086978"`, explicitly identifying the public OpenAPI's UUID description as a
documentation error that Deribit intends to correct. This is the collision-free formal
clarification required by the contract, so `SPEC-01` is complete and `ORD-07` is no longer
externally blocked.

Implementation must parse the string exactly into a signed `long`, reject malformed or
overflowed input and `Long.MIN_VALUE` (the SBE null sentinel), reject duplicate/ambiguous
snapshot identities, and compare the primitive value directly with local SBE state. It
must not implement UUID parsing, tuple/label fallback, or conversion from the unrelated
legacy identifier. The additional undocumented fields in the support example are not part
of this clarification and must not be inferred into production behavior without separate
source evidence.

### 2026-08-27 clarification and restart audit

The formal support clarification above resolves `SPEC-01`, despite the still-incorrect
public OpenAPI. Fresh source review also found changes that must be handled before `ORD-07`:

- Starbase REST OpenAPI SHA-256
  `2E8E1B6FB09D988059BE2A63A4D8E0C6F986EAA343C2AB046D573E439C2E187E`; it still calls
  `order_id` a UUID-style string, so the support clarification governs identity semantics;
- production order-entry XML is schema 2101/v15/semantic version 1.5, SHA-256
  `4BA2A80B473AC233B6DDB971158E7A65353B1E287C7B304F14062AC2E5E9106C`;
- market-data XML remains schema 2102/v1/semantic version 1.0, SHA-256
  `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`;
- binary-reference Markdown SHA-256
  `6BC97D8A31BE0DE3372F468AFA957CD10D807C05D8F5647D8BB2BB800B9E3339`;
- changelog Markdown SHA-256
  `2F0B8C8BC6D2E968954734F28D2E615CCC44EB0E0E5847B0DDEC2320CF23B45F`;
- current order SDK remains schema v14, archive SHA-256
  `25B23E41E1FB92E290DD6D4E4124A9A69C2E215274C6957C22DE4BCFB8D6392D`; and
- the legacy XML bundle and SDK remain SHA-256
  `4B21E0F317B0C62BFDD3C77E0BC125EFD043A71493406FC45A3A00CE64297B42` and
  `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`.

Production v15 adds `MMP_MIN_FREEZE_TIME_NOT_ELAPSED` to `OrderRejectReason` as value 30
and to `MassQuoteRejectReason` as value 9. The public binary reference still links a v14
order SDK, and testnet remains v14. The current OpenAPI also differs from the implemented
REST baseline and still specifies Bearer authentication while the dedicated authentication
guide specifies HTTP Basic. These source deltas belong to `SPEC-02`; no behavior was
changed in this documentation-only handoff.

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
| `ORD-01`–`ORD-06`, `CLIENT-ID-01` | Fixed correlation table; cross-session local order state; command encoder facade; exact-once fills; native signed-long and stateless canonical bidirectional String client IDs; deterministic one-send A/B routing | `orderentry/state/*Test.java`, `orderentry/command/*Test.java` |
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
- correlation/order lifecycle, command encode/send, fills, client-ID forward conversion,
  and routing.

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
| `SPEC-01` | DONE | Formal clarification: Starbase REST `order_id` is the decimal string serialization of SBE `orderId` | Deribit support response dated 2026-08-27 |
| `SPEC-02` | READY | Review/adopt applicable production OE v12-v15, corrected MD v1, and current REST source deltas with pinned resources and golden tests | Current official sources; `SPEC-01` resolved |
| `ORD-07` | READY | Parse the clarified exact ID and reconcile missing, extra, matching, duplicate, invalid, and ambiguous REST/SBE orders before restoring readiness | `SPEC-02` |
| `ASM-MD` | TODO | Compose both A/B feed instances, arbitration, retransmit, snapshot fallback, atomic books, and health into the public market-data lifecycle | `SPEC-02`; existing MD components |
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
3. Download the current direct production/testnet SBE XMLs, legacy XML bundle, REST
   OpenAPI/reference/authentication docs, binary reference/changelog, current order SDK,
   and legacy SDK. Compute hashes and compare them with the 2026-08-27 pins.
4. Mark `SPEC-02` active. Review every changed schema ID, version, template ID, block
   length, offset, enum, null, flag, and padding rule, plus each applicable REST model and
   authentication delta. Update checked-in references, manifest, hardcoded codecs, and
   golden tests test-first; do not generate or runtime parse XML. Do not infer unrelated
   fields or units from the private support example.
5. Complete `SPEC-02` with focused/full pass evidence, then mark `ORD-07` active. First add
   RED coverage to `StarbaseOpenOrdersEndpointTest` for the representative decimal string,
   signed-long boundaries, malformed/overflow/sentinel values, and the stale UUID fixture.
   Make `StarbaseOpenOrder` expose the exact primitive SBE identity.
6. Add `OrderStateReconciliationTest` before its implementation. Cover exact matches,
   REST-only orders, SBE-only orders, terminal orders, duplicates, invalid identity,
   ambiguity rejection, snapshot failure/age, disconnect, and the readiness transition.
7. Implement only `Long.parseLong(Starbase REST order_id)` identity semantics; never use
   tuple/label matching or the legacy currency-prefixed `order_id`. Run focused and full
   tests and record RED/pass evidence here.
8. Complete `ASM-MD` and `ASM-OE` with deterministic integration tests before consumer
   adapters, then work through the remaining integration and validation rows one at a time.

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
