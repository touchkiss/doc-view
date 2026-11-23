package com.liuzhihang.doc.view.service.gpt;

/** Model failures with a diagnostic safe to show in the IDE. */
public final class ModelRequestException extends RuntimeException {
    public ModelRequestException(String message) {
        super(message);
    }
}
