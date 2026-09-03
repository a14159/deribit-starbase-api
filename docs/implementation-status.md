# Starbase implementation status and restart handoff

## Current checkpoint

| Field | Value |
| --- | --- |
| Overall state | **WAITING — `RST-06` complete; private `VAL-EC2` evidence is next** |
| Gate resolution date | 2026-08-27 |
| Active task | None; `RST-06` completed locally after a fresh 18/18 source-hash match |
| Blocking task | `VAL-EC2` requires the assigned private-network environment and credentials; consumer repository/guidance is absent; the REST authentication conflict remains isolated |
| Last completed implementation task | `RST-06`, fail-closed Starbase REST open-order flag validation |
| Local verification | 2026-09-03: `RST-06` focused RED failed 2/9 as intended; focused PASS 9/9; affected REST/reconciliation/assembly tests passed 42/42; isolated retry of one unrelated full-suite timing failure passed 3/3; final clean `mvnw clean test` passed 349/349. |
| Production readiness | **No** — downstream consumer integration, joint builds, private connectivity/authentication validation, and operations/rollback validation remain |
| Exact next action | On the assigned private-network instance, revalidate the official sources, leave state changes disabled, run the credential-safe `StarbaseTestEnvironmentMain`, and record its complete non-trading `STARBASE_TEST` output. |

`SPEC-02`, `ORD-07`, and `ASM-MD` completed on 2026-08-27 after the required restart audit
and test-first implementation. `ASM-OE` completed on 2026-08-28 after another complete
official-source revalidation. `SPEC-01` remains resolved by the formal clarification, but
production readiness remains closed until consumer integration and validation complete.

## 2026-09-03 `RST-06` REST open-order flag validation maintenance

The requester explicitly authorized the review's recommended REST hardening. A fresh
18/18 official-source audit matched every 2026-09-03 pin before work began, so this task is
limited to the unchanged OpenAPI contract: retain distinct optional `post_only`,
`reject_post_only`, and `reduce_only` booleans; reject explicit JSON `null`; and reject the
mutually exclusive `post_only=true` plus `reject_post_only=true` state. It does not change
wire layout, REST authentication, exact-ID reconciliation, mass-quote scope, or readiness.

`StarbaseRestApi` now distinguishes an omitted optional order flag from a present JSON
`null`, rejects present non-booleans/nulls, and rejects the contradictory dual post-only
mode before publishing any snapshot. Two valid orders with complementary flag values pin
the three JSON-to-record mappings independently. Omitted optional flags still decode as
`null`; `reduce_only` remains observation-only and cannot be submitted through SBE.

Test-first evidence:

- RED: focused `StarbaseOpenOrdersEndpointTest` ran 9 tests and failed the two new
  fail-closed cases because contradictory and explicitly null flags were accepted.
- PASS: the corrected focused suite passed 9/9; affected REST, recovery, reconciliation,
  and order-entry assembly suites passed 42/42.
- The first clean full run passed 348/349 and observed an unrelated
  `StarbaseAdministrativeEndpointsTest` timing failure; that unchanged class passed 3/3 in
  isolation, and the second clean full run passed 349/349 with no failures, errors, or
  skips.

Changed files are `StarbaseRestApi`, `StarbaseOpenOrdersEndpointTest`, and this canonical
handoff. No schema, authentication, identity reconciliation, order-entry, mass-quote,
credential, endpoint, or consumer behavior changed. Private validation remains unavailable,
so `VAL-EC2` is again the exact next action and production readiness remains closed.

## 2026-09-03 latest-source audit and `SPEC-03` completion

The required restart audit downloaded the current production/testnet OE and MD XMLs,
legacy XML bundle, REST OpenAPI, all five endpoint references, REST authentication guide,
binary reference, changelog, current OE and MD SDKs, legacy SDK, and official PCAP. Of the
18 recorded inputs, 15 hashes are unchanged and three changed:

