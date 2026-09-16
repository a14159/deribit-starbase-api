# Starbase protocol source review

Reviewed and revalidated 2026-09-16. Machine-verifiable pins and checked-in schemas are
recorded under FND-03 in [schema-manifest.md](schema-manifest.md).

The 2026-09-16 restart audit downloaded all 18 required official artifacts and references.
Seven hashes remain unchanged and eleven changed. Production and testnet order entry are
now byte-identical schema 2101/version 16/semantic version 1.5. Relative to the prior v15
pin, the only XML wire change appends required `quantity` and `totalFilled` `Decimal72`
fields to `CancelOrderResponse` (220), at body offsets 56 and 65, with
`sinceVersion="16"`. Both market-data XMLs remain byte-identical schema 2102/version
1/semantic version 1.0. The latest dated changelog entry is 2026-09-16; its FIX Drop Copy
change is out of scope, while the 2026-09-10 entry is the authority for the v16 order-entry
delta. `SPEC-04` adopts that delta without inferring any additional protocol behavior.

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
| Binary API reference | https://docs.deribit.com/starbase/binary-api-reference | Current Markdown SHA-256 `3D1C446CE3426C14DF89ED02FB133DDF7EC4AE220F91C74846B37C135A5EBB0C`; production and testnet OE v16, MD v1, and order SDK v14. A server message's header `version` is the newest schema version at which that message changed, capped by the negotiated session version; for example, a v16 session can receive `LogonConf` with header version 12. |
| Gateway connectivity | https://docs.deribit.com/starbase/gateway-connectivity | Production: hot-hot A/B pairs for BTC, ETH, Tier 2/3; test: one OE pair. Endpoints are configuration only. |
| Multicast channels and subscription | https://docs.deribit.com/starbase/multicast-channels and https://docs.deribit.com/starbase/multicast-subscription-guide | Each product group requires A/B incremental and snapshot feeds. |
| Order-book maintenance, trades, retransmit | https://docs.deribit.com/starbase/order-book-maintenance, https://docs.deribit.com/starbase/trades, and https://docs.deribit.com/starbase/retransmit-gateway | L3 sequencing is authoritative; trade-summary context and retransmit paging/rejects apply. |
| Order/session operations | https://docs.deribit.com/starbase/session-messages, https://docs.deribit.com/starbase/placing-new-order, https://docs.deribit.com/starbase/amending-order, https://docs.deribit.com/starbase/cancelling-order, and https://docs.deribit.com/starbase/mass-cancel | Session, command, response/reject, immediate-fill, and unsolicited-event behavior revalidated. |
| Legacy SBE XML bundle | https://statics.deribit.com/files/deribit-sbe-xmls.zip | Still SHA-256 `4B21E0F317B0C62BFDD3C77E0BC125EFD043A71493406FC45A3A00CE64297B42`, containing the older v12 order XML; it is no longer the current order-entry source. |
| Current production order-entry XML | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api.xml | Schema 2101/version 16/semantic version 1.5, SHA-256 `64EBC71CCAC3A01203977718CD524C9476D311ADFF01049312CA0778E57CF559`. |
| Current testnet order-entry XML | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api-testnet.xml | Schema 2101/version 16/semantic version 1.5, SHA-256 `64EBC71CCAC3A01203977718CD524C9476D311ADFF01049312CA0778E57CF559`; byte-identical to production as of 2026-09-16. |
| Current production market-data XML | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api.xml | Schema 2102/version 1/semantic version 1.0, SHA-256 `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`. |
| Current testnet market-data XML | https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api-testnet.xml | Byte-identical to production, SHA-256 `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`. |
| Starbase REST OpenAPI | https://docs.deribit.com/specifications/starbase_rest_openapi.json | OpenAPI 3.0.3/API 2.0, SHA-256 `D53EC179867A5A9C7A982A8133642B86D8A6C9AA59ED18443410E9822C37671C`; still incorrectly describes `order_id` as UUID-style and still specifies Bearer authentication for private endpoints. |
| Deribit support clarification | Private support response provided by the requester, dated 2026-08-27 | Specifically confirms Starbase REST `order_id` is the decimal string form of SBE `orderId`, supplies a representative numeric-string response, and says the UUID OpenAPI wording is an error to be corrected. No personal or ticket metadata is retained here. |
| Legacy Starbase SDK | https://statics.deribit.com/files/starbase-deribit-sdk.zip | Still version 0.5.1, SHA-256 `57BB9D0861943F88D7B5A8FCE2D4DF7F19EE66AB7C8E8DB98C39A1C1C96BFC8C`; it declares schema version 11. |
| Current order-entry SDK | https://docs.deribit.com/starbase/starbase-deribit-order-sdk-14.0.zip | Schema version 14/semantic version 1.5, SHA-256 `25B23E41E1FB92E290DD6D4E4124A9A69C2E215274C6957C22DE4BCFB8D6392D`; it is behind production v16. |
| Current market-data SDK | https://docs.deribit.com/starbase/starbase-deribit-md-sdk-1.0.zip | Corrected schema version 1/semantic version 1.0, SHA-256 `6E235798278243307F57EE88F2E11FBE7C01B24E6423D08149D6881F48446EC4`. |
| Standard JSON-RPC and Drop Copy identity guidance | https://docs.deribit.com/api-reference/trading/private-get_open_orders and https://docs.deribit.com/starbase/fix-drop-copy-api | Standard records' numeric `starbase_order_id` and FIX Tag 37 equal SBE `orderId`; standard `get_open_orders*` still does not provide the Starbase live-order snapshot. This corroborates but is not needed for the clarified Starbase REST mapping. |
| Starbase changelog | https://docs.deribit.com/changelogs/starbase | Current Markdown SHA-256 `D62065D090DF6E8D694CADF6CFE545981EF7C1EDFAE26A81C58E8BF8D8421D7C`; the 2026-09-10 entry documents OE v16 on production and testnet, and the 2026-09-16 FIX Drop Copy change is out of scope. |

