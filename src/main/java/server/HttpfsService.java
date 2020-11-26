package server;

import java.io.*;

public class HttpfsService {

    private static final int DEFAULT_PORT = 8080;
    private static final boolean DEFAULT_IS_DEBUG = false;
    public static final String DEFAULT_DIRECTORY = ".";

    private boolean isDebug;
    private int port;
    private String directory;
    private String[] args;

    public HttpfsService(String[] args) throws Exception {
        this.isDebug = DEFAULT_IS_DEBUG;
        this.port = DEFAULT_PORT;
        this.directory = DEFAULT_DIRECTORY;
        this.args = args;
        this.initService();
    }

    public void initService() throws Exception {
        for (int i = 0; i < this.args.length; i++) {
            if (this.args[i].equals("-v")) {
                this.isDebug = true;
            } else if (this.args[i].equals("-p")) {
                this.port = Integer.parseInt(this.args[++i]);
                if (this.port > 65535 || this.port < 1) {
                    throw new Exception("[ERROR] Wrong port number");
                }
            } else if (this.args[i].equals("-d")) {
                this.directory = this.args[++i];
            }
        }
    }

    public void serve() throws Exception {
        this.listening();
    }

    public void listening() throws IOException {

        UDPServer server = new UDPServer();
        server.listenAndServe(port, this);

    }

    public boolean isDebug() {
        return isDebug;
    }

    public int getPort() {
        return port;
    }

    public String getDirectory() {
        return directory;
    }

}

