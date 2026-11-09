package hk.edu.hkmu.lms.delivery;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DeliveryArchitectureTest {
    private static final String BASE = "hk.edu.hkmu.lms.delivery";
    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*import\\s+(?:static\\s+)?" + Pattern.quote(BASE) + "\\.([a-z][a-z0-9]*)(?=\\.)",
        Pattern.MULTILINE
    );
    private static final Map<String, Set<String>> DEPENDENCIES = Map.ofEntries(
        Map.entry("", Set.of()),
        Map.entry("support", Set.of()),
        Map.entry("access", Set.of("support")),
        Map.entry("operations", Set.of("access", "support")),
        Map.entry("fulfilment", Set.of("support")),
        Map.entry("pickup", Set.of("operations", "fulfilment", "support")),
        Map.entry("attempts", Set.of("operations", "fulfilment", "support")),
        Map.entry("returns", Set.of("operations", "fulfilment", "support")),
        Map.entry("workspace", Set.of("access", "fulfilment", "support")),
        Map.entry("demo", Set.of("support")),
        Map.entry("web", Set.of("support"))
    );

    @Test
    void internalImportsFollowDeliveryPackageBoundaries() throws IOException {
        Path root = Path.of("src", "main", "java", BASE.replace('.', '/'));
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Path relative = root.relativize(file);
                String owner = relative.getNameCount() == 1 ? "" : relative.getName(0).toString();
                assertTrue(DEPENDENCIES.containsKey(owner), "Declare the boundary for " + relative);
                var imports = IMPORT.matcher(Files.readString(file));
                while (imports.find()) {
                    String dependency = imports.group(1);
                    assertTrue(owner.equals(dependency) || DEPENDENCIES.get(owner).contains(dependency),
                        relative + " imports " + dependency + " outside its Delivery boundary");
                }
            }
        }
    }
}