- testnet OE XML is now schema 2101/version 15/semantic version 1.5, SHA-256
  `4BA2A80B473AC233B6DDB971158E7A65353B1E287C7B304F14062AC2E5E9106C`, byte-identical
  to the unchanged production XML; it previously pinned v14 hash
  `3F375CA809C437DB96369DF1751C702FB00E4590156C8F088E7FC2957C9020EA`;
- Binary API Reference Markdown is now SHA-256
  `E6DAA603E7E7BFD6A2BF5C5A8AD703C173668B0A37CAD1704FC44C4C7177128D`; and
- Starbase changelog Markdown is now SHA-256
  `26C77E65A3A145D276D31480B618484B1A00FFBF701D2C0F77F60140AD8DB556`.

The latest dated changelog entry is still 2026-08-25. Production OE remains v15 and both
production/testnet MD XMLs remain byte-identical v1, so no production field, offset,
template, enum, or checked-in schema-resource change is indicated. The order SDK remains
v14, the MD SDK remains v1, the REST inputs are unchanged, and the Basic-versus-Bearer
authentication conflict remains unresolved.

The changed Binary API Reference does make an existing behavior explicit: the negotiated
`Logon.schemaVersion`/`LogonConf.schemaVersion` is the session ceiling, while each server
message's TCP header `version` is the newest schema version at which that message changed,
capped at the ceiling. It gives the concrete example that after negotiating v15,
`LogonConf` has header version 12. At audit time, production code globally rejected
order-entry header versions below 11, although implemented current-layout messages such as
`OrderPlaced` last changed at version 8. The live runner additionally required response
header versions to equal the negotiated schema and still treated v15 testnet as a negative
compatibility probe. Those assumptions could reject valid server traffic and make the
pending EC2 evidence misleading.

Completed `SPEC-03` scope and acceptance evidence:

1. Re-downloaded all 18 inputs at task start; every current hash matched the recorded
   2026-09-03 pin, so there was no further source delta.
2. Added the smallest deterministic failing tests for a v15-negotiated session receiving
   current layouts with their valid earlier per-message stamps, including `LogonConf`
   header v12 and `OrderPlaced` header v8. Covered future-above-ceiling versions,
   incompatible old layouts, corrupt lengths, and unknown state-changing templates as
   fail-closed.
3. Replaced the global v11-v15 inbound assumption with a bounds-checked per-message policy
   derived from the unchanged authoritative v15 XML and current reference. Retained exact
   layout/body/group validation, rejection above the pinned ceiling, and outbound v15
   stamping.
4. Updated `StarbaseTestEnvironmentMain` and its tests to negotiate testnet v15 directly
   and validate `LogonConf.schemaVersion == 15` independently of its header stamp. Removed
   the obsolete v14/v15 compatibility branch and validated heartbeat/test-request replies
   by their documented per-message stamps rather than equality with the session ceiling.
5. Focused RED ran 15 tests and produced the intended three failures: an old Logon stamp
   was accepted, Heartbeat v0 was rejected, and the runner encoded testnet v14. After the
   correction, the affected codec, dispatcher, authentication, connection, assembly, and
   live-runner set passed 61/61. The schema review-date test separately failed 1/1 before
   the pin advanced to 2026-09-03 and then passed 1/1. A further 1/6 RED pinned the rule
   that v15 enum-only changes accept header v14 when capped by a negotiated-v14 session;
   its focused correction passed.
6. Clean `mvnw clean test` passed 346/346. The allocation-containing template-dispatch and
   message-dispatch suites passed 12/12 with no skips; their post-warm-up checks still
   assert zero bytes allocated. `.gitattributes` now forces LF for pinned schema XMLs so
   their authoritative hashes survive Windows checkouts and clean resource copies. One
   intervening run observed a transient 56-byte result in the unchanged market-data
   allocation test; an isolated retry, three additional full-class repetitions, and the
   final clean suite all passed, so no market-data code was changed.

