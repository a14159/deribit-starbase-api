# Starbase protocol source review

Reviewed 2026-08-08. Machine-verifiable pins and checked-in schemas are recorded under
FND-03 in [schema-manifest.md](schema-manifest.md).

The 2026-08-08 restart audit re-downloaded the XML bundle, REST OpenAPI, SDK,
binary-reference Markdown, and Starbase changelog Markdown. The XML bundle,
binary-reference Markdown, and changelog changed; the OpenAPI and SDK remain byte-for-byte
pinned. The updated XML still has only numeric SBE order identities, while the unchanged
REST `Order` model still has only a UUID-style `order_id` and nullable `label`. No official
collision-free bridge was added, so `SPEC-01`/`ORD-07` remain paused.

## Authoritative sources

| Source | URL | Reviewed result |
| --- | --- | --- |
| Starbase overview | https://docs.deribit.com/starbase/overview | Separate low-latency stack; standard APIs remain but omit live Starbase open orders. |
| Infrastructure and compatibility | https://docs.deribit.com/starbase/connectivity-best-practices | Private-only; separate credentials/sessions; SBE OE, SBE L3 MD/retransmit, and REST are independent. |
| Binary API reference | https://docs.deribit.com/starbase/binary-api-reference | Protocol ID `0xDB`, little-endian fields, 32-byte TCP header, 24-byte UDP packet header, 16-byte market-data message header, and 8-byte TCP frame alignment. Current Markdown SHA-256 `25B0A38C344DB86F215DB766A502250E3BE5EAA13BE81E5D661F7CFC447FD43C`. |
| Gateway connectivity | https://docs.deribit.com/starbase/gateway-connectivity | Production: hot-hot A/B pairs for BTC, ETH, Tier 2/3; test: one OE pair. Endpoints are configuration only. |
| Multicast channels and subscription | https://docs.deribit.com/starbase/multicast-channels and https://docs.deribit.com/starbase/multicast-subscription-guide | Each product group requires A/B incremental and snapshot feeds. |
| Order-book maintenance, trades, retransmit | https://docs.deribit.com/starbase/order-book-maintenance, https://docs.deribit.com/starbase/trades, and https://docs.deribit.com/starbase/retransmit-gateway | L3 sequencing is authoritative; trade-summary context and retransmit paging/rejects apply. |
| Order/session operations | https://docs.deribit.com/starbase/session-messages, https://docs.deribit.com/starbase/placing-new-order, https://docs.deribit.com/starbase/amending-order, https://docs.deribit.com/starbase/cancelling-order, and https://docs.deribit.com/starbase/mass-cancel | Session, command, response/reject, immediate-fill, and unsolicited-event behavior revalidated. |
| Official SBE XML bundle | https://statics.deribit.com/files/deribit-sbe-xmls.zip | Download SHA-256 `4B21E0F317B0C62BFDD3C77E0BC125EFD043A71493406FC45A3A00CE64297B42`. Current XML details below. |
| Starbase REST OpenAPI | https://docs.deribit.com/specifications/starbase_rest_openapi.json | OpenAPI 3.0.3, API version 2.0, SHA-256 `F2F2DD44CC4ED63ACC8C4E30545B2829514BF20566EE0C6AEFBA16D0F6F267DB`. |
| Starbase SDK | https://statics.deribit.com/files/starbase-deribit-sdk.zip | Still version 0.5.1, SHA-256 `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`; its generated order messages still declare schema version 11, rather than the current XML's 12. |
| Starbase changelog | https://docs.deribit.com/changelogs/starbase | Current Markdown SHA-256 `42EECE609A40388B030F2144985764BE1CB89EBE4B82417BC3EC06DC5B09CC83`; it documents the 2026-08-07 market-data XML correction. |

The brief's changelog URL has no independently indexable page, so pins use downloadable
XMLs cross-checked against the binary reference; no version is inferred from it.

## Current upstream schema delta and pause decision

The current order-entry XML is schema 2101/version 12/semantic version 1.4, SHA-256
`ABB62943716230852B0684A6D3CE9BA1C42E235EFD424E7DED5C3864A0EC19BA`. Relative to the
implemented v11 schema, it adds a `uint16 schemaVersion` field to `Logon` (template 1,
field ID 67, since version 12) and a `uint16 schemaVersion` echo to `LogonConf` (template
2, field ID 6). It does not add a REST-compatible `orderId` or `clientOrderId`.

