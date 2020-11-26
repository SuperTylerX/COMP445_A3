package client;

public class Test {
    public static void main(String[] args) {
        Httpc.main("post http://localhost:8080/a.txt -f C:\\Users\\Tyler\\Desktop\\test.txt".split(" "));
    }
}
