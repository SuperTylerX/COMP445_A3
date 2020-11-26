package server;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;
import java.util.Scanner;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class HttpfsServiceThread {

    private String directory;
    private Path path;
    private File file;
    private Request request;
    private Response response;
    private boolean isDebug;


    public HttpfsServiceThread(String reqStr, HttpfsService hfs) throws Exception {
        this.directory = hfs.getDirectory();
        this.isDebug = hfs.isDebug();
        this.init(reqStr);
    }

    public void init(String requestString) throws Exception {

        System.out.println(requestString);

        this.request = new Request(requestString);
        String filePath = request.getPath();
        this.path = Paths.get(directory + filePath);
        this.file = new File(path.toString());

    }


    public String run() {

        if (!isInsideFolder()) {
            noPermissionResponseHandler();
        } else {
            if (request.getMethod().equals("GET")) {
                if (file.exists()) {
                    if (file.isDirectory()) {
                        readDirectoryHandler();
                    } else if (file.isFile()) {
                        readFileHandler();
                    }
                } else {
                    fileNotExistResponseHandler();
                }

            } else if (request.getMethod().equals("POST")) {
                if (file.exists() && file.isDirectory()) {
                    directoryAlreadyExistResponseHandler();
                } else {
                    writeFileHandler();
                }

            } else {
                methodNotAllowedResponseHandler();
            }
        }

        if (isDebug) {
            System.out.println("\n<<<<<<<<<<<<<<<<<<<<<<<<");
            System.out.println(this.response.toString());
            System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<\r\n");
        }

        return this.response.toString();

    }

    public void readDirectoryHandler() {

        String status = "200 OK";
        String body = "";
        HashMap headers = new HashMap<>();

        File[] tempFileList = file.listFiles();
        for (int i = 0; i < Objects.requireNonNull(tempFileList).length; i++) {
            if (tempFileList[i].isFile()) {
                body = body.concat("File: " + tempFileList[i].getName() + "\r\n");
            } else if (tempFileList[i].isDirectory()) {
                body = body.concat("Directory: " + tempFileList[i].getName() + "\r\n");
            }
        }
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void readFileHandler() {

        try {

            // Create Response
            String status = "200 OK";
            String body = "";
            HashMap headers = new HashMap<>();

            // Read the target file
            String fileContent = "";
            Scanner myReader = null;
            try {
                myReader = new Scanner(this.file);
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    fileContent = fileContent.concat(data);
                }
                myReader.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            body = fileContent;

            // Add header
            if (getMIME(path) != null) {
                headers.put("content-type", getMIME(path));
            }
            headers.put("content-disposition", "attachment; filename=" + file.getName());
            this.response = new Response(status, headers, body);


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void writeFileHandler() {

        // If the parent folder does not exist, create the parent folder
        if (!file.exists()) {
            File parentFolder = new File(file.getParent());
            System.out.println(parentFolder);
            if (!parentFolder.exists()) {
                try {
                    boolean s = parentFolder.mkdirs();
                    System.out.println(s);
                } catch (SecurityException e) {
                    serverInternalErrorResponseHandler();
                    return;
                }
            }
        }

        InputStream in = null;

        // write new content to the file

        try {
            FileWriter fileWritter = new FileWriter(file, false);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(this.request.getBody());
            bufferWritter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        String status = "200 OK";
        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");
        String body = "Successfully written to file " + file.getName();
        this.response = new Response(status, headers, body);


    }

    public void fileNotExistResponseHandler() {
        String status = "404 Not Found";
        String body = "404 File does not exist!";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);
    }

    public void noPermissionResponseHandler() {

        String status = "403 Forbidden";
        String body = "Forbidden";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);
    }

    public void directoryAlreadyExistResponseHandler() {
        String status = "403 Forbidden";
        String body = "The file could not be created because there is a folder with the same name";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }


    public void methodNotAllowedResponseHandler() {

        String status = "405 Method Not Allowed";
        String body = "Method Not Allowed";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public void serverInternalErrorResponseHandler() {

        String status = "500 Internal Server Error";
        String body = "Internal Server Error";

        HashMap headers = new HashMap<>();
        headers.put("content-type", "text/plain");
        headers.put("content-disposition", "inline");

        this.response = new Response(status, headers, body);

    }

    public static String getMIME(Path path) throws IOException {
        return Files.probeContentType(path);
    }

    public boolean isInsideFolder() {

        File directoryFile = new File(this.directory);
        File requestFile = this.file;

        try {
            String directoryFileCanonicalPath = directoryFile.getCanonicalPath();
            String requestFileCanonicalPath = requestFile.getCanonicalPath();
            return requestFileCanonicalPath.contains(directoryFileCanonicalPath);

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

    }

}
