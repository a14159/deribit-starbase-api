# Starbase implementation contract

Durable technical contract; mutable progress/blockers belong only in
[implementation-status.md](implementation-status.md).

## Purpose and repository boundaries

`deribit-starbase-api` is a reusable Starbase SBE TCP/UDP and REST-utility artifact:

- Maven `io.contek.invoker:invoker-deribit-starbase-api`; package
  `io.contek.invoker.deribit.starbase`; Java 23 unless deliberately revised.
- No dependency on consumer applications/DTOs.
- `deribit-api` retains standard REST/WebSocket history, fills, positions, balances,
  account data, ticker/statistics, and execution.
- Consumers own adaptation and independently select market-data/execution backends while
  retaining standard WebSocket and legacy-multicast rollback.

Do not add FIX, FIX Drop Copy, generated SBE codecs, or runtime XML parsing. Mass quote is
outside the current scope unless a concrete caller later requires it.

## Protocol sources and versioning

Before any wire-layout change, re-download/review official docs, SBE XML, REST OpenAPI,
changelog, and SDK; never assume pre-launch pins remain valid. Record review date,
versions/semantic versions, URLs, and SHA-256 in:

- [protocol-source-review.md](protocol-source-review.md)
- [schema-manifest.md](schema-manifest.md)

XML governs wire layout when prose differs. Hardcode, bounds-check, and byte-test every
implemented schema version, template ID, block length, offset, enum, null, flag, and
padding rule. Unknown/future/unsupported state changes fail the affected session/feed
closed.

## Public architecture

Keep the high-level shape:

```text
StarbaseApiFactory
|- marketData(context)                 -> StarbaseMarketDataApi
|- orderEntry(context, credentials)    -> StarbaseOrderEntryApi
`- rest(context, credentials)          -> StarbaseRestApi