The direct production XML links in the binary reference are authoritative for current wire
layout. The legacy ZIP and SDK remain audit inputs only and must not be used to infer the
current schema.

### 2026-09-16 restart-session REST/reference hashes

The required Markdown/JSON references were re-downloaded byte-for-byte on 2026-09-16.
All current hashes are recorded below; the authenticated REST pages still conflict between
the dedicated Basic-authentication guide and the Bearer-authenticated OpenAPI.

| Source | SHA-256 |
| --- | --- |
| Starbase REST OpenAPI | `D53EC179867A5A9C7A982A8133642B86D8A6C9AA59ED18443410E9822C37671C` |
| REST authentication guide | `A87531947508B0576DD2975EE0F9FC07572496D64830B486AAFDBA21D1DD90B6` |
| REST get-open-orders reference | `95818D795D1E16896C1C21144C92F23A0ADA83BEA64C3BBE5DBEA252768C093B` |
| REST list-instruments reference | `BF5606AE5DDD03E1C06CA3EDC7328163B39F5B6810A405A08530FC7BB1B34C67` |
| REST mass-cancel reference | `CC589A68401EC081B97366638D32A2574F9B776A46907C044DC766DBB2EF3651` |
| REST lock-portfolio reference | `D04159091A734AD5CCAC4D57DC4B136ED6578BF3676C9D8CA8CC75B84F966916` |
| REST unlock-portfolio reference | `06C6BE9A33AB39B003627ED0A737F78E3E63B3BE0F7DDFC7226F0E035F89A3E5` |
| Binary API reference | `3D1C446CE3426C14DF89ED02FB133DDF7EC4AE220F91C74846B37C135A5EBB0C` |
| Starbase changelog | `D62065D090DF6E8D694CADF6CFE545981EF7C1EDFAE26A81C58E8BF8D8421D7C` |
| Official market-data PCAP | `980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF` |

## Current upstream schema delta and gate decision

The 2026-09-16 audit found one production XML wire-layout delta. `CancelOrderResponse`
(220) grows from a 56-byte body through v15 to a 74-byte body at v16 by appending
`quantity` and `totalFilled`. Both values are required `Decimal72` fields; cancelled/leaves
quantity is exactly `quantity - totalFilled`. `SPEC-04` pins v16, keeps the legacy layout
for earlier per-message stamps, validates the new layout before dispatch, reconciles both
values with local order state before applying cancellation, and moves the non-trading live
runner's negotiated ceiling and phase labels to v16. No market-data codec changed.