Changed files are `.gitattributes`, `OrderEntryTemplateDispatch`, `ProtocolSchemas`, their
focused tests, order-entry reject/dispatcher/assembly fixtures, the test-scope live runner
and its tests, and this canonical documentation. No checked-in schema content, dependency,
endpoint, credential, capture, order-submission path, or unrelated protocol behavior
changed. `VAL-EC2` is restored as the exact next action; no private live evidence or
production-readiness claim is made.

## 2026-08-28 `VAL-EC2` runner handoff

This is the historical pre-`SPEC-03` runner record. The 2026-09-03 completion above
supersedes its v14-specific behavior and test evidence.

The test-scope `StarbaseTestEnvironmentMain` now gives the user's EC2 instance one bounded
entry point for the complete local Maven suite followed by private-environment validation.
It accepts an assigned host, client ID, and optional port overrides from process-local
environment variables. The secret defaults to a masked console prompt; no credential or
private endpoint is present in source, tracked documentation, fixtures, or logs. Mutable
credential and frame copies are wiped, and the Maven child process receives no
`STARBASE_*` variables.

The live phases are deliberately non-trading-first:

- credential-free TCP connections cover test SBE A/B and REST A/B on the audited AWS
  default ports;
- the production REST implementation parses public instruments on A/B, HTTP Basic public
  controls test the dedicated authentication guide, and one read-only implementation
  Bearer `get_open_orders` call captures the isolated OpenAPI/auth-guide conflict without
  exceeding its per-portfolio rate;
- one exact testnet-v14 logon per SBE side validates `LogonConf` and a correlated
  `TestRequest`/`Heartbeat` round trip, while a separate one-shot v15 compatibility probe
  records the known production-assembly/testnet mismatch without starting the reconnecting
  public assembly;
- when an interface is supplied, the production single-side market-data API joins the
  audited test incremental/snapshot multicast groups and reports packet/message/health
  counters. Retransmit is explicitly skipped because the official test service is
  unavailable on the AWS path; and
- order submission remains disabled by default. Even an exact risk acknowledgement cannot
  bypass the audited v14/v15 gate, so the current runner places, amends, and cancels no
  order.

Test-first and verification evidence:

- RED: focused compilation produced seven expected missing-main/configuration/framing
  errors.
- PASS: `StarbaseTestEnvironmentMainTest` passed 3/3, covering default read-only behavior,
  sensitive diagnostic redaction, the exact state-change acknowledgement/input guard, and
  v14 in both the TCP header and Logon body.
- PASS: clean `mvnw clean test` passed 342/342 with no failures, errors, or skips.
- PASS: a loopback closed-port `--live-only` run reached the final summary, reported 11
  expected failures plus the appropriate blocked/skipped phases, exposed none of the fake
  configured host/client/secret values, and returned failure status.

Changed files are the test-scope main, its deterministic test, and this canonical handoff.
No production source, protocol behavior, dependency, credential, endpoint, capture, or
consumer repository changed. Private live validation and production readiness remain open.

## 2026-08-28 private connectivity validation attempt

The user explicitly requested a live smoke test against the Starbase test environment and
placed a neighboring repository in scope only as the existing credential source. Its
guidance and dirty worktree were inspected before access. Credential literals were located
but were not printed, copied, written to another file, or loaded into a client because the
connection gate failed first.

The restart audit re-downloaded the production/testnet OE and MD XMLs, legacy XML bundle,
REST OpenAPI, all five endpoint references, the authentication guide, binary reference,
changelog, current OE and MD SDKs, legacy SDK, and official PCAP. All 18 SHA-256 values
matched the pins in [protocol-source-review.md](protocol-source-review.md), so no protocol
behavior or wire-layout change is authorized by this attempt.

Connectivity evidence:

- TCP connect attempts to official test SBE A/B on port 4210 and REST A/B on port 4410
  each timed out after five seconds before any authentication or application bytes were
  sent.
- No route for the Starbase test network was configured on this workstation, while a
  control TCP connection to the public Deribit test HTTPS service succeeded. This isolates
  the failure to missing private Starbase connectivity rather than general internet access.
