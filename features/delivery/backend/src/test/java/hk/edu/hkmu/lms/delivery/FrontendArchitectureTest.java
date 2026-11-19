package hk.edu.hkmu.lms.delivery;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FrontendArchitectureTest {
    private static final Pattern IMPORT = Pattern.compile(
        "^\\h*(?:import|export)\\h+(?:[^\\r\\n;]+?\\h+from\\h*)?['\"]([^'\"]+)['\"]"
            + "|\\bimport\\s*\\(\\s*['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    @Test
    void nativeModulesFollowDeliveryFeatureBoundaries() throws IOException {
        Path source = Path.of("..", "frontend", "src").toAbsolutePath().normalize();
        Path vendor = source.resolve(Path.of("shared", "vendor"));
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".js") && !path.startsWith(vendor)).toList()) {
                String owner = area(source.relativize(file));
                var imports = IMPORT.matcher(Files.readString(file));
                while (imports.find()) {
                    String specifier = imports.group(1) == null ? imports.group(2) : imports.group(1);
                    Path target = file.getParent().resolve(specifier).normalize();
                    assertTrue(specifier.startsWith("./") || specifier.startsWith("../"),
                        file + " must use a relative module import");
                    assertTrue(target.startsWith(source) && Files.isRegularFile(target),
                        file + " imports missing or outside source " + specifier);
                    String dependency = area(source.relativize(target));
                    assertTrue(owner.equals("app") || owner.equals(dependency) || dependency.equals("shared"),
                        file + " imports " + dependency + " outside its Delivery feature");
                }
            }
        }
    }

    private String area(Path path) {
        String folder = path.getName(0).toString();
        if (folder.equals("main.js") || folder.equals("app") || folder.equals("shared")) {
            return folder.equals("shared") ? "shared" : "app";
        }
        if (folder.equals("features") && path.getNameCount() > 2) {
            return "features/" + path.getName(1);
        }
        throw new AssertionError("Declare the frontend boundary for " + path);
    }
}
