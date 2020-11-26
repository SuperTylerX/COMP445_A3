package server;

import java.util.HashMap;
import java.util.Map;

public class Response {

    String status;
    HashMap<String, String> headers = new HashMap<>();
    String body;

    public Response(String status, HashMap<String, String> headers, String body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
        this.headers.put("content-length", String.valueOf(this.body.length()));
        this.headers.put("server", "Concordia/COMP445A2");
    }

    public Response() {
    }

    public String getStatus() {
        return status;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setHeaders(HashMap<String, String> headers) {
        this.headers = headers;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        String statusLine = "HTTP/1.0 " + status + "\r\n";
        String headerLines = "";
        String body = this.getBody();
        String res = "";

        for (Map.Entry<String, String> entry : this.getHeaders().entrySet()) {
            headerLines = headerLines.concat(entry.getKey()).concat(": ").concat(entry.getValue()).concat("\r\n");
        }
        headerLines = headerLines.concat("\r\n");
        res = statusLine.concat(headerLines).concat(body);

        return res;
    }
}
