# Starbase protocol source review

Reviewed and revalidated 2026-08-28. Machine-verifiable pins and checked-in schemas are recorded under
FND-03 in [schema-manifest.md](schema-manifest.md).

The 2026-08-27 review records a formal Deribit support clarification for the exact endpoint
that had blocked implementation. Starbase REST `GET /api/v2/private/get_open_orders`
returns the SBE `orderId` as a base-10 JSON string in `order_id`; `Long.parseLong` yields
the exact signed `int64` value. Deribit explicitly identified the current OpenAPI's UUID
description as a documentation error. This resolves `SPEC-01`. The restart audit
re-downloaded the required sources and completed `SPEC-02`; the 2026-08-28 audit
re-downloaded them again with unchanged hashes. `ORD-07` and both public assembly tasks
are complete locally.

## Authoritative sources

| Source | URL | Reviewed result |
| --- | --- | --- |
| Starbase overview | https://docs.deribit.com/starbase/overview | Separate low-latency stack; standard APIs remain but omit live Starbase open orders. |
| Infrastructure and compatibility | https://docs.deribit.com/starbase/connectivity-best-practices | Private-only; separate credentials/sessions; SBE OE, SBE L3 MD/retransmit, and REST are independent. |
| Binary API reference | https://docs.deribit.com/starbase/binary-api-reference | Current Markdown SHA-256 `6BC97D8A31BE0DE3372F468AFA957CD10D807C05D8F5647D8BB2BB800B9E3339`; production OE v15, testnet OE v14, MD v1, and order SDK v14. Protocol/framing rules remain as reviewed. |
| Gateway connectivity | https://docs.deribit.com/starbase/gateway-connectivity | Production: hot-hot A/B pairs for BTC, ETH, Tier 2/3; test: one OE pair. Endpoints are configuration only. |
| Multicast channels and subscription | https://docs.deribit.com/starbase/multicast-channels and https://docs.deribit.com/starbase/multicast-subscription-guide | Each product group requires A/B incremental and snapshot feeds. |
| Order-book maintenance, trades, retransmit | https://docs.deribit.com/starbase/order-book-maintenance, https://docs.deribit.com/starbase/trades, and https://docs.deribit.com/starbase/retransmit-gateway | L3 sequencing is authoritative; trade-summary context and retransmit paging/rejects apply. |
| Order/session operations | https://docs.deribit.com/starbase/session-messages, https://docs.deribit.com/starbase/placing-new-order, https://docs.deribit.com/starbase/amending-order, https://docs.deribit.com/starbase/cancelling-order, and https://docs.deribit.com/starbase/mass-cancel | Session, command, response/reject, immediate-fill, and unsolicited-event behavior revalidated. |
| Legacy SBE XML bundle | https://statics.deribit.com/files/deribit-sbe-xmls.zip | Still SHA-256 `4B21E0F317B0C62BFDD3C77E0BC125EFD043A71493406FC45A3A00CE64297B42`, containing the older v12 order XML; it is no longer the current order-entry source. |
| Current production order-entry XML | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api.xml | Schema 2101/version 15/semantic version 1.5, SHA-256 `4BA2A80B473AC233B6DDB971158E7A65353B1E287C7B304F14062AC2E5E9106C`. |
| Current testnet order-entry XML | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api-testnet.xml | Schema 2101/version 14/semantic version 1.5, SHA-256 `3F375CA809C437DB96369DF1751C702FB00E4590156C8F088E7FC2957C9020EA`. |
| Current production market-data XML | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api.xml | Schema 2102/version 1/semantic version 1.0, SHA-256 `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`. |
| Current testnet market-data XML | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api-testnet.xml | Byte-identical to production, SHA-256 `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`. |
| Starbase REST OpenAPI | https://docs.deribit.com/specifications/starbase_rest_openapi.json | OpenAPI 3.0.3/API 2.0, SHA-256 `2E8E1B6FB09D988059BE2A63A4D8E0C6F986EAA343C2AB046D573E439C2E187E`; still incorrectly describes `order_id` as UUID-style. |
| Deribit support clarification | Private support response provided by the requester, dated 2026-08-27 | Specifically confirms Starbase REST `order_id` is the decimal string form of SBE `orderId`, supplies a representative numeric-string response, and says the UUID OpenAPI wording is an error to be corrected. No personal or ticket metadata is retained here. |
| Legacy Starbase SDK | https://statics.deribit.com/files/starbase-deribit-sdk.zip | Still version 0.5.1, SHA-256 `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`; it declares schema version 11. |
| Current order-entry SDK | https://docs.deribit.com/starbase/starbase-deribit-order-sdk-14.0.zip | Schema version 14/semantic version 1.5, SHA-256 `25B23E41E1FB92E290DD6D4E4124A9A69C2E215274C6957C22DE4BCFB8D6392D`; it is behind production v15. |
| Current market-data SDK | https://docs.deribit.com/starbase/starbase-deribit-md-sdk-1.0.zip | Corrected schema version 1/semantic version 1.0, SHA-256 `6E235798278243307F57EE88F2E11FBE7C01B24E6423D08149D6881F48446EC4`. |
| Standard JSON-RPC and Drop Copy identity guidance | https://docs.deribit.com/api-reference/trading/private-get_open_orders and https://docs.deribit.com/starbase/fix-drop-copy-api | Standard records' numeric `starbase_order_id` and FIX Tag 37 equal SBE `orderId`; standard `get_open_orders*` still does not provide the Starbase live-order snapshot. This corroborates but is not needed for the clarified Starbase REST mapping. |
| Starbase changelog | https://docs.deribit.com/changelogs/starbase | Current Markdown SHA-256 `2F0B8C8BC6D2E968954734F28D2E615CCC44EB0E0E5847B0DDEC2320CF23B45F`; it documents production OE v15 and its new MMP freeze reject reasons. |

