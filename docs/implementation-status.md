# Starbase implementation status and restart handoff

Canonical mutable state. Durable rules live in
[implementation-contract.md](implementation-contract.md); source pins and protocol semantics
in [protocol-source-review.md](protocol-source-review.md) and
[schema-manifest.md](schema-manifest.md). Older task handoffs and their full RED/pass
evidence remain in Git history (`git log -p -- docs/implementation-status.md`).

## Current checkpoint

| Field | Value |
| --- | --- |
| Overall state | **WAITING** — private `VAL-EC2` inputs |
| Identity gate | Not paused: `SPEC-01` resolved 2026-08-27 by formal Deribit clarification (see source review) |
| Active task | None |
| Blocking | `VAL-EC2` needs the assigned private-network environment and credentials; no consumer repository is in scope |
| Last completed | `RST-08`, `MDA-06`, `MDA-05` (2026-10-28); `SPEC-06` (2026-10-27) |
| Last source audit | Full audit 2026-10-27; partial revalidation 2026-10-28 found no change |
| Local verification | 2026-10-28: `./mvnw -B -ntp clean test` passed 384/384, no failures/errors/skips |
| Production readiness | **No** — consumer integration, joint builds, private connectivity/authentication validation (including the live v19 flag echo), and operations/rollback validation remain |
| Exact next action | Obtain the assigned private-environment configuration/access reference, revalidate current sources, record `VAL-EC2` active, and run credential-safe validation with state changes disabled. Do not guess authentication or open readiness. |

## Open questions and known gaps

1. **REST authentication conflict.** The authentication guide requires HTTP Basic on every
   request; the OpenAPI specifies Bearer on private endpoints and none on public
   instruments. The existing OpenAPI-shaped behavior stays isolated pending formal
   clarification or private live evidence.
2. **Combo synthetic-leg `Trade` (31).** The trades reference does not say which instrument
   ID a combo leg carries. `TradeSummaryContext` requires each `Trade` to match its
   summary's instrument and fails closed otherwise, so leg IDs would fail the feed. Needs a
   Deribit clarification or live evidence, not a guess.
3. **Unvalidated live behavior.** v19 `NewOrderResponse.flags` echo; trade and block-trade
   framing; MD templates 12/14/15/33 (absent from the official PCAP); both redundant public
   lifecycles against a private environment.
4. **Intermittent allocation probes.** `FeedSequenceTrackerTest` and
   `StarbaseMarketDataAssemblyTest` occasionally report 56–528 bytes after warm-up on the
   reference workstation, also on an unmodified pre-`SPEC-06` checkout. No recent change
   caused it; it is tracked under `VAL-01`–`VAL-07`.
5. Index definition/info identities reach the primitive reference channel, but there is no
   named index registry.
6. No consumer dependency, backend selection, stream/execution/health adapter, joint build,
   deployment configuration, or rollback validation exists.

## Most recent completed work

Behavior is specified in the contract and source review; only the essentials and evidence
are kept here.

### `RST-08` structured REST `Retry-After` (2026-10-28)

`StarbaseRestException.retryAfterSeconds()` returns the RFC 9110 delay-seconds header of
every failure built from an HTTP response. `NO_RETRY_AFTER` (-1) covers an absent,
HTTP-date, signed, fractional, empty, or over-18-digit header, plus timeouts and I/O
failures. Nothing is retried or throttled automatically; the recovery cache keeps its
one-minute minimum.
Evidence: compile RED (10 errors), behavioral RED (1/6, `expected <37> but was <-1>`),
affected REST suites 40/40, parser mutation check, clean 384/384. No live request.

### `MDA-06` trade-summary recovery reset (2026-10-28)

`TradeSummaryContext.reset()` runs at startup and on every `requireFreshSnapshot`,
alongside the block-trade reset. A reset context joins: trades without an observed summary
are skipped until the next `TradeSummary`. Once aligned, nested summaries, orphan trades,
instrument mismatches, and `EndOfCycle` before all trades fail closed. The failure flag
still blocks the replay and legacy paths until the next reset.
Evidence: RED 3/15 intended failures, affected 37/37, three mutation checks, clean 382/382.

