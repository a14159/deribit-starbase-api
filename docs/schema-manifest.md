# Starbase schema manifest

## Current upstream review (not adopted)

Reviewed 2026-08-08: `https://statics.deribit.com/files/deribit-sbe-xmls.zip`, SHA-256
`4B21E0F317B0C62BFDD3C77E0BC125EFD043A71493406FC45A3A00CE64297B42`.

The bundle now contains order-entry schema 2101/version 12/semantic version 1.4, XML
SHA-256 `ABB62943716230852B0684A6D3CE9BA1C42E235EFD424E7DED5C3864A0EC19BA`, and
market-data schema 2102/version 1/semantic version 1.0, XML SHA-256
`6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE`. The order-entry
delta is schema negotiation in `Logon`/`LogonConf`; the market-data correction adds
`IndexInfo` (12), changes `InstrumentInfo` (14), and adds `openInterest` to
`InstrumentRef` (15). It introduces no REST/SBE identity bridge, so it is not adopted
while `SPEC-01`/`ORD-07` remain paused.

## Implemented reference pin

The checked-in references below remain the hardcoded-codec baseline until a separately
selected, test-first source-update task is permitted by the identity gate.

Reviewed 2026-08-06: `https://statics.deribit.com/files/deribit-sbe-xmls.zip`, SHA-256
`D36FEDB7AEB2FC5418FBFCFA9FBA80762E865689198B34A64DAEC8DB6D6FB425`.

| Schema | ID | Version | Semantic version | Checked-in reference | SHA-256 |
| --- | ---: | ---: | ---: | --- | --- |
| Order entry | 2101 | 11 | 1.3 | `src/main/resources/schema/deribit-sbe-order-api.xml` | `70B1B297A4D8472CA31C76E97613909B136C0CF4782CB858CAC306696C0C5A89` |
| Market data | 2102 | 1 | 1.0 | `src/main/resources/schema/deribit-sbe-market-data-api.xml` | `68F52A5FEF08FA2ECD5F217DEDDA94130AB3B6A24F39090CF088F19D072A73E2` |

XML files are reference-only; production codecs are hardcoded/bounds-checked, never
generated or runtime-parsed.

## Implemented template map

| Schema | Template ID | Message | Hardcoded codec | Golden/bounds test |
| --- | ---: | --- | --- | --- |
| Market data | 10 | InstrumentDefinition | `InstrumentDefinitionDecoder` | `ReferenceDataDecodersTest` |
| Market data | 11 | IndexDefinition | `IndexDefinitionDecoder` | `ReferenceDataDecodersTest` |
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
