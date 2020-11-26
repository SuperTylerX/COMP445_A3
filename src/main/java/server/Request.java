package server;

import java.util.Arrays;
import java.util.HashMap;

public class Request {

    String method;
    HashMap<String, String> headers;
    String body;
    String path;
    String rawRequestString;

    public Request(String method, HashMap<String, String> headers, String body, String path) {
        this.method = method;
        this.headers = headers;
        this.body = body;
        this.path = path;
    }

    public Request(String rawRequestString) {
        this.rawRequestString = rawRequestString;
        this.headers = new HashMap<>();
        String[] requestArr = rawRequestString.split("\r\n");
        this.method = requestArr[0].split(" ")[0];
        this.path = requestArr[0].split(" ")[1];
        for (int i = 1; i < requestArr.length; i++) {
            if (requestArr[i].equals("")) {
                String[] bodyArr = Arrays.copyOfRange(requestArr, ++i, requestArr.length);
                this.body = String.join("", bodyArr);
                break;
            } else {
                String[] header = requestArr[i].split(": ");
                this.headers.put(header[0], header[1]);
            }
        }
    }

    public String getMethod() {
        return method;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public String getPath() {
        return path;
    }

    public String toString() {
        return rawRequestString;
    }
}


