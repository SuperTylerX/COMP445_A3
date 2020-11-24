package common;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static List<String> getStrList(String inputString, int length) {

        List<String> list = new ArrayList<String>();
        for (int index = 0; index < Math.ceil(inputString.length() / (double) length); index++) {
            String childStr = substring(inputString, index * length,
                    (index + 1) * length);
            list.add(childStr);
        }
        return list;

    }

    public static String substring(String str, int f, int t) {
        if (f > str.length())
            return null;
        if (t > str.length()) {
            return str.substring(f, str.length());
        } else {
            return str.substring(f, t);
        }
    }
}
