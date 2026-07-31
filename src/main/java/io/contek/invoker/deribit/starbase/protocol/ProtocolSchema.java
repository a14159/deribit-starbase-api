package io.contek.invoker.deribit.starbase.protocol;

/** Immutable metadata for a reviewed, pinned Starbase wire schema. */
public record ProtocolSchema(
    int schemaId, int version, String semanticVersion, String resourcePath, String sha256) {}
