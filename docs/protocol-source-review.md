# Starbase protocol source review

Reviewed and revalidated 2026-10-27. Checked-in schemas and the implemented template map
are recorded in [schema-manifest.md](schema-manifest.md); mutable work and verification
evidence belong in [implementation-status.md](implementation-status.md).

The restart audit downloaded all 18 required official inputs and both cancellation
references byte-for-byte; a second complete download matched every byte. Seven required
hashes match the 2026-10-04 audit and eleven changed, as did both cancellation references.
Production and testnet OE are byte-identical schema 2101/version 19/semantic version 1.5.
Relative to the checked-in v17 source, the XML adds exactly two things:

- v18: `OrderRejectReason.CANCEL_ONLY` value 31 (`sinceVersion="18"`), used by
  `NewOrderReject` (202) and `AmendOrderReject` (212); and
- v19: `NewOrderResponse` (200) field `flags` (`OrderFlags`, uint16, id 96,
  `sinceVersion="19"`) at root-block offset 94, growing the root block from 94 to 96 bytes
  before the unchanged `fills` and `legs` groups.

No other field, enum, set, offset, group, or message changed. Production/testnet MD remain
byte-identical schema 2102/version 1/semantic version 1.0. The legacy XML bundle, all
three SDKs (order SDK still v14), and the official PCAP remain unchanged.

The latest dated changelog entry is 2026-10-13 and documents only OE v18/v19; both changes
are additive. The 2026-09-22 entry documents OE v17 and current REST instrument
filters/open-order metadata; the 2026-09-24 entry adds a read-only risk-limit endpoint and
specifies a ten-per-minute, per-portfolio/per-gateway open-order limit. The implementation
retains its conservative one-minute minimum recovery interval. The risk-limit endpoint
remains outside the existing five-call REST contract. The Binary API Reference now also
lists historical order-entry XMLs by semantic version; they are not current inputs.

## Authoritative downloads and SHA-256

Hashes below cover the direct XML/JSON/archive/PCAP bytes and the indicated Markdown
representations, not rendered HTML. XML governs wire layout when prose differs. The direct
production XML links in the Binary API Reference remain authoritative; legacy bundles and
SDKs must not be used to infer current layouts.

| Source and reviewed result | Download URL | SHA-256 |
| --- | --- | --- |
| Production order-entry XML: schema 2101/v19/semantic 1.5 | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api.xml | `5A1BF2FDE696424CA35F468815D879DF096AC0B14E92A93BEA1BD1C433D58283` |
| Testnet order-entry XML: byte-identical to production | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api-testnet.xml | `5A1BF2FDE696424CA35F468815D879DF096AC0B14E92A93BEA1BD1C433D58283` |
| Production market-data XML: schema 2102/v1/semantic 1.0 | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api.xml | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |
| Testnet market-data XML: byte-identical to production | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api-testnet.xml | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |
| Legacy XML bundle: older OE v12, audit input only | https://statics.deribit.com/files/deribit-sbe-xmls.zip | `4B21E0F317B0C62BFDD3C77E0BC125EFD043A71493406FC45A3A00CE64297B42` |
| Starbase REST OpenAPI: OpenAPI 3.0.3/API 2.0 | https://docs.deribit.com/specifications/starbase_rest_openapi.json | `81A73CFF2A29230B8E093DB9EB085EDA4068DC534F2FB23D50861C910448CA27` |
| REST Order Gateway Authentication | https://docs.deribit.com/starbase/rest-authentication.md | `39A2D948DD6A244C8B02941FCF0C74C2F88498D9C913BF1F8CC136847D534488` |
| REST Get Open Orders | https://docs.deribit.com/api-reference/trading/get-open-orders.md | `646D54E697015C014957E82450D882C0D7D6C29E39B642902FFE73EF9181C25F` |
| REST List Instruments | https://docs.deribit.com/api-reference/market-data/list-instruments.md | `68632E9FD59D55B8C3358BECBCCB28932BE37BA53658D3CB1747862A33143AE8` |
| REST Mass Cancel | https://docs.deribit.com/api-reference/portfolio-management/mass-cancel.md | `F4E97B59603739D91B5296EA0B59EAC86C1F310C21E6FA487B8646749373DB41` |
| REST Lock Portfolio | https://docs.deribit.com/api-reference/portfolio-management/lock-portfolio.md | `0BBD3BE311257CD28F315CBD5AB053A9EB5263AC9D22C5AC990EA26EB0A838AC` |
| REST Unlock Portfolio | https://docs.deribit.com/api-reference/portfolio-management/unlock-portfolio.md | `87198D2D8C6F637E0B8F40E23EA1B570E4BE4D64CA82B4E8B8CBC34FD07A47D8` |
| Binary API Reference | https://docs.deribit.com/starbase/binary-api-reference.md | `9E30C581857820CEF83550A1CC28F150DCFBC3F8E9BCE8E0F54EA93AD58CC54E` |
| Starbase changelog | https://docs.deribit.com/changelogs/starbase.md | `741C17D90321C0BB7A8A996D64F3AC0347E9D73535ED49B8717EC38F55389882` |
| Order-entry SDK: v14/semantic 1.5, audit input only | https://docs.deribit.com/starbase/starbase-deribit-order-sdk-14.0.zip | `25B23E41E1FB92E290DD6D4E4124A9A69C2E215274C6957C22DE4BCFB8D6392D` |
| Market-data SDK: v1/semantic 1.0 | https://docs.deribit.com/starbase/starbase-deribit-md-sdk-1.0.zip | `6E235798278243307F57EE88F2E11FBE7C01B24E6423D08149D6881F48446EC4` |
| Legacy SDK: 0.5.1, schema v11, audit input only | https://statics.deribit.com/files/starbase-deribit-sdk.zip | `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C` |
| Official market-data PCAP: 5,344,700 bytes | https://statics.deribit.com/files/starbase-market-data.pcap | `980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF` |

