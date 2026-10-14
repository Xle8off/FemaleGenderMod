import java.nio.file.*;
import java.util.zip.*;
import java.lang.reflect.*;

/** JVM-level linkage smoke check, not a substitute for a real Minecraft launch. */
public class ProtectedLoadCheck {
    public static void main(String[] args) throws Exception {
        int checked = 0;
        try (var jar = new ZipFile(args[0])) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.equals("module-info.class")) continue;
                var type = Class.forName(name.substring(0, name.length()-6).replace('/', '.'), false,
                    ProtectedLoadCheck.class.getClassLoader());
                type.getDeclaredMethods(); type.getDeclaredFields(); type.getDeclaredConstructors();
                checked++;
            }
        }
        String mapping = Files.readString(Path.of(args[1]));
        String section = mapping.substring(mapping.indexOf("com.wildfire.client.music.SpringPlusSession -> "));
        String className = section.substring(section.indexOf(" -> ") + 4, section.indexOf(':')).trim();
        var type = Class.forName(className);
        // Aggressive overloads may share names; select by full descriptor.
        for (var method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())
                && method.getParameterCount() == 0 && method.getReturnType() == boolean.class) {
                if ((boolean)method.invoke(null)) throw new AssertionError("Protected session must fail closed");
            }
        }
        System.out.println("Protected JVM linkage passed for " + checked + " classes; session fails closed");
    }
}