The direct production XML links in the binary reference are authoritative for current wire
layout. The legacy ZIP and SDK remain audit inputs only and must not be used to infer the
current schema.

### Restart-session REST/reference hashes

The required Markdown/JSON references were re-downloaded byte-for-byte again on
2026-08-28; all hashes remained unchanged:

| Source | SHA-256 |
| --- | --- |
| Starbase REST OpenAPI | `2E8E1B6FB09D988059BE2A63A4D8E0C6F986EAA343C2AB046D573E439C2E187E` |
| REST authentication guide | `A5B5E2CDFAFEA87CCE84301F2FF18D140C5ABEAD2154E9799F274B6DF82E85D2` |
| REST get-open-orders reference | `A31BA9CC2E2DDB00655D0B2E5C1A3594F2C3650A4331897D8326377C2E4752FB` |
| REST list-instruments reference | `1BC3D88EF08AAB9A433F8E746C7AB576C373270269DE4FBFDF42FE17F0EF9A94` |
| REST mass-cancel reference | `B65249EF61DE21FBBD646942C4A388D05B977DDB21DF13532493F2E700261F7C` |
| REST lock-portfolio reference | `C2B71237510E0FD2DC4427F1560B31F40060B0230B555526EA7F5BC087A5B82D` |
| REST unlock-portfolio reference | `2FDE015F400DFCEE538C557FB3498236A951D79B7D8CA19D35FF73507A558A5A` |
| Binary API reference | `6BC97D8A31BE0DE3372F468AFA957CD10D807C05D8F5647D8BB2BB800B9E3339` |
| Starbase changelog | `2F0B8C8BC6D2E968954734F28D2E615CCC44EB0E0E5847B0DDEC2320CF23B45F` |
| Official market-data PCAP | `980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF` |

## Current upstream schema delta and gate decision

The current direct production order-entry XML is schema 2101/version 15/semantic version
1.5, SHA-256 `4BA2A80B473AC233B6DDB971158E7A65353B1E287C7B304F14062AC2E5E9106C`. Relative to
the implemented v11 schema, it adds `Logon.schemaVersion` (field 67, since version 12),
`LogonConf.schemaVersion` (field 6, since version 12), session-wide
`Logon.cancelOnDisconnect` (field 68, since version 13), and the `GATEWAY_NOT_ACTIVE`
reject reason (6, since version 14). Version 15 adds
`MMP_MIN_FREEZE_TIME_NOT_ELAPSED` to `OrderRejectReason` (30) and
`MassQuoteRejectReason` (9). `OrderId` and `ClientOrderId` remain `int64`.

The current market-data XML is schema 2102/version 1/semantic version 1.0, SHA-256
`6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`. Its 2026-08-07
correction adds `IndexInfo` (template 12), changes `InstrumentInfo` (template 14) to carry
only price bands and mark price, and adds optional `openInterest` to `InstrumentRef`
(template 15). The protocol version did not change despite the market-data wire-layout
correction, so version dispatch cannot distinguish the old and corrected layouts.

