# Starbase protocol source review

Reviewed on 2026-07-30. This review completes the source and rollout revalidation
prerequisite; the machine-verifiable manifest and checked-in schema resources belong to
FND-03.

## Authoritative sources

| Source | URL | Reviewed result |
| --- | --- | --- |
| Starbase overview | https://docs.deribit.com/starbase/overview | Starbase is a separate low-latency stack; standard APIs remain available but do not expose live Starbase open orders. |
| Infrastructure and compatibility | https://docs.deribit.com/starbase/connectivity-best-practices | Private connectivity only; separate credentials/sessions; SBE order entry, SBE L3 market data/retransmit, and REST are independent interfaces. |
| Binary API reference | https://docs.deribit.com/starbase/binary-api-reference | Protocol ID `0xDB`, little-endian fields, 32-byte TCP header, 24-byte UDP packet header, 16-byte market-data message header, and 8-byte TCP frame alignment. |
| Gateway connectivity | https://docs.deribit.com/starbase/gateway-connectivity | Production uses hot-hot A/B pairs sharded across BTC, ETH, Tier 2, and Tier 3; test uses one A/B order-entry pair. Endpoints remain configuration, never application defaults. |
| Multicast channels and subscription | https://docs.deribit.com/starbase/multicast-channels and https://docs.deribit.com/starbase/multicast-subscription-guide | Both A/B incremental and snapshot feeds are required for each selected product group. |
| Order-book maintenance, trades, retransmit | https://docs.deribit.com/starbase/order-book-maintenance, https://docs.deribit.com/starbase/trades, and https://docs.deribit.com/starbase/retransmit-gateway | Starbase L3 snapshot/incremental sequencing is authoritative; trade-summary context and retransmit paging/reject behavior are required. |
| Order/session operations | https://docs.deribit.com/starbase/session-messages, https://docs.deribit.com/starbase/placing-new-order, https://docs.deribit.com/starbase/amending-order, https://docs.deribit.com/starbase/cancelling-order, and https://docs.deribit.com/starbase/mass-cancel | Session, order command, response, reject, immediate-fill, and unsolicited-event behavior revalidated. |
| Official SBE XML bundle | https://statics.deribit.com/files/deribit-sbe-xmls.zip | Download SHA-256 `D36FEDB7AEB2FC5418FBFCFA9FBA80762E865689198B34A64DAEC8DB6D6FB425`. XML details below. |
| Starbase REST OpenAPI | https://docs.deribit.com/specifications/starbase_rest_openapi.json | OpenAPI 3.0.3, API version 2.0, SHA-256 `F2F2DD44CC4ED63ACC8C4E30545B2829514BF20566EE0C6AEFBA16D0F6F267DB`. |

The changelog URL from the original brief currently does not expose an independently
indexable page. Version pinning therefore uses the current official downloadable XMLs,
with the current binary reference as a cross-check, rather than inferring a version from
the changelog.

## Pinned schema versions

| Schema | Schema ID | Version | Semantic version | XML SHA-256 |
| --- | ---: | ---: | ---: | --- |
| Order entry (`deribit-sbe-order-api.xml`) | 2101 | 11 | 1.3 | `70B1B297A4D8472CA31C76E97613909B136C0CF4782CB858CAC306696C0C5A89` |
| Market data (`deribit-sbe-market-data-api.xml`) | 2102 | 1 | 1.0 | `68F52A5FEF08FA2ECD5F217DEDDA94130AB3B6A24F39090CF088F19D072A73E2` |

The required market-data IDs in the implementation brief match the XML. The XML also
contains `IndexDefinition` (11) and `BlockTrade` (33); these must be decoded if needed for
correct reference/trade behavior. Order-entry v11 includes cancel by exchange order ID
(`CancelOrderByIdRequest`, 125), speed-bump lifecycle (`OrderPlaced`, 312), and the
session/order/fill/cancel families required by the tracker. Mass quote and FIX Drop Copy
remain outside the initial implementation scope.

## REST rollout scope

Revalidated directly from the official OpenAPI on 2026-07-31 for RST-01. Private
operations use HTTP Bearer authentication with the Starbase REST API key; the public
instruments operation explicitly has an empty security requirement. All five current
operations are HTTP `GET`, including cancel-all, lock, and unlock. Successful and failed
responses use JSON-RPC 2.0 envelopes, with failures carrying numeric `code`, string
`message`, and optional untyped `data`. The general Starbase overview still describes the
REST utilities as forthcoming, so the downloadable OpenAPI is the implementation contract
and live production readiness remains subject to private-connectivity validation.

The current OpenAPI contains exactly the five utility operations required by the
implementation brief:

- `GET /api/v2/public/get_instruments`
- `GET /api/v2/private/get_open_orders`
- `GET /api/v2/private/cancel_all`
- `GET /api/v2/private/lock_portfolio`
- `GET /api/v2/private/unlock_portfolio`

REST is a private-connectivity control/recovery plane, not the live order path.
`get_open_orders` must be cached and rate-limited; current documentation warns that it is
limited to one request per minute per IP.

## Revalidated safety and compatibility decisions

- Cancel on disconnect is always enabled and session-scoped. Disconnect immediately makes
  the session unavailable; reconnect does not restore orders. Originating-session identity
  must survive cross-session amend/cancel handling.
- One API key may have only one connection to each gateway instance. A and B are hot-hot,
  have independent limits, and may both carry flow, but a single order must be sent once.
- Standard WebSocket/JSON-RPC and Starbase use different credentials and event semantics.
  Standard history, positions, balances, and ticker functionality remain in
  `deribit-api`; live Starbase open orders come from local SBE state plus Starbase REST
  reconciliation.
- The official v11 order-entry XML contains no reduce-only field or flag. The current
  amend-order documentation describes a reduce-only flag, which conflicts with that XML.
  The pinned XML controls wire layout: reduce-only is unsupported for this implementation
  and must be rejected explicitly or the entire operation routed to the configured
  standard backend. It must never be silently dropped.
- Documentation uses some friendly message names that differ from XML names. Code constants
  and layouts will use the pinned XML names/IDs; documentation aliases do not create extra
  wire templates.
- FIX and FIX Drop Copy are deliberately not implemented despite current production
  documentation describing them.
- The official Starbase SDK 0.5.1 was rechecked for ORD-07 (download SHA-256
  `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`). It contains
  numeric SBE `client_order_id`/`order_id` handling but no REST client, open-order model,
  or UUID/string-to-SBE identity bridge. Combined with the REST 2.0 schema's UUID-style
  `order_id` and nullable `label`, exact reconnect reconciliation remains externally
  blocked; field-tuple matching is not permitted.

## Paused implementation subset

As of 2026-07-31 the repository contains hardcoded, bounds-checked codecs and independently
tested state/transport components for the required session/order messages, unsolicited
lifecycle/fills, market-data reference/L3/trade/snapshot/retransmit messages, L3
reconstruction, TCP lifecycle, and all five REST utilities. The exact template inventory
is in [schema-manifest.md](schema-manifest.md), and component/integration status is in
[implementation-status.md](implementation-status.md).

The A/B, retransmit, snapshot, TCP session, order-state, and routing primitives are not yet
fully composed behind the public APIs. Downstream backend selection and consumer adapters
have not started. FIX, generated codecs, runtime XML parsing, and mass quoting remain out
of scope.

On resumption, re-download every official source and compare versions and hashes before
editing a codec. In particular, do not proceed past the current pause unless the official
REST/SBE order-identity mismatch is resolved exactly; an unchanged specification is a
reason to keep readiness closed, not to approximate reconciliation.
