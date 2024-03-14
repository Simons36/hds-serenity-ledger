import java.util.concurrent.TimeUnit;

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

        Delay(15000);
    }

    private void SpawnNewNode(String nodeId, String configFile) {
        // node1

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "mvn exec:java -Dexec.args=\"" + nodeId + " " + configFile + "\"");

        try {
            Process process = builder.inheritIO().start();
            process.waitFor(13, TimeUnit.SECONDS);
            process.destroy();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void Delay(int time) {
        long T0 = System.currentTimeMillis();
        long T1;
        long runTime = 0;
        while (runTime < time) {
            T1 = System.currentTimeMillis();
            runTime = T1 - T0;
        }
    }
}
