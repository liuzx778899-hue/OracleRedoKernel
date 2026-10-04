package com.streamhouse.engines.oracle;

import com.streamhouse.spi.CapDecoder;
import com.streamhouse.spi.ConnectorDescriptor;
import com.streamhouse.spi.EngineConnector;
import com.streamhouse.spi.WindowScanner;
import java.io.IOException;

/** SPI discovery is available; public window consumption awaits its Oracle contract. */
public final class OracleConnector implements EngineConnector {
    @Override public ConnectorDescriptor descriptor() {
        return new ConnectorDescriptor("ORACLE","0.1.0","ORACAP01",
                "Oracle input-window, trusted dictionary and publication gates not accepted");
    }
    @Override public WindowScanner windowScanner() {
        return root->{throw new IOException("ORACLE_CAPTURE_WINDOW_CONTRACT_NOT_ACCEPTED");};
    }
    @Override public CapDecoder decoder() {
        return (path,context)->CapDecoder.DecodeResult.failed("ORACLE_DICTIONARY_PUBLICATION_GATES_NOT_ACCEPTED");
    }
}