The current market-data XML is schema 2102/version 1/semantic version 1.0, SHA-256
`6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`. Its 2026-08-07
correction adds `IndexInfo` (template 12), changes `InstrumentInfo` (template 14) to carry
only price bands and mark price, and adds optional `openInterest` to `InstrumentRef`
(template 15). The protocol version did not change despite the market-data wire-layout
correction, so version dispatch cannot distinguish the old and corrected layouts.

The OpenAPI and SDK downloads remain exactly pinned to the prior audit, and their REST/SBE
identity models are unchanged. This source delta is therefore not an unblock: no codec,
checked-in schema, or protocol behavior may change under the current pause.

## Implemented schema pins

The following are the checked-in references and hardcoded-codec pins. They remain the
implementation baseline until the identity gate is resolved and an explicitly selected
test-first update task adopts the upstream changes.

| Schema | Schema ID | Version | Semantic version | XML SHA-256 |
| --- | ---: | ---: | ---: | --- |
| Order entry (`deribit-sbe-order-api.xml`) | 2101 | 11 | 1.3 | `70B1B297A4D8472CA31C76E97613909B136C0CF4782CB858CAC306696C0C5A89` |
| Market data (`deribit-sbe-market-data-api.xml`) | 2102 | 1 | 1.0 | `68F52A5FEF08FA2ECD5F217DEDDA94130AB3B6A24F39090CF088F19D072A73E2` |

Required MD IDs match XML, which also defines `IndexDefinition` (11) and `BlockTrade`
(33); decode them when correct reference/trade behavior needs them. OE v11 includes
`CancelOrderByIdRequest` (125), speed-bump `OrderPlaced` (312), and required
session/order/fill/cancel families. Mass quote and FIX Drop Copy remain out of scope.

## REST rollout scope

RST-01 was revalidated from the byte-for-byte unchanged OpenAPI on 2026-08-08. The OpenAPI
and generated endpoint reference still specify Bearer authentication with the Starbase REST
key; public instruments explicitly has no security. The separately rendered REST Order
Gateway Authentication guide currently says HTTP Basic instead. This official-source
conflict requires clarification before changing authentication behavior; this paused audit
makes no such change. All five calls are HTTP `GET`. All responses use JSON-RPC 2.0
envelopes; failures contain numeric `code`, string `message`, and optional untyped `data`.
The OpenAPI defines exactly:

- `GET /api/v2/public/get_instruments`
- `GET /api/v2/private/get_open_orders`
- `GET /api/v2/private/cancel_all`
- `GET /api/v2/private/lock_portfolio`
- `GET /api/v2/private/unlock_portfolio`

REST is private-connectivity control/recovery, never live ordering. Cache/rate-limit
`get_open_orders`: documentation limits it to one request/minute/IP.

## Revalidated safety and compatibility decisions

- Cancel-on-disconnect is mandatory/session-scoped: disconnect makes the session
  unavailable, reconnect does not restore orders, and origin session survives cross-session
  amend/cancel.
- One API key permits one connection/gateway. Hot-hot A/B have independent limits and may
  both carry flow, but each order is sent once.
- Standard WebSocket/JSON-RPC and Starbase credentials/events differ. History, positions,
  balances, and tickers stay in `deribit-api`; live Starbase open orders use local SBE state
  plus exact Starbase REST reconciliation.
- OE XML v11 has no reduce-only field despite amend prose. XML governs wire layout: reject
  reduce-only or route the whole operation to the configured standard backend; never drop
  it.
- Code uses pinned XML names/IDs; friendly doc aliases create no templates. FIX/FIX Drop
  Copy remain unimplemented.
- SDK 0.5.1, re-downloaded on 2026-08-08 (SHA-256
  `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`) handles numeric
  SBE `client_order_id`/`order_id` but has no REST client/model or UUID/string bridge. With
  REST's UUID-style `order_id` and nullable `label`, exact reconnect reconciliation remains
  externally blocked; field-tuple matching is forbidden.

## Paused implementation subset

As of 2026-07-31, hardcoded/bounds-checked codecs and independently tested state/transport
cover required session/order/lifecycle/fill, MD reference/L3/trade/snapshot/retransmit, L3
reconstruction, TCP lifecycle, and five REST utilities. See the [template
manifest](schema-manifest.md) and [component status](implementation-status.md). Public APIs
do not yet compose all A/B, recovery, TCP, state, and routing pieces; downstream adapters
have not begun. FIX, generated/runtime-parsed codecs, and mass quote remain out of scope.

Before any codec edit, re-download all official sources and compare versions/hashes. Do
not resume past the pause without an exact official REST/SBE identity bridge; unchanged
specifications keep readiness closed.