API -> stable cached channel -> registered primitive listeners
API -> owned transport/session -> hardcoded dispatcher -> state/channel routing
```

Caller contexts supply endpoints, product groups, A/B sides, interfaces, timeouts, buffers,
and spin/blocking policy; code contains no private/production endpoint. Configuration may
allocate channels, but repeated logical-stream access returns one stable object without a
new decoder/connection. Listener removal never controls transport lifetime. Callbacks run
on I/O unless an explicitly bounded alternative exists; they neither block nor retain
temporary flyweights.

## Codec and hot-path rules

- Fixed offsets; absolute access on explicitly little-endian `ByteBuffer`s.
- Reused direct send/receive buffers; no per-event decoder, message, field, slice,
  duplicate, string, collection, future, or closure.
- Separate TCP header/body offsets; validate complete frames, eight-byte alignment, and
  padding before reads.
- Treat split/coalesced/trailing-partial frames, partial/zero-progress writes, EOF, and
  truncation as explicit TCP byte-stream states.
- Keep Price9/Decimal72 exact primitives until a non-hot boundary; reject overflow,
  precision loss, and invalid nulls.
- Never truncate signed 64-bit instrument/order/client-order/match IDs.
- No per-packet/message logs; exceptional logs are rate-limited and secret-safe.

## Market-data correctness

Starbase MD is an independent L3 sequence domain; never merge standard REST L2 into it.
For each product group:

1. Join A and B incremental feeds and A and B snapshot feeds.
2. Track feeds independently; de-duplicate equivalent A/B sequences.
3. Next packet sequence is `sequenceNum + messageCount`, including zero-message heartbeat
   semantics.
4. Recover gaps via unicast retransmit, paging by actual return count within count/MTU
   limits; use a fresh Starbase snapshot when recovery is impossible.
5. Apply snapshot + buffered incrementals atomically; publish only at valid
   transaction/end-of-cycle boundaries.
6. Mark feed/book unhealthy for corrupt frames, unsupported state changes, gaps, broken
   snapshot boundaries, capacity exhaustion, or invariant failure.

Key L3 orders by exact 64-bit exchange ID; retain side, Price9 quantity, and `sortOrderId`.
Priority uses `sortOrderId`, never arrival. Reduce/delete exact orders and incrementally
aggregate changed levels for consumer L2 adapters. `TradeSummary` is reusable context for
following `Trade`s; preserve trade order/flags without intermediate decoded-message
allocation.

## Order-entry correctness and lifecycle

One TCP connection represents one API key/gateway; product group and A/B side are explicit.
A router may choose an eligible side, but sends once and never retries an ambiguous send
on its peer.

Connection lifetime is explicit:

```java
api.start();
api.isAuthenticated();
api.close();
```

Never auto-close idle sessions: disconnect can cancel live orders. Unexpected disconnect:

1. makes the session unavailable;
2. stops outbound submission;
3. marks session-scoped orders uncertain/cancelled per protocol;
4. schedules bounded reconnect/backoff; and
5. holds readiness closed until authentication, sequencing, reference data, and exact
   reconciliation all succeed.

Maintain one primitive cross-session order store. De-duplicate immediate and later
unsolicited fills in one exact match-ID domain; retain origin session for cross-session
lifecycle. Consumer string labels need a collision-free reversible int64 client-order-ID
mapping lasting as long as any related order may live.

Make unsupported semantics explicit. XML v11 has no reduce-only field: reject or route the
whole order through the configured standard backend, never drop the flag. Convert quantity
only from authoritative reference data and reject inexact values.

## REST recovery plane

Starbase REST is blocking bootstrap/recovery/administration, never live execution/MD, with
credentials separate from standard Deribit. Implemented: instruments, open orders,
portfolio cancel, lock, unlock. Open-order snapshots are immutable, cached, single-flight,
and attempted no faster than documented. Failure may retain last-good state but cannot
invent the first snapshot or restore readiness.

REST/SBE reconciliation requires an official collision-free exact identity bridge;
instrument/side/price/amount/time tuples and optional labels are invalid substitutes.

## Downstream consumer integration contract

Consumers keep exchange-neutral strategy interfaces unchanged and put independent
protocol choices in their Deribit adapter, equivalent to:

```java
enum MarketDataBackend { WEBSOCKET, LEGACY_MULTICAST, STARBASE }
enum ExecutionBackend { STANDARD, STARBASE }
```

Keep existing `multicast=true` as an alias for `LEGACY_MULTICAST`, never Starbase. Add
Starbase book/trade adapters; keep ticker/statistics on standard WebSocket. Book adapters
translate only changed aggregated levels.

Compose a Starbase execution connector. Only authoritative local state may enable place,
amend, cancel by client/exchange ID, scoped mass cancel, or cached open orders. Historical
fills, positions, balances, account summaries, and ordinary fallbacks—including
`GetUserTradesByCurrencyAndTime`—stay in `deribit-api`.

Do not widen shared instrument DTOs or truncate IDs. Resolve names via
`InstrumentRegistry`; retain exchange-ID -> client-ID/instrument mappings and origin
session; process immediate fills without awaiting duplicate unsolicited events; reject
inexact quantity and unsupported reduce-only operations.

## Health and performance contract

Expose connection/feed/book/reference/reconciliation health separately; connected !=
ready. Use bounded saturating counters for packets, messages, duplicates, gaps,
retransmits/rejects, snapshot resets, corrupt frames, unknown templates, reconnects, order
rejects, callback failures, and capacity exhaustion.

After warm-up, normal processing must allocate zero bytes for:

- UDP/TCP framing, validation, decode, and dispatch;
- sequence tracking, A/B arbitration, and gap detection;
- L3 add/reduce/delete and incremental aggregation;
- outbound order encoding and correlation/state updates; and
- eventual consumer order-book adaptation paths.

REST, configuration, initial capacity/channel creation, exceptional logging, and an
existing allocating public return type may allocate outside normal packet flow.

For every behavior: observe a deterministic failing test, make the smallest change, run
focused/affected tests, cover corrupt/boundary states, and measure hot paths after warm-up.
Prefer byte fixtures, fake clocks, scripted channels, loopback peers, and checked-in PCAP
before live infrastructure.

## Completion standard

Completion/production readiness requires:

- every status blocker/item resolved or explicitly approved out of scope;
- the public APIs compose the tested transport, recovery, state, and channel components;
- a representative consumer independently selects Starbase MD/execution without changing
  exchange-neutral interfaces;
- the Starbase artifact, `deribit-api`, and a representative consumer build and test
  together;
- official replay and allocation validation pass; and
- private test-environment lifecycle validation is recorded; absence must be stated without
  claiming readiness.