- The official quickstart states that Starbase is unavailable over the public internet and
  requires approved private connectivity. The credential source is used by a standard
  Deribit client; it is not independent evidence that the values are separate Starbase
  credentials.
- A fresh `mvnw clean test` completed successfully with 339 tests, no failures, errors, or
  skips.

No live logon, heartbeat, REST authentication comparison, market-data subscription,
retransmit, or order lifecycle was attempted. No source or test harness was added, and this
evidence does not claim private validation or production readiness. Once private routing is
available, repeat reachability first, then use process-local credential copies and wipe them
after a non-trading SBE authentication/heartbeat and REST read-only smoke test.

## 2026-08-28 allocation-test maintenance

`FeedSequenceTrackerTest.testNormalSequenceTrackingAllocatesNothingAfterWarmup` was
intermittently charging the JDK 25 thread-allocation probe's one-time runtime
initialization to the tracker. The reported failure observed 312 bytes; local reproduction
failed 2 of 5 fresh Maven forks with 240 bytes even after increasing tracker warm-up and
using the same loop call site. A discarded allocation-counter read immediately before the
baseline isolates that initialization while leaving the measured tracker workload and its
zero-allocation assertion unchanged. The test now also checks support and explicitly
enables thread-allocation accounting.

RED/pass evidence:

- RED: the original focused test failed in 2/5 fresh Maven forks with 240 allocated bytes.
- PASS: the corrected test passed 20/20 fresh Maven forks; the complete
  `FeedSequenceTrackerTest` passed 7/7.
- The affected synchronized `FeedArbitratorTest` independently reproduced the same class
  of warm-up noise with 168 bytes. Probe calibration alone later failed fresh fork 11 with
  224 bytes, so its warm-up was strengthened from 100,000 to 1,000,000 operations as well
  as calibrating the probe. The corrected class passed 20/20 fresh Maven forks.
- The first clean full-suite run exposed an independent test synchronization race:
  `OrderEntryConnectionTest` observed the volatile failed state before its scripted
  transport finished closing. The scripted transport now publishes a close latch before
  the test reads its non-volatile call counter. That test passed 10/10 fresh forks, the
  combined focused suites passed 19/19, and final `mvnw clean test` passed 339/339 with no
  failures, errors, or skips.

Only tests and this handoff changed. No production or protocol behavior changed, so the
complete official-source revalidation recorded below for 2026-08-28 remains current and
no source conflict was reopened. Production readiness remains closed for the unchanged
consumer/live-validation prerequisites.

## `ASM-OE` completion handoff

`StarbaseApiFactory.orderEntry(A, B, recovery)` now constructs one public order-entry API
that validates an exact matching-product/opposite-side pair and owns independent A/B
credentials, TCP transports, frame loops, authentication, liveness, sequence state,
correlations, and reconnect gates. Initial v15 logon resets sequence state; reconnect
logon preserves exact inbound/outbound continuity and does not reset it. Heartbeats keep
idle sessions alive, inbound gaps request the exact missing range, and any unresolved
side gap closes global routing until resend recovery completes.

The paired API owns one exact `LocalOrderStateStore`, fill de-duplicator, origin router,
REST recovery cache, and per-session `OrderStateReconciliation`. Disconnect never retries
an ambiguous command on the peer, invalidates both routing gates, and requires a later
fresh exact REST snapshot before either side can route again. Failed, stale, REST-only,
SBE-only, duplicate, malformed, pending, terminal-present, or capacity-exhausted recovery
stays fail-closed. The identity is only the REST decimal string parsed by
`Long.parseLong` and compared with the exact SBE `int64` order ID.

Public limit/market/amend/cancel/scoped-mass-cancel paths preserve origin-session routing,
correlation, authoritative status/quantity, queued and terminal lifecycles, immediate and
unsolicited fills, and stable primitive event channels. Native signed-long and String
client IDs are supported. Cancel by exchange ID resolves the exact authoritative local
mapping and preserves every signed non-sentinel `int64` order ID. Unsupported or
inconsistent state-changing messages fail the affected session and global readiness
closed. Connection, authentication, reference, reconciliation, session-state, and trading
readiness observations remain separate.

