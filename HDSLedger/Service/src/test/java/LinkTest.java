import org.junit.Before;
import org.junit.Test;

public class LinkTest {

    private final String terminal = "kitty";

    @Before
    public void setUp() {
    }

    @Test
    public void testSomething() {

        // create three nodes

        new Thread(() -> {
            SpawnNewNode("1", "regular_config.json");
        }).start();

        new Thread(() -> {
            SpawnNewNode("2", "regular_config.json");
        }).start();

        new Thread(() -> {
            SpawnNewNode("3", "regular_config.json");
        }).start();
        new Thread(() -> {
            SpawnNewNode("4", "regular_config.json");
        }).start();


    }

    private void SpawnNewNode(String nodeId, String configFile) {
        // node1

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "mvn exec:java -Dexec.args=\"" + nodeId + " " + configFile + "\"");

        try {
            Process process = builder.inheritIO().start();
            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
