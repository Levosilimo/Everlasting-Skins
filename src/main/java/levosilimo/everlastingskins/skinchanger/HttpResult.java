package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;

import java.io.IOException;

/**
 * Typed outcome for HTTP fetch operations. Replaces the nullable-HttpResponse
 * pattern so callers must handle I/O failures explicitly rather than dereferencing null.
 */
public sealed interface HttpResult {

    record Success(HttpResponse response) implements HttpResult {}

    record Failure(IOException cause) implements HttpResult {}

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default HttpResponse get() {
        return switch (this) {
            case Success(var response) -> response;
            case Failure(var cause) -> throw new IllegalStateException("Cannot unwrap failed HTTP result", cause);
        };
    }
}
