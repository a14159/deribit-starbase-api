package io.contek.invoker.deribit.starbase.rest;

/** Validated JSON-RPC success envelope with an unparsed result for endpoint-specific decoding. */
public record StarbaseRestResponse(int httpStatus, String resultJson) {}