The current public Starbase REST OpenAPI still says `order_id` is UUID-style and shows a UUID
example. The formal support clarification explicitly corrects that statement for the
Starbase REST open-order endpoint: the actual value is the base-10 string serialization of
SBE `orderId`. The representative value `"215074398825086978"` therefore parses directly
to the exact local key; no FIX component, label/tuple match, or UUID conversion is needed.
This resolves the external identity gate. `SPEC-02` adopted the v15 XML, corrected MD XML,
and unconflicted REST model deltas. The SDK version mismatch and REST authentication-source
conflict remain recorded limitations, not reasons to keep `SPEC-01` open.

## Implemented schema pins

The following are the checked-in references and hardcoded-codec pins after `SPEC-02`.

| Schema | Schema ID | Version | Semantic version | XML SHA-256 |
| --- | ---: | ---: | ---: | --- |
| Order entry (`deribit-sbe-order-api.xml`) | 2101 | 15 | 1.5 | `4BA2A80B473AC233B6DDB971158E7A65353B1E287C7B304F14062AC2E5E9106C` |
| Market data (`deribit-sbe-market-data-api.xml`) | 2102 | 1 | 1.0 | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |

Required MD IDs match XML, including `IndexDefinition` (11), `IndexInfo` (12), and
`BlockTrade` (33); `BlockTrade` remains fail-closed until correct trade behavior needs it. OE v15 includes
`CancelOrderByIdRequest` (125), speed-bump `OrderPlaced` (312), and required
session/order/fill/cancel families. Mass quote and FIX Drop Copy remain out of scope.

## REST rollout scope

RST-01 was revalidated from the current OpenAPI on 2026-08-27. It uses plain `http://`
gateway URLs and still specifies Bearer authentication; public instruments explicitly has
no security. The dedicated REST Order Gateway Authentication guide, SHA-256
`A5B5E2CDFAFEA87CCE84301F2FF18D140C5ABEAD2154E9799F274B6DF82E85D2`, specifies HTTP
Basic on every request. This conflict requires clarification or live evidence before
changing authentication behavior and belongs to `SPEC-02`. All five calls are HTTP `GET`.
All responses use JSON-RPC 2.0
envelopes; failures contain numeric `code`, string `message`, and optional untyped `data`.
The OpenAPI defines exactly:

- `GET /api/v2/public/get_instruments`
- `GET /api/v2/private/get_open_orders`
- `GET /api/v2/private/cancel_all`
- `GET /api/v2/private/lock_portfolio`
- `GET /api/v2/private/unlock_portfolio`

`SPEC-02` did not guess between the conflicting authentication sources: it preserves the
existing OpenAPI-shaped Bearer/private and unauthenticated-instruments behavior and keeps
readiness closed pending live evidence or clarification. REST is private-connectivity
control/recovery, never live ordering. Cache/rate-limit
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
- The implemented OE XML v15 has no reduce-only field despite amend prose. XML governs wire layout: reject
  reduce-only or route the whole operation to the configured standard backend; never drop
  it.
- Code uses pinned XML names/IDs; friendly doc aliases create no templates. FIX/FIX Drop
  Copy remain unimplemented.
- Starbase REST open-order `order_id` parses directly to SBE `orderId` under the formal
  2026-08-27 clarification. Missing/malformed/out-of-range/sentinel or duplicate identities
  fail reconciliation closed; tuple/label matching remains forbidden.

## Restartable implementation subset

Hardcoded/bounds-checked codecs, state/transport, and the locally assembled redundant public
APIs cover required session/order/lifecycle/fill, MD reference/L3/trade/snapshot/retransmit,
L3 reconstruction, TCP lifecycle, exact REST reconciliation, and five REST utilities. See
the [template manifest](schema-manifest.md) and [component status](implementation-status.md).
Downstream adapters and private live validation have not begun. FIX,
generated/runtime-parsed codecs, and mass quote remain out of scope.

Before any later codec edit, re-download all official sources and compare versions/hashes.
The identity gate, `SPEC-02`, `ORD-07`, and local public assembly are complete. Runtime
order readiness still requires exact fresh reconciliation plus every documented session,
sequence, reference, and connection prerequisite.
