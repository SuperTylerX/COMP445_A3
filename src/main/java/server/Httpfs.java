package server;

public class Httpfs {

    public static void main(String[] args) {
        try {
            HttpfsService hfs = new HttpfsService(args);
            hfs.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
