package android.content;
import java.util.HashMap;
import java.util.Map;
public class SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    public boolean getBoolean(String k, boolean d) { return (Boolean) values.getOrDefault(k, d); }
    public int getInt(String k, int d) { return (Integer) values.getOrDefault(k, d); }
    public long getLong(String k, long d) { return (Long) values.getOrDefault(k, d); }
    public Editor edit() { return new Editor(); }
    public class Editor {
        public Editor putBoolean(String k, boolean v) { values.put(k, v); return this; }
        public Editor putInt(String k, int v) { values.put(k, v); return this; }
        public Editor putLong(String k, long v) { values.put(k, v); return this; }
        public void apply() {}
    }
}
