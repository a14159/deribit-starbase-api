# Starbase protocol source review

Reviewed and revalidated 2026-10-04. Checked-in schemas and the implemented template map
are recorded in [schema-manifest.md](schema-manifest.md); mutable work and verification
evidence belong in [implementation-status.md](implementation-status.md).

The restart audit downloaded all 18 required official inputs byte-for-byte. Seven hashes
match the 2026-09-16 audit and eleven changed. Production and testnet OE are byte-identical
schema 2101/version 17/semantic version 1.5. Relative to the checked-in v16 source, the XML
only changes the schema version and adds a comment describing unsolicited cross-session
cancellation. No message field, enum, offset, block length, or repeating group changed.
Production/testnet MD remain byte-identical schema 2102/version 1/semantic version 1.0.
The legacy XML bundle, all three SDKs, and the official PCAP remain unchanged.

The latest dated changelog entry is 2026-09-24. The 2026-09-22 entry documents OE v17 and
current REST instrument filters/open-order metadata. The 2026-09-24 entry adds a read-only
risk-limit endpoint and specifies a ten-per-minute, per-portfolio/per-gateway open-order
limit. The implementation retains its conservative one-minute minimum recovery interval.
The new risk-limit endpoint is outside the existing five-call REST contract.

## Authoritative downloads and SHA-256

Hashes below cover the direct XML/JSON/archive/PCAP bytes and the indicated Markdown
representations, not rendered HTML. XML governs wire layout when prose differs. The direct
production XML links in the Binary API Reference remain authoritative; legacy bundles and
SDKs must not be used to infer current layouts.

| Source and reviewed result | Download URL | SHA-256 |
| --- | --- | --- |
| Production order-entry XML: schema 2101/v17/semantic 1.5 | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api.xml | `6A721ED6161ACFEAD838ACE8D6F4F9A44347C0B293BEB99B9E9ED3D3D57B3B91` |
| Testnet order-entry XML: byte-identical to production | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api-testnet.xml | `6A721ED6161ACFEAD838ACE8D6F4F9A44347C0B293BEB99B9E9ED3D3D57B3B91` |
| Production market-data XML: schema 2102/v1/semantic 1.0 | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api.xml | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |
| Testnet market-data XML: byte-identical to production | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api-testnet.xml | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |
| Legacy XML bundle: older OE v12, audit input only | https://statics.deribit.com/files/deribit-sbe-xmls.zip | `4B21E0F317B0C62BFDD3C77E0BC125EFD043A71493406FC45A3A00CE64297B42` |
| Starbase REST OpenAPI: OpenAPI 3.0.3/API 2.0 | https://docs.deribit.com/specifications/starbase_rest_openapi.json | `6C44CD19E63D61C3D112F161C7017CC3E4606F89D656E1D059DD5B65615A5B5A` |
| REST Order Gateway Authentication | https://docs.deribit.com/starbase/rest-authentication.md | `4854C66E25E7B33EE8900C8601F724B19B02CA00FD55C0CF21087BEF77D3626C` |
| REST Get Open Orders | https://docs.deribit.com/api-reference/trading/get-open-orders.md | `79585FFEF7F68D08226008476D543D89AB414AEB392EE28A5A9EB64D8DD1A6DF` |
| REST List Instruments | https://docs.deribit.com/api-reference/market-data/list-instruments.md | `652EEC41C90F794742769C794C1FB2CFDB9D7E17A745F21B716E8260FC31E56C` |
| REST Mass Cancel | https://docs.deribit.com/api-reference/portfolio-management/mass-cancel.md | `6534C1462C74CC919D3B2CF6BB36B817CC2F088EA254154BB0D0A90E2BB75E84` |
| REST Lock Portfolio | https://docs.deribit.com/api-reference/portfolio-management/lock-portfolio.md | `BC88740CAC1959DD94ED982A717F7C5A684BF3E04D59DC13289923CB070F0527` |
| REST Unlock Portfolio | https://docs.deribit.com/api-reference/portfolio-management/unlock-portfolio.md | `A7046EFF45D2EF570AE38632344DBDA4708F6821F5782AD6585EC6398016C90C` |
| Binary API Reference | https://docs.deribit.com/starbase/binary-api-reference.md | `E8CE9C610CF73D1164DCB17E5D00F79541AE040881A0AAE2EFF267640D0DF078` |
| Starbase changelog | https://docs.deribit.com/changelogs/starbase.md | `DB93501D6C8B168E9B834586A92A29424F72A47B215DF176C19F86A2E9BC021E` |
| Order-entry SDK: v14/semantic 1.5, audit input only | https://docs.deribit.com/starbase/starbase-deribit-order-sdk-14.0.zip | `25B23E41E1FB92E290DD6D4E4124A9A69C2E215274C6957C22DE4BCFB8D6392D` |
| Market-data SDK: v1/semantic 1.0 | https://docs.deribit.com/starbase/starbase-deribit-md-sdk-1.0.zip | `6E235798278243307F57EE88F2E11FBE7C01B24E6423D08149D6881F48446EC4` |
| Legacy SDK: 0.5.1, schema v11, audit input only | https://statics.deribit.com/files/starbase-deribit-sdk.zip | `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C` |
| Official market-data PCAP: 5,344,700 bytes | https://statics.deribit.com/files/starbase-market-data.pcap | `980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF` |

