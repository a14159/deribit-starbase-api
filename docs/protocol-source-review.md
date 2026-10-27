# Starbase protocol source review

Last full audit 2026-10-27; a second complete download matched every byte. A partial
revalidation on 2026-10-28 found the OE/MD XMLs, OpenAPI, binary reference, changelog,
trades, and book-maintenance pages unchanged. Checked-in schemas and the template map are in
[schema-manifest.md](schema-manifest.md); mutable state is in
[implementation-status.md](implementation-status.md).

## Current source state

- Production and testnet OE XMLs are byte-identical: schema 2101/version 19/semantic 1.5.
- Production and testnet MD XMLs are byte-identical: schema 2102/version 1/semantic 1.0.
- The latest dated changelog entry is 2026-10-13 (OE v18/v19, both additive).
- The order-entry SDK is still v14 and cannot establish v17–v19 semantics. The legacy XML
  bundle (OE v12), legacy SDK (0.5.1, schema v11), and the historical order-entry XMLs
  listed in the Binary API Reference are audit inputs only, never sources for current
  layouts.
- XML governs wire layout when prose differs.

## Authoritative downloads and SHA-256

Hashes cover the direct XML/JSON/archive/PCAP bytes and the indicated Markdown
representations, not rendered HTML.

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

Additional behavior references checked in the same audit:

| Source | Download URL | SHA-256 |
| --- | --- | --- |
| Cancelling an Order | https://docs.deribit.com/starbase/cancelling-order.md | `6E823986021D5A6BAE1F82226E8034E85B5EA8C55D25877899EF5652DD3A5D42` |
| Unsolicited Events | https://docs.deribit.com/starbase/unsolicited-events.md | `8ED2C7AE75469961C37CCC7D5A15FB861DB7B34F3D1742C24736D6DA4BA99062` |
| Placing a New Order | https://docs.deribit.com/starbase/placing-new-order.md | `A6B913FA089DB3753EB9A4F4115A3166711914676DFFC18D443C72D663DED639` |
| API Rate Limits | https://docs.deribit.com/starbase/api-rate-limits.md | `FD3C92E78C631F26B47B9E410626E3F6659E8A9CCE254635A368450F32424ACE` |
| Trades (recorded 2026-10-28) | https://docs.deribit.com/starbase/trades.md | `FC734C79545619FD5348E48E858B8DE9193E064AE234A5D4937FE9DD461DBF95` |

