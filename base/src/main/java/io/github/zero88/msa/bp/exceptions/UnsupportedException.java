package io.github.zero88.msa.bp.exceptions;

public final class UnsupportedException extends BlueprintException {

    public UnsupportedException(String message, Throwable e) {
        super(ErrorCode.UNSUPPORTED, message, e);
    }

    public UnsupportedException(String message) { this(message, null); }

    public UnsupportedException(Throwable e)    { this(null, e); }

}