Test-first evidence:

- RED: the initial assembly test compile produced 13 expected missing API/constructor
  errors. Deterministic follow-ups exposed open readiness on a peer gap, peer routing after
  disconnect, missing immediate-amend fill handling, quantity-exponent mismatch, queued
  state, paired factory construction, and ambiguous-write behavior. Public health,
  String-ID, and exchange-ID additions later produced 11 expected compile errors; the
  reference health getter produced two more.
- PASS: focused order-entry assembly/codec/connection/factory verification is 48/48;
  `StarbaseOrderEntryAssemblyTest` is 12/12; and the clean full suite is 339/339 with no
  failures, errors, or skips.
- The public new-order encode/route/state path measures zero allocated bytes after 3,000
  warm-up operations. Earlier 1,000-operation probes observed one-time JVM warm-up totals
  of 880 and 584 bytes, so they were not recorded as a steady-state pass.

Changed production includes `StarbaseOrderEntryApi`, `StarbaseApiFactory`,
`OrderCommandFacade`, `OrderSessionRouter`, `OrderEntryConnection`,
`AuthenticationStateMachine`, `SessionLiveness`, `LocalOrderStateStore`,
`OrderFillProcessor`, exact signed `CancelOrderById` validation, and the new
`SocketOrderEntryTransport`. Changed tests include the new
`StarbaseOrderEntryAssemblyTest` and `SocketOrderEntryTransportTest` plus affected
factory/session/liveness/cancel coverage. Private live validation was unavailable; this
task does not claim production readiness.

## `ASM-MD` completion handoff

`StarbaseApiFactory.marketData(A, B)` now returns one `StarbaseMarketDataApi` that validates
an exact matching-product A/B pair and owns four independent receiver loops plus both
retransmit transports. Per-feed cursors and diagnostics remain separate; message-level
arbitration accepts the first valid A/B copy, suppresses duplicates, and lets either side
continue contiguous progress.

An incremental gap synchronously requests the exact missing global range through the
affected side's bounded reusable retransmit client. Successful pages dispatch through the
same validator/arbitrator before the held message is retried. Timeout, reject, or exhausted
recovery invalidates configured books and waits past an `EndOfCycle` boundary for a whole
fresh Starbase snapshot cycle; REST L2 is never introduced. Per-side held cursors advance
only after recovery or safe abandonment, avoiding repeated false gaps.

Configured books use the existing fixed-capacity `AtomicBookSnapshot` behind
`ReconstructedOrderBookState`: snapshot mutations build staging books, concurrent
incrementals buffer by exact sequence, and no replacement level is published before the
trailer and complete-cycle boundary. Activation swaps the aggregate, emits an explicit
invalidation followed by the complete current levels, and then resumes coherent live
deltas. Late book configuration closes synchronization and requires another fresh cycle.
Unsupported state-changing template 33 fails the affected input and global readiness
closed; structural/feed failures remain visible in the corresponding diagnostics.
`IndexDefinition`/`IndexInfo` identities are now routed to the stable reference channel.

Public `isReady()` is distinct from transport connectivity and requires an owned redundant
lifecycle, at least one usable incremental and snapshot input, an initialized global
incremental cursor, a complete fresh snapshot cycle, no fatal assembly state, and every
configured book ready. Explicit close owns all receiver/retransmit resources. The legacy
single-context constructor remains usable for per-side diagnostics/replay but can never
claim redundant readiness.

Test-first evidence:

- RED: `StarbaseMarketDataAssemblyTest` initially failed compilation with 38 missing public
  assembly symbols; the late-book recovery case then failed functionally until it requested
  a fresh cycle; and the recovered per-feed cursor test failed compilation with two missing
  method errors.
- PASS: deterministic A/B arbitration, duplicate, retransmit success/failure, fresh-cycle,
  buffered overlap, late subscription, unsupported-state, four-loop lifecycle, readiness,
  and reference-routing tests pass; clean full suite 322/322 with no failures, errors, or
  skips.
