package au.com.endurancecoach.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public interface Transport {
    Response request(String method, String url, Map<String,String> headers, byte[] body) throws IOException;
    final class HttpFailure extends IOException {
        public final int status;
        public HttpFailure(int status){super("Request failed (HTTP "+status+"). Check connection or credentials.");this.status=status;}
    }
    final class Response {
        public final int code; public final byte[] body;
        public Response(int code, byte[] body) { this.code=code; this.body=body; }
        public String text() { return new String(body,StandardCharsets.UTF_8); }
        public void requireSuccess() throws IOException {
            if (code<200 || code>=300) throw new HttpFailure(code);
        }
    }
}
