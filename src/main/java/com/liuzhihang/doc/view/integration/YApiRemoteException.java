package com.liuzhihang.doc.view.integration;

/** Identifies whether a YApi operation failed in transport or returned an unusable response. */
public final class YApiRemoteException extends Exception {

    public enum Kind {
        /** The remote operation failed. */
        REQUEST_FAILED,
        /** The remote response did not match its contract. */
        RESPONSE_INVALID
    }

    private final Kind kind;

    private YApiRemoteException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static YApiRemoteException requestFailed(Exception cause) {
        String summary = cause.getMessage();
        if (summary == null || summary.isBlank()) {
            summary = cause.getClass().getSimpleName();
        }
        return new YApiRemoteException(Kind.REQUEST_FAILED, summary);
    }

    public static YApiRemoteException responseInvalid(String summary) {
        return new YApiRemoteException(Kind.RESPONSE_INVALID, summary);
    }

    public Kind getKind() {
        return kind;
    }
}
