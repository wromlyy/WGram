import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class JavaSyntaxCheck {
    public static void main(String[] paths) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            ArrayList<File> inputs = new ArrayList<>();
            for (String path : paths) inputs.add(new File(path));
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                Arrays.asList("-proc:none", "-encoding", "UTF-8"), null, files.getJavaFileObjectsFromFiles(inputs));
            task.parse();
            for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) throw new AssertionError(d.toString());
            }
        }
        System.out.println("PASS: Java syntax parsed for " + paths.length + " WGram and integration sources (no Android type checking)");
    }
}
