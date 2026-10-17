import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.io.File;
import java.util.*;

/** Grammar parsing only: does not resolve Minecraft APIs or replace a real compile. */
public final class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var manager=compiler.getStandardFileManager(diagnostics,null,null)) {
            var files=manager.getJavaFileObjectsFromFiles(Arrays.stream(args).map(File::new).toList());
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none","--release","21"),null,files);
            int count=0;for(var ignored:task.parse())count++;
            boolean failed=false;
            for(var diagnostic:diagnostics.getDiagnostics()) {
                if(diagnostic.getKind()==Diagnostic.Kind.ERROR) { failed=true;System.err.println(diagnostic); }
            }
            if(failed)throw new AssertionError("Java syntax errors");
            System.out.println("Java syntax-only parse passed: "+count+" files (not a Minecraft compile)");
        }
    }
}
