package com.valentin.inventory.grpc;

import io.grpc.Metadata;
import io.grpc.protobuf.ProtoUtils;

public final class InventoryGrpcMetadata {

    public static final Metadata.Key<MissingProducts> MISSING_PRODUCTS = Metadata.Key.of(
            "missing-products-bin",
            ProtoUtils.metadataMarshaller(MissingProducts.getDefaultInstance())
    );

    private InventoryGrpcMetadata() {
    }
}