The v17–v19 behavior and REST limits were additionally checked against these current
official references:

| Source | Download URL | SHA-256 |
| --- | --- | --- |
| Cancelling an Order | https://docs.deribit.com/starbase/cancelling-order.md | `6E823986021D5A6BAE1F82226E8034E85B5EA8C55D25877899EF5652DD3A5D42` |
| Unsolicited Events | https://docs.deribit.com/starbase/unsolicited-events.md | `8ED2C7AE75469961C37CCC7D5A15FB861DB7B34F3D1742C24736D6DA4BA99062` |
| Placing a New Order | https://docs.deribit.com/starbase/placing-new-order.md | `A6B913FA089DB3753EB9A4F4115A3166711914676DFFC18D443C72D663DED639` |
| API Rate Limits | https://docs.deribit.com/starbase/api-rate-limits.md | `FD3C92E78C631F26B47B9E410626E3F6659E8A9CCE254635A368450F32424ACE` |

Related official concept references remain the
[overview](https://docs.deribit.com/starbase/overview),
[connectivity guidance](https://docs.deribit.com/starbase/connectivity-best-practices),
[gateway connectivity](https://docs.deribit.com/starbase/gateway-connectivity),
[multicast channels](https://docs.deribit.com/starbase/multicast-channels),
[subscription guide](https://docs.deribit.com/starbase/multicast-subscription-guide),
[book maintenance](https://docs.deribit.com/starbase/order-book-maintenance),
[trades](https://docs.deribit.com/starbase/trades),
[retransmit](https://docs.deribit.com/starbase/retransmit-gateway),
[session messages](https://docs.deribit.com/starbase/session-messages),
[new orders](https://docs.deribit.com/starbase/placing-new-order),
[amends](https://docs.deribit.com/starbase/amending-order), and
[mass cancel](https://docs.deribit.com/starbase/mass-cancel).

## Current order-entry behavior and pins

| Schema | Schema ID | Version | Semantic version | Checked-in XML SHA-256 |
| --- | ---: | ---: | ---: | --- |
| Order entry | 2101 | 19 | 1.5 | `5A1BF2FDE696424CA35F468815D879DF096AC0B14E92A93BEA1BD1C433D58283` |
| Market data | 2102 | 1 | 1.0 | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |

The public client negotiates v19, and the assembled lifecycle requires `LogonConf` to echo
v19.

OE v19 echoes the submitted order flags in `NewOrderResponse`. The field is present only
when the session negotiated v19 or later, so the root block is 96 bytes at header v19 and
remains the legacy 94 bytes for earlier stamps; the `fills` (25-byte entries) and `legs`
(34-byte entries) groups follow unchanged. The echoed bits are the same `OrderFlags` as
`NewOrderRequest` field 10 (bits 0–5; bits 6–15 reserved). The decoder rejects reserved
bits. The assembled lifecycle retains the exact uint16 wire flags it sent, including the
`marketLimit` bit always set for market orders. Any v19 echo that differs fails the session
closed before placement, fill, or event publication. A legacy-stamped response remains
accepted under the existing per-message compatibility policy, as with the versioned
`CancelOrderResponse`.

OE v18 adds `OrderRejectReason.CANCEL_ONLY` (31): while global algo cancel-only mode is
active, new algo parent submissions are rejected. `NewOrderReject` and `AmendOrderReject`
accept reasons 0–31 and complete the correlation with that code; 32 and above fail closed.
The client submits no algo parent orders. Its reject layouts and their v14 header floor are
unchanged.

OE v17 changes cancellation delivery: when another session cancels an order, the submitting
session negotiated at v17 or later receives unsolicited `OrdersCanceled` (310), while the
cancelling session receives `CancelOrderResponse` (220). Sessions negotiated below v17
retain the previous routing, where both sessions receive the cancel response. The client
continues to send its own amend/cancel commands to the stored origin session exactly once.
Unsolicited cancellation requires no local cancel correlation; the exact
order/client/instrument identity and cumulative filled quantity must match local state
before cancellation and event publication.

The negotiated `Logon`/`LogonConf.schemaVersion` remains distinct from each server message's
header `version`. The header carries that message's newest last-change version, capped at
the negotiated ceiling. A v19 session can receive `LogonConf` at header v12, `Heartbeat` at
v0, `OrderPlaced` at v8, `CancelOrderResponse` at v16, reject messages at v18, and
`NewOrderResponse` at v19. Keep per-message compatible floors and exact body/group
validation; versions above the pinned/negotiated ceiling fail closed.

The v16 cancel-response extension remains required at header version 16 and later:
`quantity` and `totalFilled` are required `Decimal72` fields at body offsets 56 and 65.
Bodies through v15 are exactly 56 bytes; v16–v19 bodies are exactly 74 bytes. Leaves
quantity is `quantity - totalFilled`. The assembled lifecycle checks the exact exponent,
original quantity, and remaining quantity; mismatches fail closed without synthesizing
fills. Signed 64-bit order/client/instrument/match IDs are never truncated.

The earlier v12-v15 negotiation, session cancel-on-disconnect, and reject-enum additions
remain adopted. The official order-entry SDK is still v14 and cannot establish v17–v19
semantics. Mass quote, FIX, FIX Drop Copy, generated codecs, and runtime XML parsing remain
outside scope. The XML still has no per-order reduce-only field; reject unsupported
submission semantics or route the whole operation through the configured standard backend.

## Exact REST/SBE identity clarification

The formal Deribit support clarification supplied by the requester on 2026-08-27 resolves
`SPEC-01`: Starbase REST `GET /api/v2/private/get_open_orders` returns the exact SBE
`orderId` as a base-10 JSON string in `order_id`. Its representative value,
`"215074398825086978"`, parses directly with `Long.parseLong`. Deribit explicitly identified
the public OpenAPI's UUID wording as a documentation error. That wording and UUID example
remain in the current OpenAPI; they do not supersede the formal clarification.

Missing, malformed, out-of-range, SBE-null-sentinel, duplicate, or ambiguous identities
must keep recovery/readiness closed. Do not use UUID conversion, instrument/side/price/
amount/time tuples, labels, or the unrelated currency-prefixed legacy identifier. Standard
JSON-RPC's numeric `starbase_order_id` and FIX Tag 37 corroborate the SBE identity but do not
provide the Starbase live open-order recovery snapshot. Private support metadata and any
undocumented extra response fields are not retained or inferred into production behavior.

## Current REST contract and unresolved authentication

The dedicated authentication guide still specifies HTTP Basic on every request, while the
current OpenAPI still specifies Bearer on private endpoints and no authentication on public
instruments. Requests use plain HTTP over private connectivity. This source conflict
requires formal clarification or private live evidence before changing authentication.
The implementation and runner preserve the isolated existing behavior; readiness must not
be guessed open.

The five implemented utility calls remain HTTP `GET` with JSON-RPC 2.0 envelopes:

- `/api/v2/public/get_instruments`
- `/api/v2/private/get_open_orders`
- `/api/v2/private/cancel_all`
- `/api/v2/private/lock_portfolio`
- `/api/v2/private/unlock_portfolio`

The current OpenAPI requires `instrument_id` as an int64 on every open order and includes
optional nullable `product_group`. The instruments endpoint combines its existing
currency/kind/expired filters with optional int64 `instrument_id`, product group, and
lifecycle `state` (`open`, `inactive`, `settlement`, `delivered`, `locked`, `halted`).
The implementation retains this metadata and supports all these filters. Exact instrument
identity is checked against local SBE state during order-ID reconciliation; missing,
sentinel, or contradictory values fail closed without any tuple fallback. Optional order
flags remain distinct:
`post_only` and `reject_post_only` are mutually exclusive, and per-order `reduce_only`
is observation-only for the SBE client. Failure responses retain numeric error code,
message, and optional data.

REST remains blocking bootstrap/recovery/administration rather than live ordering or
market data. Open-order snapshots remain immutable, cached, single-flight, and attempted
no faster than the retained one-minute interval. Private REST limits apply per portfolio,
per endpoint, and per REST gateway, on fixed UTC-aligned one-minute windows with independent
A/B counters. `get_open_orders` allows ten requests per minute. `cancel_all`,
`lock_portfolio`, `unlock_portfolio`, and out-of-scope `get_starbase_risk_limits` each allow
one. Public `get_instruments` is exempt. Exceeding a cap returns HTTP 429 with
`Retry-After` in seconds.

The 2026-10-27 audit compared the current OpenAPI `Order` and `Instrument` schemas,
required fields, endpoint parameters, and security schemes with the parser. It found no
payload or authentication delta. Administrative calls remain explicit operator actions
without client-side throttling. A 429 surfaces as `StarbaseRestException` with
`httpStatus() == 429`, the JSON-RPC error when present, and, since `RST-08`,
`retryAfterSeconds()` carrying the documented delay-seconds header. An absent or
uninterpretable header reports `NO_RETRY_AFTER` rather than a guess. A 429 is never
treated as success or retried automatically.

## Market data and remaining validation boundaries

MD layouts remain v1, including `IndexInfo` (12), corrected `InstrumentInfo` (14), and
optional open interest on `InstrumentRef` (15). The unchanged official PCAP remains proof
for unaffected messages but has no templates 12/14/15/33 and no trades. Golden/boundary
fixtures validate those layouts.

The [trades reference](https://docs.deribit.com/starbase/trades.md) (2026-10-28 SHA-256
`FC734C79545619FD5348E48E858B8DE9193E064AE234A5D4937FE9DD461DBF95`) defines `BlockTrade`
(33) as one message per off-book block-trade fill. The fills of a per-instrument block-trade
event are grouped by the header start/end-of-transaction flags. A multi-leg block trade or
Block RFQ produces one transaction per leg. `blockRfqId` is null for standard block trades
and `impliedVolatility` is null for non-options. The implementation publishes each fill on
a dedicated block-trade channel, separate from on-book trades. It skips fills of a
transaction whose start preceded startup or fresh-snapshot recovery, and fails orphan,
nested, or cross-instrument framing closed once aligned. The same reference says `Trade`
(31) is also sent for combo synthetic legs but does not state which instrument ID a leg
carries. The existing summary-instrument check is unchanged pending clarification or live
evidence.

The 2026-09-22 announcement describes optional future eviction of delivered instruments
from snapshot caches. It explicitly ships disabled and depends on two other emission
flags. It does not change the current MD schema or snapshot contents; no new market-data
behavior is inferred from it.

One API key permits one connection per gateway. A/B routing sends each order once and
never retries an ambiguous send on its peer. Session disconnect can cancel orders, so
transport lifetime stays explicit. Reference, sequence, connection, book, and exact
reconciliation gates remain independent. Standard history, positions, balances, and
tickers remain in `deribit-api`.

Consumer adapters, joint builds, private lifecycle validation, and operations/rollback
validation remain pending. Before later codec/protocol edits, download the complete source
set again and compare these hashes. The current public APIs and source adoption do not
establish production readiness.