### `MDA-05` market-data block trades (2026-10-28)

`BlockTradeDecoder` (layout in the schema manifest) feeds the stable
`getBlockTradesChannel(instrumentId)` channel with every field plus the fill's index and
last-fill marker. `BlockTradeContext` frames every block trade by the header transaction
flags, joins after a reset, and fails orphan, nested, or cross-instrument fills closed once
aligned. Every pinned MD template now has a codec, so a datagram carrying any out-of-schema
template ID (all counted headers are checked) increments `unknownTemplates` and closes
assembly readiness until restart.
Evidence: RED (retargeted fatal test; 69 compile errors), affected 48/48, three mutation
checks, clean 379/379. A same-day revalidation found the XMLs, OpenAPI, binary reference,
changelog, trades, and book-maintenance pages unchanged since 2026-10-27.

### `SPEC-06` order-entry schema v19 (2026-10-27)

The OE pin, negotiation, `LogonConf` echo, and non-trading runner use v19; header stamps
above 19 fail closed. Reject reasons 0–31 (`CANCEL_ONLY`) are accepted. `NewOrderResponse`
uses the 94-byte root block below header v19 and 96 bytes at v19. `LocalOrderStateStore`
retains each order's submitted uint16 wire flags (`UNKNOWN_ORDER_FLAGS` for legacy
registration). A mismatched v19 echo fails the session closed before placement.
Evidence: compile RED (18 errors), behavioral RED 31/81, affected 132/132 across 17
classes, two mutation checks, clean 370/370, second full source download byte-identical.
Live echo behavior awaits `VAL-EC2`.

## Completed task inventory

Tests and source are the detailed record; the schema manifest is the byte-layout inventory.

| Completed IDs | Implemented behavior | Main proof |
| --- | --- | --- |
| `FND-01`–`FND-06` | Maven artifact; pinned sources/resources; byte fixtures; validated contexts, wipeable credentials, factory, explicit lifecycle, stable primitive channels | `ArtifactBaselineTest`, `ProtocolSchemasTest`, `WireTestSupportTest`, `ConfigurationTest`, `FactoryLifecycleTest`, `StarbaseLongChannelTest` |
| `COD-01`–`COD-06` | Bounds/unsigned/alignment primitives; TCP, UDP, and MD headers; exact Price9/Decimal72; schema/template/version dispatch | `codec/common/*Test.java` |
| `MDC-01`–`MDC-07` | Hardcoded MD reference, L3, trade, snapshot/cycle, and retransmit layouts; UDP packet validation; official PCAP replay | `codec/marketdata/*Test.java`, checked-in PCAP and golden trace |
| `MDT-01`–`MDT-06` | UDP receiver; feed sequencing/heartbeats; A/B arbitration; retransmit transport; snapshot synchronization; health counters | `marketdata/*Test.java` |
| `BOK-01`–`BOK-07` | Exact 64-bit instrument registry; fixed-capacity L3 store and mutations; level aggregation/priority; coherent, double-buffered publication | `book/*Test.java` |
| `MDA-01`–`MDA-06` | Channel caches; reference, book, trade, and block-trade channels; PCAP-to-channel replay; trade/block-trade context reset and join | `MarketDataChannelRoutingTest`, `OrderBookChannelTest`, `TradesChannelTest`, `BlockTradesChannelTest`, `PcapToChannelsReplayTest`, `StarbaseMarketDataAssemblyTest` |
| `OEC-01`–`OEC-06` | 25 hardcoded order-entry layouts with fail-closed dispatch | `codec/orderentry/*Test.java` |
| `OET-01`–`OET-07` | TCP frame assembly and partial writes; connection loop; authentication; heartbeat; sequence/resend; reconnect/readiness gates | `orderentry/connection/*Test.java` |
| `ORD-01`–`ORD-07`, `CLIENT-ID-01` | Correlation table; cross-session order state; command facade; exact-once fills; native and canonical String client IDs; one-send A/B routing; exact REST/SBE reconciliation | `orderentry/state/*Test.java`, `orderentry/command/*Test.java`, `OrderStateReconciliationTest` |
| `RST-01`–`RST-08` | REST transport; instruments and filters; open-order metadata and flags; cancel-all/lock/unlock; recovery cache; `Retry-After` | `rest/*Test.java` |
| `SPEC-01`–`SPEC-06` | Identity clarification; current schema/REST adoption through OE v19 with per-message version compatibility | `ProtocolSchemasTest`, `TemplateDispatchTest`, codec and assembly tests, `StarbaseTestEnvironmentMainTest` |
| `ASM-MD`, `ASM-OE` | Redundant public market-data and order-entry lifecycles with fail-closed recovery/readiness | `StarbaseMarketDataAssemblyTest`, `StarbaseOrderEntryAssemblyTest` |