- The post-warm-up live A/B arbitration, validation, dispatch, L3 mutation, aggregation,
  and channel-publication path measures zero allocated bytes.

Changed production: `StarbaseApiFactory`, `StarbaseMarketDataApi`,
`ReconstructedOrderBookState`, `OrderBookStateRouter`, `FeedSequenceTracker`,
`FeedArbitrator`, `AggregatedL3Book`, `PriceLevelStore`, and the new
`PriceLevelConsumer`. Changed tests: the new `StarbaseMarketDataAssemblyTest`, expanded
transport lifecycle/feed-sequence/channel-routing coverage, and existing affected market
data/book suites. Private live validation was unavailable; this task does not claim
production readiness.

## `ORD-07` completion handoff

`StarbaseRestApi` now parses each `get_open_orders` `order_id` with exact
`Long.parseLong` semantics and exposes the result as a primitive `long`. It does not trim,
normalize, parse UUIDs, inspect labels, compare tuples, or use legacy currency-prefixed
IDs. Malformed and overflowed values, the SBE `Long.MIN_VALUE` null sentinel, and
duplicate/ambiguous primitive identities fail the entire snapshot closed.

`OrderStateReconciliation` refreshes the rate-limited REST recovery cache, requires a
fresh successful post-disconnect snapshot, and atomically compares the REST identities
with `LocalOrderStateStore`. Exact live matches plus absent terminal orders are the only
success case. REST-only, SBE-only, terminal-present, pending-local, duplicate, invalid,
over-capacity, stale, failed, and disconnected states clear the reconciliation readiness
gate. A refresh failure also closes a previously ready session even though the cache
retains its last-good snapshot.

Test-first evidence:

- RED: the required parser and `OrderStateReconciliationTest` additions failed
  compilation with 18 expected errors against the old String record and missing
  reconciliation API.
- PASS: focused parser/cache/store/readiness/reconciliation tests 28/28 and clean full
  suite 311/311 with no failures, errors, or skips.

Changed production: `StarbaseOpenOrder`, `StarbaseRestApi`, `OpenOrderRecoveryCache`,
`LocalOrderStateStore`, `ReconnectReadiness`, and the new `OrderStateReconciliation`.
Changed tests: `StarbaseOpenOrdersEndpointTest`, `OpenOrderRecoveryCacheTest`, and the new
`OrderStateReconciliationTest`. `ORD-07` is complete as a fail-closed component; `ASM-OE`
must compose it into the public order-entry lifecycle before that API can report ready.

## `SPEC-02` completion handoff

The restart audit re-downloaded the current production/testnet OE and MD XMLs, legacy XML
bundle, current order and MD SDKs, legacy SDK, REST OpenAPI and endpoint/authentication
references, binary reference, changelog, and official PCAP. Core hashes and versions were
unchanged from the 2026-08-27 handoff. Exact URLs and hashes are recorded in
[protocol-source-review.md](protocol-source-review.md).

Applicable source deltas are adopted:

- checked-in OE XML and metadata now pin production schema 2101/v15/semantic 1.5;
- `Logon` hardcodes the 68-byte v15 body, production-v15 schema negotiation, and the
  session cancel-on-disconnect flag; `LogonConf` hardcodes its 6-byte body and must echo
  v15 in the assembled production lifecycle; order-entry frames accept the audited
  compatible range v11-v15 but reject future versions;
- session reject reason 6 and order/amend reject reason 30 are accepted, while later
  values fail closed;
- corrected MD v1 adds `IndexInfo` template 12, changes `InstrumentInfo` to 32 bytes with
  mark price at offset 24, and changes `InstrumentRef` to 56 bytes with optional open
  interest at offset 48; and
- REST instruments retain optional `qty_tick_size`, while open orders retain optional
  `reject_post_only`.

