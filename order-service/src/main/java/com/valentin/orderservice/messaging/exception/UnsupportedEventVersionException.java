package com.valentin.orderservice.messaging.exception;


public class UnsupportedEventVersionException extends EventContractException {
    public UnsupportedEventVersionException(
            int actualVersion,
            int supportedVersion
    ) {
        super(
                "Unsupported event version: actual=%s, supported=%s"
                        .formatted(
                                actualVersion,
                                supportedVersion
                        )
        );
    }
}
