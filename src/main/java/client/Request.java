package client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Request {

    String method;
    HashMap<String, String> headers;
    String body;
    String url;

    public Request(String method, HashMap<String, String> headers, String body, String url) {
        this.method = method;
        this.headers = headers;
        this.body = body;
        this.url = url;
    }

    public Response send() {

        String responseStr = "";

        try {
            // parse the Url into the host, port, path and query
            URL url = new URL(this.url);
            String host = url.getHost();
            int port = url.getPort() == -1 ? 80 : url.getPort();
            String path = url.getPath().equals("") ? "/" : url.getPath();
            String query = url.getQuery();

            //create a new request
            StringBuilder request;

            if (query == null) {
                request = new StringBuilder(this.method + " " + path + " HTTP/1.0\r\n");
            } else {
                request = new StringBuilder(this.method + " " + path + "?" + query + " HTTP/1.0\r\n");
            }

            // If the Host is not specified in the header, the default Host is added to header
            if (!headers.containsKey("Host")) {
                headers.put("Host", host);
            }

            //  If the User-Agent is not specified in the header, the default User-Agent is added to header
            if (!headers.containsKey("User-Agent")) {
                headers.put("User-Agent", "Concordia-HTTP/1.0");
            }

            headers.put("Content-Length", String.valueOf(this.body.length()));

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }

            if (!this.body.equals("")) {
                request.append("\r\n");
                request.append(this.body);
            }

            request.append("\r\n");


            // Router address
            String routerHost = "localhost";
            int routerPort = 3000;

            // Server address

            String msg = request.toString();

            SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
            InetSocketAddress serverAddress = new InetSocketAddress(host, port);

            responseStr = UDPClient.runClient(routerAddress, serverAddress, msg);



        } catch (Exception e) {
            e.printStackTrace();
        }

        return new Response(responseStr);

    }
}
