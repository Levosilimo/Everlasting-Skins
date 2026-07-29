package levosilimo.everlastingskins.util;


import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public interface HttpClient {
    HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                         String userAgent, HttpMethod method,
                         Map<String, String> headers, int timeout) throws IOException;

    enum HttpMethod {
        GET,
        POST,
        PUT,
        DELETE
    }

    enum HttpType {
        JSON("application/json");
        private final String contentType;

        HttpType(String s) {
            contentType = s;
        }

        public String getContentType() {
            return contentType;
        }
    }

    final class RequestBody {
        private final String body;
        private final HttpType type;

        public RequestBody(String body, HttpType type) {
            this.body = body;
            this.type = type;
        }

        public String body() {
            return body;
        }

        public HttpType type() {
            return type;
        }
    }
}
