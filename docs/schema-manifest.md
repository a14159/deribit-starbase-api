# Starbase schema manifest

## Current upstream review and adoption

Reviewed 2026-08-27, revalidated unchanged on 2026-08-28, and audited again on 2026-09-03.
The production schema pins remain unchanged. The legacy bundle at
`https://statics.deribit.com/files/deribit-sbe-xmls.zip` remains SHA-256
`4B21E0F317B0C62BFDD3C77E0BC125EFD043A71493406FC45A3A00CE64297B42` and still carries
the older order-entry v12 XML. The current binary reference instead links the direct
production order-entry XML at
`https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api.xml`:
schema 2101/version 15/semantic version 1.5, SHA-256
`4BA2A80B473AC233B6DDB971158E7A65353B1E287C7B304F14062AC2E5E9106C`. Its v12-v15 delta
adds schema negotiation in `Logon`/`LogonConf`, session-wide
`Logon.cancelOnDisconnect` (field 68), `GATEWAY_NOT_ACTIVE` reject reason 6,
`OrderRejectReason.MMP_MIN_FREEZE_TIME_NOT_ELAPSED` value 30, and
`MassQuoteRejectReason.MMP_MIN_FREEZE_TIME_NOT_ELAPSED` value 9. The `OrderId` and
`ClientOrderId` types remain `int64`. As of 2026-09-03, the direct testnet order-entry XML
has advanced from v14 to a byte-identical copy of this production-v15 XML and hash. The
direct market-data XML remains
schema 2102/version 1/semantic version 1.0, SHA-256
`6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`.

The current Binary API Reference also clarifies that an inbound server message's header
`version` is the newest schema version at which that individual message changed, capped at
the negotiated session version; it is not necessarily the negotiated v15 ceiling. The
hardcoded layouts remain pinned correctly, and completed `SPEC-03` now applies
schema-derived per-message lower bounds plus the pinned-v15 upper bound before exact
layout/body/group validation.

Formal Deribit clarification dated 2026-08-27 establishes that the Starbase REST snapshot's
string `order_id` is the base-10 serialization of this same SBE `OrderId`; the public
OpenAPI's UUID description is a documentation error. `SPEC-01` is therefore resolved.
`SPEC-02` adopted the current XMLs and unconflicted REST model deltas test-first on the same
date. `ORD-07`, `ASM-MD`, and `ASM-OE` are complete locally; consumer and private live
validation remain.

## Implemented reference pin

The checked-in references below are the production hardcoded-codec baseline. Direct XML
URLs and the production/testnet/legacy source hashes are recorded in
[protocol-source-review.md](protocol-source-review.md).

| Schema | ID | Version | Semantic version | Checked-in reference | SHA-256 |
| --- | ---: | ---: | ---: | --- | --- |
| Order entry | 2101 | 15 | 1.5 | `src/main/resources/schema/deribit-sbe-order-api.xml` | `4BA2A80B473AC233B6DDB971158E7A65353B1E287C7B304F14062AC2E5E9106C` |
| Market data | 2102 | 1 | 1.0 | `src/main/resources/schema/deribit-sbe-market-data-api.xml` | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |

XML files are reference-only; production codecs are hardcoded/bounds-checked, never
generated or runtime-parsed.

## Implemented template map

| Schema | Template ID | Message | Hardcoded codec | Golden/bounds test |
| --- | ---: | --- | --- | --- |
| Market data | 10 | InstrumentDefinition | `InstrumentDefinitionDecoder` | `ReferenceDataDecodersTest` |
| Market data | 11 | IndexDefinition | `IndexDefinitionDecoder` | `ReferenceDataDecodersTest` |
| Market data | 12 | IndexInfo | `IndexInfoDecoder` | `ReferenceDataDecodersTest` |
| Market data | 14 | InstrumentInfo | `InstrumentInfoDecoder` | `ReferenceDataDecodersTest` |
| Market data | 15 | InstrumentRef | `InstrumentRefDecoder` | `ReferenceDataDecodersTest` |
| Market data | 16 | InstrumentStatusUpdate | `InstrumentStatusUpdateDecoder` | `ReferenceDataDecodersTest` |
| Market data | 20 | BidPut | `BidPutDecoder` | `BookPutDecodersTest` |
| Market data | 21 | AskPut | `AskPutDecoder` | `BookPutDecodersTest` |
| Market data | 22 | BidQtyReduced | `BidQtyReducedDecoder` | `BookMutationDecodersTest` |
| Market data | 23 | AskQtyReduced | `AskQtyReducedDecoder` | `BookMutationDecodersTest` |
| Market data | 24 | BidDelete | `BidDeleteDecoder` | `BookMutationDecodersTest` |
| Market data | 25 | AskDelete | `AskDeleteDecoder` | `BookMutationDecodersTest` |
| Market data | 30 | TradeSummary | `TradeSummaryDecoder` | `TradeDecodersTest` |
| Market data | 31 | Trade | `TradeDecoder` | `TradeDecodersTest` |
| Market data | 100 | SnapshotHeader | `SnapshotHeaderDecoder` | `SnapshotDecodersTest` |
| Market data | 101 | SnapshotTrailer | `SnapshotTrailerDecoder` | `SnapshotDecodersTest` |
| Market data | 119 | EndOfCycle | `EndOfCycleDecoder` | `SnapshotDecodersTest` |
| Market data | 200 | RetransmitRequest | `RetransmitRequestEncoder` | `RetransmitCodecsTest` |
| Market data | 202 | RetransmitReject | `RetransmitRejectDecoder` | `RetransmitCodecsTest` |