Related concept references: [overview](https://docs.deribit.com/starbase/overview),
[connectivity guidance](https://docs.deribit.com/starbase/connectivity-best-practices),
[gateway connectivity](https://docs.deribit.com/starbase/gateway-connectivity),
[multicast channels](https://docs.deribit.com/starbase/multicast-channels),
[subscription guide](https://docs.deribit.com/starbase/multicast-subscription-guide),
[book maintenance](https://docs.deribit.com/starbase/order-book-maintenance),
[retransmit](https://docs.deribit.com/starbase/retransmit-gateway),
[session messages](https://docs.deribit.com/starbase/session-messages),
[amends](https://docs.deribit.com/starbase/amending-order), and
[mass cancel](https://docs.deribit.com/starbase/mass-cancel).

## Order-entry semantics

The client negotiates v19, and the assembled lifecycle requires `LogonConf` to echo v19.
The negotiated `Logon`/`LogonConf.schemaVersion` is the session ceiling. Each server
message's header `version` is instead the newest version at which that message changed,
capped at the ceiling. A v19 session can therefore receive `LogonConf` at header v12,
`Heartbeat` at v0, `OrderPlaced` at v8, `CancelOrderResponse` at v16, rejects at v18, and
`NewOrderResponse` at v19. Keep per-message floors and exact body/group validation;
versions above the ceiling fail closed. Per-version layout changes are listed in the
[schema manifest](schema-manifest.md#order-entry-schema-changes).

- **v19 flag echo.** `NewOrderResponse` echoes the submitted `OrderFlags` (bits 0–5 known,
  6–15 reserved and rejected). The lifecycle retains the exact uint16 wire flags it sent,
  including the `marketLimit` bit always set for market orders. A differing v19 echo fails
  the session closed before placement, fill, or event publication. A legacy-stamped
  response is still accepted under the per-message compatibility policy.
- **v18 `CANCEL_ONLY`.** While global algo cancel-only mode is active, new algo parent
  submissions are rejected with reason 31. Reasons 0–31 complete the correlation; 32 and
  above fail closed. The client submits no algo parent orders.
- **v17 cancellation routing.** When another session cancels an order, a submitting session
  negotiated at v17 or later receives unsolicited `OrdersCanceled` (310); the cancelling
  session receives `CancelOrderResponse` (220). Earlier sessions keep the old routing. The
  client sends its own amend/cancel exactly once to the stored origin session. Unsolicited
  cancellation needs no local correlation but must match exact order/client/instrument
  identity and cumulative filled quantity.
- **v16 cancel quantities.** Leaves quantity is `quantity - totalFilled`. The lifecycle
  checks exponent, original quantity, and remaining quantity; mismatches fail closed
  without synthesizing fills.

The XML has no per-order reduce-only field: reject the semantics or route the whole
operation through the configured standard backend. Mass quote, FIX, FIX Drop Copy,
generated codecs, and runtime XML parsing remain out of scope.

## Exact REST/SBE identity clarification

The formal Deribit support clarification supplied by the requester on 2026-08-27 resolves
`SPEC-01`. Starbase REST `GET /api/v2/private/get_open_orders` returns the exact SBE
`orderId` as a base-10 JSON string in `order_id`; it is not a UUID. The representative value
`"215074398825086978"` parses directly with `Long.parseLong`. Deribit identified the
OpenAPI's UUID wording, still present, as a documentation error. The currency-prefixed
legacy `order_id` is separate and has no conversion to the SBE ID.

Missing, malformed, out-of-range, SBE-null-sentinel, duplicate, or ambiguous identities keep
recovery and readiness closed. Never use UUID conversion, instrument/side/price/amount/time
tuples, or labels. Standard JSON-RPC `starbase_order_id` and FIX Tag 37 corroborate the SBE
identity but do not provide the recovery snapshot. Undocumented extra fields in the support
example are not inferred into production behavior.

## REST contract and unresolved authentication

The authentication guide specifies HTTP Basic on every request; the OpenAPI specifies
Bearer on private endpoints and no authentication on public instruments. Requests use plain
HTTP over private connectivity. The existing OpenAPI-shaped behavior stays isolated until
formal clarification or private live evidence; readiness is never guessed open.

The five implemented calls are HTTP `GET` with JSON-RPC 2.0 envelopes:
`/api/v2/public/get_instruments`, `/api/v2/private/get_open_orders`,
`/api/v2/private/cancel_all`, `/api/v2/private/lock_portfolio`, and
`/api/v2/private/unlock_portfolio`. The read-only `get_starbase_risk_limits` endpoint is out
of scope.

- Every open order requires int64 `instrument_id` and may carry nullable `product_group`.
  Instrument identity is checked against local SBE state during reconciliation; missing,
  sentinel, or contradictory values fail closed without tuple fallback.
- `post_only` and `reject_post_only` are mutually exclusive; `reduce_only` is
  observation-only for the SBE client. Explicit JSON `null` flags are rejected.
- Instruments support currency/kind/expired plus optional int64 `instrument_id`, product
  group, and lifecycle `state` (`open`, `inactive`, `settlement`, `delivered`, `locked`,
  `halted`).
- Private limits apply per portfolio, endpoint, and REST gateway on fixed UTC-aligned
  one-minute windows with independent A/B counters: `get_open_orders` ten per minute;
  `cancel_all`, `lock_portfolio`, `unlock_portfolio`, and the risk-limit endpoint one each;
  public `get_instruments` exempt. Exceeding a cap returns HTTP 429 with `Retry-After` in
  seconds.
- The client keeps a one-minute minimum open-order recovery interval and does not throttle
  explicit administrative calls. A 429 surfaces as `StarbaseRestException` with
  `httpStatus() == 429`, any JSON-RPC error, and `retryAfterSeconds()`
  (`NO_RETRY_AFTER` when absent or uninterpretable). It is never treated as success or
  retried automatically.

## Market-data notes

MD v1 includes `IndexInfo` (12), the 32-byte `InstrumentInfo` (14), and optional open
interest on `InstrumentRef` (15). The official PCAP has no templates 12/14/15/33 and no
trades, so golden/boundary fixtures cover those layouts.

The trades reference defines `BlockTrade` (33) as one message per off-book block-trade
fill. A per-instrument block-trade event's fills are grouped by the header
start/end-of-transaction flags; a multi-leg block trade or Block RFQ produces one
transaction per leg. `blockRfqId` is null for standard block trades and
`impliedVolatility` is null for non-options. The same reference says `Trade` (31) is also
sent for combo synthetic legs without stating the leg's instrument ID; the existing
summary-instrument check is unchanged pending clarification or live evidence.

Optional future eviction of delivered instruments from snapshot caches (announced
2026-09-22) ships disabled and changes neither the MD schema nor snapshot contents; no
behavior is inferred from it.