The v17 behavior was additionally checked against these current official references:

| Source | Download URL | SHA-256 |
| --- | --- | --- |
| Cancelling an Order | https://docs.deribit.com/starbase/cancelling-order.md | `1EDC382B19652D56CA8E096124386A38FD2622B3EEB295F6AE3A4CA2A452200E` |
| Unsolicited Events | https://docs.deribit.com/starbase/unsolicited-events.md | `3C5410EE1BA3AE0C275051C2F075663B980E3518FE69326B70F744421228F44E` |

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
| Order entry | 2101 | 17 | 1.5 | `6A721ED6161ACFEAD838ACE8D6F4F9A44347C0B293BEB99B9E9ED3D3D57B3B91` |
| Market data | 2102 | 1 | 1.0 | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |

OE v17 changes cancellation delivery: when another session cancels an order, the submitting
session negotiated at v17 or later receives unsolicited `OrdersCanceled` (310), while the
cancelling session receives `CancelOrderResponse` (220). Sessions negotiated below v17
retain the previous routing, where both sessions receive the cancel response. The public
client negotiates v17 and continues to send its own amend/cancel commands to the stored
origin session exactly once. Unsolicited cancellation requires no local cancel correlation;
the exact order/client/instrument identity and cumulative filled quantity must match local
state before cancellation and event publication.

The negotiated `Logon`/`LogonConf.schemaVersion` remains distinct from each server message's
header `version`. The header carries that message's newest last-change version, capped at
the negotiated ceiling; a v17 session can receive `LogonConf` at header v12, `Heartbeat` at
v0, `OrderPlaced` at v8, and `CancelOrderResponse` at v16. Keep per-message compatible
floors and exact body/group validation; versions above the pinned/negotiated ceiling fail
closed. No message layout was changed by the v17 adoption.

The v16 cancel-response extension remains required at header version 16 and later:
`quantity` and `totalFilled` are required `Decimal72` fields at body offsets 56 and 65.
Bodies through v15 are exactly 56 bytes; v16/v17 bodies are exactly 74 bytes. Leaves
quantity is `quantity - totalFilled`. The assembled lifecycle checks the exact exponent,
original quantity, and remaining quantity; mismatches fail closed without synthesizing
fills. Signed 64-bit order/client/instrument/match IDs are never truncated.

The earlier v12-v15 negotiation, session cancel-on-disconnect, and reject-enum additions
remain adopted. The official order-entry SDK is still v14 and cannot establish v17
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
no faster than the retained one-minute interval. Current upstream allows ten requests per
minute per portfolio on each gateway and returns HTTP 429 with retry information; the A/B
counters are independent.

## Market data and remaining validation boundaries

MD layouts remain v1, including `IndexInfo` (12), corrected `InstrumentInfo` (14), and
optional open interest on `InstrumentRef` (15). The unchanged official PCAP remains proof
for unaffected messages but has no templates 12/14/15. Golden/boundary fixtures validate
those reference layouts. `BlockTrade` (33) remains fail-closed.

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
