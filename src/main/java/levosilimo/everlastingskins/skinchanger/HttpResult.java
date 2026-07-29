package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;

import java.io.IOException;

public abstract class HttpResult {

    private HttpResult() {}

    public abstract boolean isSuccess();

    public abstract HttpResponse get();

    public static final class Success extends HttpResult {
        private final HttpResponse response;

        public Success(HttpResponse response) {
            this.response = response;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public HttpResponse get() {
            return response;
        }

        public HttpResponse response() {
            return response;
        }
    }

    public static final class Failure extends HttpResult {
        private final IOException cause;

        public Failure(IOException cause) {
            this.cause = cause;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public HttpResponse get() {
            throw new IllegalStateException("Cannot unwrap failed HTTP result", cause);
        }

        public IOException cause() {
            return cause;
        }
    }
}
