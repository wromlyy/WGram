package android.content;
import java.util.HashMap;
import java.util.Map;
public class Context {
    public static final int MODE_PRIVATE = 0;
    private final Map<String, SharedPreferences> files = new HashMap<>();
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return files.computeIfAbsent(name, key -> new SharedPreferences());
    }
}
