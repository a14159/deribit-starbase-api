# Starbase schema manifest

Checked-in pins and implemented templates as of the 2026-10-27 audit. Source URLs, hashes,
and protocol semantics are in [protocol-source-review.md](protocol-source-review.md).

## Checked-in reference pins

| Schema | ID | Version | Semantic version | Checked-in reference | SHA-256 |
| --- | ---: | ---: | ---: | --- | --- |
| Order entry | 2101 | 19 | 1.5 | `src/main/resources/schema/deribit-sbe-order-api.xml` | `5A1BF2FDE696424CA35F468815D879DF096AC0B14E92A93BEA1BD1C433D58283` |
| Market data | 2102 | 1 | 1.0 | `src/main/resources/schema/deribit-sbe-market-data-api.xml` | `6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE` |

Both are byte-identical to the current production and testnet downloads. XML files are
reference-only; production codecs are hardcoded and bounds-checked, never generated or
runtime-parsed. `.gitattributes` forces LF on the pinned XMLs so their hashes survive
Windows checkouts.

## Order-entry schema changes

Changes relative to the legacy inputs (XML bundle v12, SDK v11). `OrderId` and
`ClientOrderId` remain `int64`.

| Version | Change |
| --- | --- |
| v12–v15 | `Logon`/`LogonConf` schema negotiation; session-wide `Logon.cancelOnDisconnect` (field 68); `SessionReject` `GATEWAY_NOT_ACTIVE` (6); `OrderRejectReason.MMP_MIN_FREEZE_TIME_NOT_ELAPSED` (30); `MassQuoteRejectReason` value 9 (mass quote not implemented) |
| v16 | `CancelOrderResponse` (220) appends required `quantity` and `totalFilled` `Decimal72` at body offsets 56 and 65 (body 56 → 74 bytes) |
| v17 | No layout change; cross-session cancellation sends unsolicited `OrdersCanceled` to the submitting session |
| v18 | `OrderRejectReason.CANCEL_ONLY` (31) |
| v19 | `NewOrderResponse` (200) `flags` (`OrderFlags`, uint16, id 96) at root offset 94 (root block 94 → 96 bytes), before the unchanged groups |

## Market-data templates

| Template ID | Message | Hardcoded codec | Golden/bounds test |
| ---: | --- | --- | --- |
| 10 | InstrumentDefinition | `InstrumentDefinitionDecoder` | `ReferenceDataDecodersTest` |
| 11 | IndexDefinition | `IndexDefinitionDecoder` | `ReferenceDataDecodersTest` |
| 12 | IndexInfo | `IndexInfoDecoder` | `ReferenceDataDecodersTest` |
| 14 | InstrumentInfo | `InstrumentInfoDecoder` | `ReferenceDataDecodersTest` |
| 15 | InstrumentRef | `InstrumentRefDecoder` | `ReferenceDataDecodersTest` |
| 16 | InstrumentStatusUpdate | `InstrumentStatusUpdateDecoder` | `ReferenceDataDecodersTest` |
| 20 | BidPut | `BidPutDecoder` | `BookPutDecodersTest` |
| 21 | AskPut | `AskPutDecoder` | `BookPutDecodersTest` |
| 22 | BidQtyReduced | `BidQtyReducedDecoder` | `BookMutationDecodersTest` |
| 23 | AskQtyReduced | `AskQtyReducedDecoder` | `BookMutationDecodersTest` |
| 24 | BidDelete | `BidDeleteDecoder` | `BookMutationDecodersTest` |
| 25 | AskDelete | `AskDeleteDecoder` | `BookMutationDecodersTest` |
| 30 | TradeSummary | `TradeSummaryDecoder` | `TradeDecodersTest` |
| 31 | Trade | `TradeDecoder` | `TradeDecodersTest` |
| 33 | BlockTrade | `BlockTradeDecoder` | `TradeDecodersTest` |
| 100 | SnapshotHeader | `SnapshotHeaderDecoder` | `SnapshotDecodersTest` |
| 101 | SnapshotTrailer | `SnapshotTrailerDecoder` | `SnapshotDecodersTest` |
| 119 | EndOfCycle | `EndOfCycleDecoder` | `SnapshotDecodersTest` |
| 200 | RetransmitRequest | `RetransmitRequestEncoder` | `RetransmitCodecsTest` |
| 202 | RetransmitReject | `RetransmitRejectDecoder` | `RetransmitCodecsTest` |

Common framing/dispatch tests: `TcpHeaderCodecTest`, `UdpPacketHeaderCodecTest`,
`MarketDataMessageHeaderCodecTest`, and `TemplateDispatchTest`. Every template in the pinned
MD schema has a codec; a datagram carrying a template outside it is counted as an unknown
template and fails the assembly closed until restart.

`BlockTrade` (33) is a fixed 78-byte root block packed in field order:

| Offset | Field | Type |
| ---: | --- | --- |
| 0 | `matchId` | int64 |
| 8 | `instrumentId` | int64 |
| 16 | `blockTradeId` | int64 |
| 24 | `blockRfqId` | optional int64 (null `Long.MIN_VALUE`) |
| 32 | `fillQtyMantissa` | int64 |
| 40 | `fillPrice` | `Price9` |
| 48 | `markPrice` | `Price9` |
| 56 | `indexPrice` | `Price9` |
| 64 | `impliedVolatility` | optional double (null NaN) |
| 72 | `takerFlags` | `TradeFlags` uint32 |
| 76 | `numberOfLegs` | uint16 |

The message is accepted at its unpadded 94 bytes or aligned 96 bytes with zero padding.

## Order-entry templates

Encoders stamp v19, and the lifecycle requires `LogonConf.schemaVersion` to echo v19. The
dispatcher accepts each template's schema-derived version floor through the v19 ceiling
with exact lengths and bounds checks; for example `LogonConf`, `Heartbeat`, and
`OrderPlaced` accept header stamps 12, 0, and 8. Notable layouts:

- `Logon` has a 68-byte body and `LogonConfirmation` a 6-byte body.
- `CancelOrderResponse` is exactly 56 bytes through v15 and 74 bytes at v16–v19 with
  non-null `quantity` and `totalFilled`.
- `NewOrderResponse` has a 94-byte root block through header v18 and 96 bytes at v19, with
  echoed `flags` (bits 0–5 known, reserved bits fail closed) before the `fills` (25-byte
  entries) and `legs` (34-byte entries) groups.
- `NewOrderReject`/`AmendOrderReject` accept `OrderRejectReason` 0–31.
- `OrdersCanceled` (310) keeps header v5.

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
schema template IDs fail closed; recognition by `OrderEntryTemplateDispatch` does not imply
codec or behavior support.

## Official market-data replay fixture

`https://statics.deribit.com/files/starbase-market-data.pcap`, checked in as
`src/test/resources/pcap/starbase-market-data.pcap` (5,344,700 bytes, SHA-256
`980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF`).

- `OfficialPcapReplayTest` sends all 13,306 packets/37,745 messages through
  `MarketDataPacketDispatcher` and checks packet/template/channel counts plus first
  instrument/snapshot facts against `starbase-market-data.trace`.
- `PcapToChannelsReplayTest` on channel 44853 yields 2,115 reference and 861 coherent book
  callbacks, hash `7987323303025136854`, zero trades, and a ready final book.
- The capture uses MD header version 0 where the pin is 1; production accepts 0–1 and
  rejects later versions. Zero-filled eight-byte padding is validated byte-for-byte.
- The capture has no templates 12, 14, 15, or 33 and no trades; `ReferenceDataDecodersTest`
  and `TradeDecodersTest` cover those layouts.