Post-warm-up zero-allocation assertions cover every implemented normal path; list them with
`rg -n "[Aa]llocat(e|es|ion).*([Nn]othing|0 bytes)|0 allocated bytes" src/test/java`.
They do not replace the pending end-to-end benchmark.

## Remaining work

| ID | State | Work | Dependency |
| --- | --- | --- | --- |
| `VAL-EC2` | WAITING | Run the credential-safe non-trading validator; record private connectivity, REST/authentication, SBE A/B v19 negotiation, and available multicast evidence | Assigned private-network configuration and credentials; current source audit |
| `CON-01`–`CON-08` | TODO | Validate a representative consumer dependency, independent backend selection, lifecycle holder, book/trade adapters, execution/amend/cancel/open-order paths, and health/rollback | A consumer repository explicitly in scope; follow its own guidance |
| `VAL-01`–`VAL-07` | TODO | Artifact and consumer-graph builds; replay/recovery/TCP scenarios; end-to-end allocations (including the intermittent probes); private smoke tests including the live v19 flag echo; operations/configuration/rollback audit | All integration work |

Never bypass `ORD-07` reconciliation or any assembled readiness gate during integration.

## Exact restart procedure

1. Run `git status --short` here and in any explicitly scoped dependency/consumer
   repository; preserve user and unrelated changes. Inspect branch/remotes directly.
2. Read the contract, this file, the source review, and the schema manifest.
3. Download every source in both tables of
   [protocol-source-review.md](protocol-source-review.md) and compare SHA-256 values. If
   anything changed, update the audit and re-scope test-first before protocol or runner
   changes.
4. Preserve all completed behavior. Do not guess through the REST authentication conflict or
   bypass any reference, reconciliation, sequence, book, or session readiness gate.
5. If the private configuration/access reference is still unavailable, record that and
   stop; never invent endpoints or credentials.
6. With private access, record `VAL-EC2` active, then run the test-scope runner
   `io.contek.invoker.deribit.starbase.live.StarbaseTestEnvironmentMain` (`--help` lists
   its settings) from the private-network instance with state changes disabled. Collect its
   complete `STARBASE_TEST` output; never record secrets or private endpoints.
7. Activate `CON-01` only when a representative consumer repository is explicitly in scope:
   read its guidance and git status, record `CON-01` active, add the smallest dependency and
   backend-selection RED tests, then proceed one row at a time.

## Local verification

```text
Windows:      .\mvnw.cmd clean test
Linux/macOS:  ./mvnw clean test
Focused:      ./mvnw -B -ntp "-Dtest=StarbaseRestTransportTest,OpenOrderRecoveryCacheTest" test
Runner help:  java -cp target/test-classes:target/classes io.contek.invoker.deribit.starbase.live.StarbaseTestEnvironmentMain --help
```

The runner's `--help` output is not live validation.
