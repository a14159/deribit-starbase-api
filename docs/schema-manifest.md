# Starbase schema manifest

Source bundle: `https://statics.deribit.com/files/deribit-sbe-xmls.zip`

Review date: 2026-07-30

Bundle SHA-256: `D36FEDB7AEB2FC5418FBFCFA9FBA80762E865689198B34A64DAEC8DB6D6FB425`

| Schema | ID | Version | Semantic version | Checked-in reference | SHA-256 |
| --- | ---: | ---: | ---: | --- | --- |
| Order entry | 2101 | 11 | 1.3 | `src/main/resources/schema/deribit-sbe-order-api.xml` | `70B1B297A4D8472CA31C76E97613909B136C0CF4782CB858CAC306696C0C5A89` |
| Market data | 2102 | 1 | 1.0 | `src/main/resources/schema/deribit-sbe-market-data-api.xml` | `68F52A5FEF08FA2ECD5F217DEDDA94130AB3B6A24F39090CF088F19D072A73E2` |

The XML files are reviewed reference resources. Production code must not parse them or
generate codecs from them. All production codecs are hardcoded and bounds checked.

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

Common framing and dispatch are covered by `TcpHeaderCodecTest`,
`UdpPacketHeaderCodecTest`, `MarketDataMessageHeaderCodecTest`, and
`TemplateDispatchTest`.

Schema-known market-data template 33 (`BlockTrade`) is not implemented. It is recognized
by the pinned template table but rejected fail-closed by `MarketDataPacketDispatcher`.

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

`OrderEntryMessageDispatcherTest` proves that these 25 layouts are validated before
callbacks. Other IDs known to schema v11 are deliberately unsupported by that dispatcher
and fail closed; recognizing an ID in `OrderEntryTemplateDispatch` does not claim codec or
behavior support.

## Official market-data replay fixture

Source: `https://statics.deribit.com/files/starbase-market-data.pcap`

Review date: 2026-07-30

Checked-in fixture: `src/test/resources/pcap/starbase-market-data.pcap`

Size: 5,344,700 bytes

SHA-256: `980B9D78E46057A5271CB1F99184A82920A5964A0DA959276FACAF4FC8F869CF`

`OfficialPcapReplayTest` decodes all 13,306 UDP packets and 37,745 embedded messages
through `MarketDataPacketDispatcher` and compares packet types, channel counts, template
counts, and the first decoded instrument/snapshot facts with the checked-in
`starbase-market-data.trace`. The capture uses acting version 0 for layouts whose pinned
schema version is 1; production accepts versions 0 through 1 and rejects later versions.
Advertised message lengths may include zero-filled eight-byte alignment padding, which is
validated byte-for-byte rather than ignored.
