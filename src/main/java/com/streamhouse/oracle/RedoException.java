package com.streamhouse.oracle;

import java.io.IOException;

/** A visible refusal, never an empty successful decode. */
public final class RedoException extends IOException {
    public RedoException(String reason) { super(reason); }
}