The current REST sources conflict: the dedicated authentication guide requires HTTP Basic
on every request, while the OpenAPI requires Bearer on private endpoints and explicitly no
authentication on public instruments. No live evidence or formal clarification resolves
that conflict. `SPEC-02` therefore preserves the existing OpenAPI-shaped Bearer/public
behavior, records the conflict, and keeps readiness closed; it does not guess or silently
switch credentials. The unchanged official PCAP does not contain templates 12, 14, or 15,
so it remains valid evidence for the unaffected packet/message paths but cannot validate
the corrected reference layouts.

Test-first evidence:

- RED: the focused compile failed with 17 expected missing-symbol/signature errors for the
  new schema metadata, session fields, `IndexInfoDecoder`, open interest, and REST model
  accessors.
- PASS: focused source-adoption tests 41/41; boundary follow-up 16/16; and clean full suite
  303/303 with no failures, errors, or skips.
- Corrected reference/session decoder allocation checks remain zero bytes after warm-up.

Changed production/resources: `ProtocolSchemas`, order-entry version/session/reject codecs,
the new `IndexInfoDecoder`, corrected `InstrumentInfoDecoder`/`InstrumentRefDecoder`, MD
dispatch, REST records/parser, and both checked-in schema XMLs. Changed tests: protocol
pins, session/new/amend/dispatch/reference codec tests, and REST instrument/open-order
endpoint tests. Private live validation was unavailable; `SPEC-02` does not claim
production readiness.

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
| `RST-01`–`RST-06` | Configured bearer/no-auth HTTP transport; instruments and registry bootstrap; fail-closed open-order parsing/flag validation; cancel-all/lock/unlock; rate-limited recovery cache | `rest/*Test.java` |
| `SPEC-02`, `ORD-07` | Current production schema/REST model adoption and exact decimal REST/SBE identity reconciliation | Protocol/REST/codec tests and `OrderStateReconciliationTest` |
| `ASM-MD`, `ASM-OE` | Redundant public market-data and order-entry lifecycles with fail-closed recovery/readiness | `StarbaseMarketDataAssemblyTest`, `StarbaseOrderEntryAssemblyTest` |

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
  routing, and the assembled public new-order encode/route/state path.

The individual measured methods remain discoverable with:

```powershell
rg -n "[Aa]llocat(e|es|ion).*([Nn]othing|0 bytes)|0 allocated bytes" src/test/java
```

These unit assertions do not replace the pending end-to-end benchmark.

## `OpenOrderRecoveryCache` handoff

[`OpenOrderRecoveryCache`](../src/main/java/io/contek/invoker/deribit/starbase/rest/OpenOrderRecoveryCache.java)
retains the last-good REST snapshot and its successful-refresh generation/timestamp:

- rejects intervals below one minute; synchronized `get()`/`refresh()` permit one
  synchronous expired-snapshot load;
- publishes immutable `List.copyOf` results; every attempt (including failure) advances
  the rate window, which `invalidate()` cannot bypass;
- surfaces refresh failure while retaining last-good state; initial failure leaves
  `hasSnapshot()` false and rate-limited reads fail honestly; and
- counters/deadlines saturate.

[`OpenOrderRecoveryCacheTest`](../src/test/java/io/contek/invoker/deribit/starbase/rest/OpenOrderRecoveryCacheTest.java)
covers the cache contract. The cache itself never maps REST/SBE IDs or mutates local
state; `OrderStateReconciliation` owns that fail-closed comparison and readiness gate.

## Known assembly and validation gaps

Green component/replay tests do **not** make the public APIs a complete client:

1. The redundant `StarbaseOrderEntryApi` lifecycle is assembled and tested locally, but no
   private order-entry/REST environment or downstream execution adapter has validated it.
2. The redundant `StarbaseMarketDataApi` lifecycle is assembled and tested locally, but no
   private multicast/retransmit environment or downstream health adapter has validated it.
3. Index definition/info identities are published to the primitive reference channel but
   there is no named index registry. `BlockTrade` template 33 has no codec and fails closed.
4. The REST authentication guide requires Basic while the OpenAPI specifies Bearer/private
   and unauthenticated instruments. The configured OpenAPI-shaped behavior remains
   isolated pending clarification or private live evidence.
