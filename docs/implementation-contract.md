# Starbase implementation contract

This file retains the durable requirements from the original implementation brief. It is
the technical contract for resuming work; mutable progress and the current blocker belong
in [implementation-status.md](implementation-status.md).

## Purpose and repository boundaries

`deribit-starbase-api` is a separate reusable Maven artifact for Starbase SBE TCP, SBE
UDP, and REST utility interfaces used by downstream consumers.

- Coordinates: `io.contek.invoker:invoker-deribit-starbase-api`.
- Package root: `io.contek.invoker.deribit.starbase`.
- Java release: 23 unless deliberately revised.
- It must not depend on consumer applications or their DTOs.
- `deribit-api` remains responsible for standard Deribit REST/WebSocket history, fills,
  positions, balances, account data, ticker/statistics, and standard execution.
- Each downstream consumer owns its backend selection and adaptation. Market-data and
  execution backends must be independently selectable, with standard WebSocket and legacy
  multicast rollback paths retained.

Do not add FIX, FIX Drop Copy, generated SBE codecs, or runtime XML parsing. Mass quote is
outside the current scope unless a concrete caller later requires it.

## Protocol sources and versioning

Before changing a wire layout, re-download and review the current official documentation,
SBE XML bundle, REST OpenAPI, changelog, and SDK. Never assume the currently pinned
pre-launch versions remain valid. Record the new review date, versions, semantic versions,
source URLs, and SHA-256 values in:

- [protocol-source-review.md](protocol-source-review.md)
- [schema-manifest.md](schema-manifest.md)

The XML is the canonical wire-layout reference when prose and XML disagree. Every
implemented schema version, template ID, block length, offset, enum, null value, flag, and
padding rule must be hardcoded, bounds checked, and covered by byte-layout tests. Unknown,
future, or unsupported state-changing messages fail the affected session or feed closed.

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

All network endpoints, product groups, A/B sides, interfaces, timeouts, buffer sizes, and
spin/blocking policies come from caller-supplied contexts. Do not bake private or
production endpoints into code.

Channel objects may allocate during configuration, but repeated access for the same
logical stream must return a stable object and must not create another decoder or network
connection. Listener removal never controls transport lifetime. Callbacks run on the I/O
thread unless an explicitly bounded alternative is added; they must not block or retain a
temporary flyweight view.

## Codec and hot-path rules

- Use fixed offsets and absolute access on explicitly little-endian `ByteBuffer` objects.
- Reuse direct receive/send buffers. Do not allocate a decoder, message, field object,
  slice, duplicate buffer, string, collection, future, or closure per wire event.
- Keep TCP header/body offsets separate, validate the full frame before reading, align TCP
  frames to eight bytes, and validate padding.
- Treat TCP as a byte stream: split headers/bodies, coalesced frames, trailing partials,
  partial writes, zero-progress writes, EOF, and truncation are all explicit states.
- Preserve Price9 and Decimal72 as exact primitives until a non-hot API boundary requires
  conversion. Reject overflow, precision loss, and invalid null use.
- Never truncate signed 64-bit Starbase instrument, order, client-order, or match IDs.
- Do not log per packet/message; exceptional logs must be rate limited and secret safe.

## Market-data correctness

Starbase market data is an independent L3 sequence domain. Never merge a standard REST L2
snapshot into it.

For every configured product group, the completed client must:

1. Join A and B incremental feeds and A and B snapshot feeds.
2. Track each feed independently and de-duplicate equivalent A/B messages by sequence.
3. Compute the next packet sequence as `sequenceNum + messageCount`, including the
   protocol's zero-message heartbeat behavior.
4. Recover gaps through the unicast retransmit gateway, page by the actual returned count,
   respect count/MTU limits, and fall back to a fresh Starbase snapshot when recovery is
   impossible.
5. Apply snapshot and buffered incrementals atomically, and publish only at a valid
   transaction/end-of-cycle boundary.
6. Mark a feed/book unhealthy on corrupt framing, unsupported state changes, sequence
   gaps, broken snapshot boundaries, capacity exhaustion, or book invariant failures.

The L3 book is keyed by exact 64-bit exchange order ID and retains side, Price9 quantity,
and `sortOrderId`. Priority comes from `sortOrderId`, not packet arrival order. Quantity
reductions and deletes target an exact order. Changed price levels are aggregated
incrementally for eventual consumer L2 adapters.

`TradeSummary` supplies reusable context for the following `Trade` records. The logical
trade stream must preserve order and exact flags without allocating intermediate decoded
messages.

## Order-entry correctness and lifecycle

One TCP connection represents one API key on one gateway instance. Product group and A/B
side are explicit. A router may select an eligible side, but one order is sent exactly
once; an ambiguous send failure must never be retried on the peer automatically.

Connection lifetime is explicit:

```java
api.start();
api.isAuthenticated();
api.close();
```

An idle session must not auto-close because disconnect can cancel live orders. An
unexpected disconnect immediately:

1. makes the session unavailable;
2. stops outbound submission;
3. marks session-scoped orders uncertain/cancelled as required by the protocol;
4. schedules bounded reconnect/backoff; and
5. keeps readiness closed until authentication, sequence state, reference data, and exact
   order reconciliation all succeed.

Maintain one consolidated primitive local order-state store across sessions. Process
immediate response fills and later unsolicited fills through the same exact match-ID
de-duplication domain. Retain the originating session for cross-session lifecycle
handling. String labels presented by consumers require a collision-free reversible int64
client-order-ID mapping that persists for the lifetime of every potentially live order.

Unsupported semantics are explicit. In particular, the pinned v11 XML has no reduce-only
wire field: reject the operation or route the entire order through the configured standard
backend; never drop the flag. Quantity conversion must use authoritative reference data
and reject inexact values.

## REST recovery plane

Starbase REST is a blocking bootstrap, recovery, and administrative plane, never the live
execution or market-data path. Its credentials are separate from standard Deribit
credentials.

The current implemented subset is instruments, open orders, portfolio-wide cancel, lock,
and unlock. Open-order snapshots must be immutable, cached, single-flight, and attempted no
more often than the documented limit. A failed refresh may retain a last-good snapshot but
must never invent an initial snapshot or restore readiness by itself.

REST-to-SBE order reconciliation requires an officially documented, collision-free exact
identity bridge. Instrument/side/price/amount/time tuples and optional labels are not valid
substitutes.

## Downstream consumer integration contract

When integration resumes, a downstream consumer should keep its exchange-neutral strategy
interfaces unchanged and add protocol choices inside its Deribit-specific adapter. Model
market-data and execution selection as independent concepts equivalent to:

```java
enum MarketDataBackend { WEBSOCKET, LEGACY_MULTICAST, STARBASE }
enum ExecutionBackend { STANDARD, STARBASE }
```

If a consumer already supports a `multicast=true` setting, retain it as a compatibility
alias for `LEGACY_MULTICAST`; do not reinterpret it as Starbase. Add Starbase order-book
and trade-stream adapters while leaving ticker/statistical streams on the standard
WebSocket path. A book adapter must translate only changed aggregated levels rather than
rebuild the whole book per event.

Add a Starbase-backed execution connector by composition. It may provide place, amend,
cancel-by-client-ID, cancel-by-exchange-ID, scoped mass cancel, and cached open-order
behavior only after the local state is authoritative. Historical fills, positions,
balances, account summaries, and ordinary standard fallbacks continue through
`deribit-api`, including `GetUserTradesByCurrencyAndTime`.

Never widen a consumer's shared instrument DTO or truncate a Starbase ID just for this
integration. Resolve market names through `InstrumentRegistry`. Preserve exchange-ID to
client-ID/instrument mappings for amend/cancel, process immediate fills without waiting
for a duplicate unsolicited event, retain the originating session, and reject inexact
quantity conversions and unsupported reduce-only behavior.

## Health and performance contract

Expose connection/feed/book/reference/reconciliation health separately. Connected is not
the same as ready. Use bounded, saturating counters for packets, messages, duplicates,
gaps, retransmits/rejects, snapshot resets, corrupt frames, unknown templates, reconnects,
order rejects, callback failures, and capacity exhaustion.

After warm-up, normal processing must allocate zero bytes for:

- UDP/TCP framing, validation, decode, and dispatch;
- sequence tracking, A/B arbitration, and gap detection;
- L3 add/reduce/delete and incremental aggregation;
- outbound order encoding and correlation/state updates; and
- eventual consumer order-book adaptation paths.

REST calls, configuration, initial capacity/channel creation, exceptional logging, and an
existing allocating public return type may allocate outside the normal packet path.

Every new behavior follows deterministic test-first development: observe the intended
failure, implement the smallest change, run focused and affected tests, cover corrupt and
boundary states, and measure allocation-sensitive paths after warm-up. Prefer byte
fixtures, fake clocks, scripted channels, loopback peers, and checked-in PCAP data before
live infrastructure.

## Completion standard

Do not call the implementation complete or production-ready until:

- the blocker and every remaining item in [implementation-status.md](implementation-status.md)
  is resolved or explicitly approved out of scope;
- the public APIs compose the tested transport, recovery, state, and channel components;
- a representative consumer supports independent Starbase market-data/execution selection
  without changing exchange-neutral interfaces;
- the Starbase artifact, `deribit-api`, and a representative consumer build and test
  together;
- official replay and allocation validation pass; and
- private test-environment lifecycle validation is recorded, or its absence is stated
  without claiming readiness.