Common framing/dispatch tests: `TcpHeaderCodecTest`, `UdpPacketHeaderCodecTest`,
`MarketDataMessageHeaderCodecTest`, and `TemplateDispatchTest`. Schema-known template 33
(`BlockTrade`) has no codec and is rejected fail-closed by `MarketDataPacketDispatcher`.

### Order-entry templates

All listed layouts are unchanged from the originally implemented subset except `Logon`
(68-byte body) and `LogonConfirmation` (6-byte body). Encoders stamp production v15, and
the assembled lifecycle requires `LogonConf.schemaVersion` to echo negotiated v15. The
dispatcher accepts each template's schema-derived compatible version floor through the
pinned v15 ceiling, while retaining exact template lengths and bounds checks. In
particular, current `LogonConf`, `Heartbeat`, and `OrderPlaced` layouts accept their
documented header stamps 12, 0, and 8. Testnet now uses the same v15 XML as production.

| Template ID | Message | Hardcoded codec(s) | Golden/bounds test |
| ---: | --- | --- | --- |
| 1 | Logon | `LogonEncoder`, `LogonDecoder` | `SessionCodecsTest` |
| 2 | LogonConfirmation | `LogonConfirmationCodec` | `SessionCodecsTest` |
| 4 | Logout | `LogoutCodec` | `SessionCodecsTest` |
| 5 | LoggedOut | `LoggedOutCodec` | `SessionCodecsTest` |
| 10 | Heartbeat | `HeartbeatCodec` | `SessionCodecsTest` |
| 11 | TestRequest | `TestRequestCodec` | `SessionCodecsTest` |
| 20 | ResendRequest | `ResendRequestCodec` | `SessionCodecsTest` |
| 21 | GapFill | `GapFillEncoder`, `GapFillDecoder` | `SessionCodecsTest` |
| 30 | SessionReject | `SessionRejectEncoder`, `SessionRejectDecoder` | `SessionCodecsTest` |
| 100 | NewOrderRequest | `NewOrderRequestEncoder`, `NewOrderRequestDecoder` | `NewOrderCodecsTest` |
| 110 | AmendOrderRequest | `AmendOrderRequestEncoder`, `AmendOrderRequestDecoder` | `AmendOrderCodecsTest` |
| 120 | CancelOrderRequest | `CancelOrderRequestEncoder`, `CancelOrderRequestDecoder` | `CancelOrderCodecsTest` |
| 125 | CancelOrderByIdRequest | `CancelOrderByIdRequestEncoder`, `CancelOrderByIdRequestDecoder` | `CancelOrderCodecsTest` |
| 140 | MassCancelRequest | `MassCancelRequestEncoder`, `MassCancelRequestDecoder` | `CancelOrderCodecsTest` |
| 200 | NewOrderResponse | `NewOrderResponseDecoder` | `NewOrderCodecsTest` |
| 202 | NewOrderReject | `NewOrderRejectDecoder` | `NewOrderCodecsTest` |
| 210 | AmendOrderResponse | `AmendOrderResponseDecoder` | `AmendOrderCodecsTest` |
| 212 | AmendOrderReject | `AmendOrderRejectDecoder` | `AmendOrderCodecsTest` |
| 220 | CancelOrderResponse | `CancelOrderResponseDecoder` | `CancelOrderCodecsTest` |
| 222 | CancelOrderReject | `CancelOrderRejectDecoder` | `CancelOrderCodecsTest` |
| 240 | MassCancelResponse | `MassCancelResponseDecoder` | `CancelOrderCodecsTest` |
| 242 | MassCancelReject | `MassCancelRejectDecoder` | `CancelOrderCodecsTest` |
| 300 | OrderFilled | `OrderFilledDecoder` | `OrderLifecycleCodecsTest` |
| 310 | OrdersCanceled | `OrdersCanceledDecoder` | `OrderLifecycleCodecsTest` |
| 312 | OrderPlaced | `OrderPlacedDecoder` | `OrderLifecycleCodecsTest` |

`OrderEntryMessageDispatcherTest` proves pre-callback validation of all 25 layouts. Other
schema-v11 IDs fail closed; recognition by `OrderEntryTemplateDispatch` does not imply
codec/behavior support.

## Official market-data replay fixture

Reviewed 2026-07-30: `https://statics.deribit.com/files/starbase-market-data.pcap`;
checked in as `src/test/resources/pcap/starbase-market-data.pcap`; 5,344,700 bytes; SHA-256
`980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF`.

`OfficialPcapReplayTest` sends all 13,306 packets/37,745 messages through
`MarketDataPacketDispatcher` and checks packet/template/channel counts plus first
instrument/snapshot facts against `starbase-market-data.trace`. The capture acts at
version 0 where the pin is 1; production accepts 0–1 and rejects later versions.
Zero-filled eight-byte padding included in advertised lengths is validated byte-for-byte.
The unchanged capture contains no template 12, 14, or 15 messages, so it does not validate
the corrected MD-v1 reference-data layouts; current golden/boundary coverage is in
`ReferenceDataDecodersTest`.