5. No downstream Starbase dependency/backend choice, stream/execution adapter, or health
   integration is validated.
6. No joint dependency-graph build or private environment, credentials, authenticated
   flow, deployment configuration, or rollback validation exists.

## Remaining work

| ID | State | Work | Dependency |
| --- | --- | --- | --- |
| `SPEC-01` | DONE | Formal clarification: Starbase REST `order_id` is the decimal string serialization of SBE `orderId` | Deribit support response dated 2026-08-27 |
| `SPEC-02` | DONE | Adopted applicable production OE v12-v15, corrected MD v1, and unconflicted REST model deltas; unresolved REST authentication conflict isolated with readiness closed | Current official sources; `SPEC-01` resolved |
| `SPEC-03` | DONE | Adopted testnet OE v15 and the documented distinction between negotiated session ceiling and per-message TCP header version; corrected dispatcher and live-runner assumptions test-first | 2026-09-03 official-source audit; unchanged production OE v15 layout |
| `RST-06` | DONE | Reject contradictory or explicitly null REST open-order flags while retaining distinct optional `post_only`, `reject_post_only`, and `reduce_only` mappings | Unchanged current OpenAPI; fresh 18/18 source audit |
| `ORD-07` | DONE | Parse the clarified exact ID and reconcile missing, extra, matching, duplicate, invalid, and ambiguous REST/SBE orders before restoring readiness | `SPEC-02` |
| `ASM-MD` | DONE | Compose both A/B feed instances, arbitration, retransmit, snapshot fallback, atomic books, and health into the public market-data lifecycle | `SPEC-02`; existing MD components |
| `ASM-OE` | DONE | Composed TCP A/B sessions, dispatcher, state, commands, routing, events, exact REST recovery, and fail-closed readiness into `StarbaseOrderEntryApi` | `ORD-07`; existing OE components |
| `CON-01`–`CON-08` | TODO | Validate a representative consumer dependency, independent backend selection, lifecycle holder, book/trade adapters, execution/amend/cancel/open-order paths, and health/rollback behavior | `ASM-MD`, `ASM-OE`; follow the consumer repository's own guidance |
| `VAL-01`–`VAL-07` | TODO | Full artifact and consumer-graph builds, replay/recovery/TCP scenarios, end-to-end allocations, private smoke tests, and operations/configuration/rollback audit | `SPEC-03`; all integration work |

Do not bypass the completed `ORD-07` reconciliation or any assembled readiness gate during
consumer integration.

## Exact restart procedure

1. Inspect `git status --short` in this repository and any dependency or consumer
   repository explicitly placed in scope. Preserve user and unrelated changes.
2. Read [implementation-contract.md](implementation-contract.md), this file,
   [protocol-source-review.md](protocol-source-review.md), and
   [schema-manifest.md](schema-manifest.md).
3. Download the current direct production/testnet SBE XMLs, legacy XML bundle, REST
   OpenAPI/reference/authentication docs, binary reference/changelog, current order SDK,
   current MD SDK, and legacy SDK. Compute hashes and compare them with the 2026-09-03
   revalidation record.
4. If the source set still matches, preserve completed `SPEC-03` and run the credential-safe
   EC2 validation with state changes disabled. If it changed again, update the audit and
   re-scope before protocol or live-runner changes.
5. Preserve completed `SPEC-02`, `ORD-07`, `ASM-MD`, and `ASM-OE` behavior. Do not guess
   through the isolated REST authentication conflict or bypass any reference,
   reconciliation, sequence, book, or session readiness gate.
6. Collect the credential-safe runner's complete non-trading `STARBASE_TEST` output from
   the private-network instance. Do not enable order submission during this phase.
7. Do not activate `CON-01` until a representative consumer repository is explicitly in
   scope. Then inspect that repository's own guidance and git status before planning or
   editing it.
8. Record `CON-01` active before consumer production changes. Add the smallest dependency
   and independent-backend-selection RED tests, then work through consumer integration
   and validation rows one at a time.

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