The Binary API Reference continues to distinguish the v16 session ceiling
negotiated in `Logon`/`LogonConf` from each inbound message's header `version`. Server
messages carry the newest schema version at which that particular message changed, capped
by the session ceiling. The former global order-entry dispatcher accepted only header
versions 11 through 15, even though current-layout messages can legitimately carry earlier
stamps; `OrderPlaced`, for example, last changed at version 8. The former live runner also
expected `LogonConf` and `Heartbeat` header versions to equal the negotiated session
version, contrary to the reference's explicit negotiated/header-version distinction.
Completed `SPEC-03` replaces those assumptions with bounds-checked per-message version
handling. `SPEC-04` updates the negotiated ceiling and testnet probe to v16. XML continues
to govern every layout, and future versions above the negotiated/pinned ceiling still fail
closed.

The current direct production order-entry XML is schema 2101/version 16/semantic version
1.5, SHA-256 `64EBC71CCAC3A01203977718CD524C9476D311ADFF01049312CA0778E57CF559`. Relative to
the implemented v11 schema, it adds `Logon.schemaVersion` (field 67, since version 12),
`LogonConf.schemaVersion` (field 6, since version 12), session-wide
`Logon.cancelOnDisconnect` (field 68, since version 13), and the `GATEWAY_NOT_ACTIVE`
reject reason (6, since version 14). Version 15 adds
`MMP_MIN_FREEZE_TIME_NOT_ELAPSED` to `OrderRejectReason` (30) and
`MassQuoteRejectReason` (9). Version 16 appends the two `CancelOrderResponse` quantities
described above. `OrderId` and `ClientOrderId` remain `int64`.

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
This resolves the external identity gate. `SPEC-02` adopted the then-current v15 XML,
corrected MD XML, and unconflicted REST model deltas. The SDK version mismatch and REST
authentication-source conflict remain recorded limitations, not reasons to keep `SPEC-01`
open.

## Implemented schema pins

The following are the checked-in references and hardcoded-codec pins after `SPEC-04`.

| Schema | Schema ID | Version | Semantic version | XML SHA-256 |
| --- | ---: | ---: | ---: | --- |
| Order entry (`deribit-sbe-order-api.xml`) | 2101 | 16 | 1.5 | `64EBC71CCAC3A01203977718CD524C9476D311ADFF01049312CA0778E57CF559` |
| Market data (`deribit-sbe-market-data-api.xml`) | 2102 | 1 | 1.0 | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |

The current testnet order-entry XML is now byte-identical to the production pin. The
official order-entry SDK remains version 14 and therefore remains an audit input rather
than the authority for v16 behavior.

Required MD IDs match XML, including `IndexDefinition` (11), `IndexInfo` (12), and
`BlockTrade` (33); `BlockTrade` remains fail-closed until correct trade behavior needs it.
OE v16 includes `CancelOrderByIdRequest` (125), speed-bump `OrderPlaced` (312), and
required session/order/fill/cancel families. Mass quote and FIX Drop Copy remain out of
scope.

## REST rollout scope

RST-01 was revalidated from the current OpenAPI on 2026-09-16. It uses plain `http://`
gateway URLs and still specifies Bearer authentication; public instruments explicitly has
no security. The dedicated REST Order Gateway Authentication guide, SHA-256
`A87531947508B0576DD2975EE0F9FC07572496D64830B486AAFDBA21D1DD90B6`, specifies HTTP
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
control/recovery, never live ordering. The current `get_open_orders` page scopes rate
limiting per portfolio and returns HTTP 429 when exceeded; the implementation retains its
conservative one-minute minimum attempt interval.

## Revalidated safety and compatibility decisions

- Cancel-on-disconnect is mandatory/session-scoped: disconnect makes the session
  unavailable, reconnect does not restore orders, and origin session survives cross-session
  amend/cancel.
- One API key permits one connection/gateway. Hot-hot A/B have independent limits and may
  both carry flow, but each order is sent once.
- Standard WebSocket/JSON-RPC and Starbase credentials/events differ. History, positions,
  balances, and tickers stay in `deribit-api`; live Starbase open orders use local SBE state
  plus exact Starbase REST reconciliation.
- The implemented OE XML v16 has no reduce-only field despite amend prose. XML governs
  wire layout: reject reduce-only or route the whole operation to the configured standard
  backend; never drop it.
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
The identity gate, `SPEC-02`, `ORD-07`, local public assembly, `SPEC-03`, and `SPEC-04` are
complete. `VAL-EC2` is the exact next action. Runtime order readiness still requires exact fresh
reconciliation plus every documented session, sequence, reference, and connection
prerequisite.
