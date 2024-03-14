import java.io.Writer;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.junit.Before;
import org.junit.Test;

public class Append {
    private final String terminal = "kitty";

    @Before
    public void setUp() {
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
    
    @Test
    public void testSomething() {
        new Thread(() -> {
            SpawnNewClient("client1", "regular_config.json", "localhost", "4001", "all");
        }).start();

        /* to give time to show execution of the append command working and to terminate all the processes so we can set up for the next test */
        long Time0 = System.currentTimeMillis();
        long Time1;
        long runTime = 0;
        
        while (runTime < 40000) { 
            Time1 = System.currentTimeMillis();
            runTime = Time1 - Time0;
        }
    }

    private void SpawnNewNode(String nodeId, String configFile) {

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "mvn exec:java -Dexec.args=\"" + nodeId + " " + configFile + "\"");

        try {
            Process process = builder.inheritIO().start();
            process.waitFor(30, TimeUnit.SECONDS);
            process.destroy(); 

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void SpawnNewClient(String clientId, String configFile, String IP, String port, String policy) {

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "cd ..; cd Client; mvn exec:java -Dexec.args=\"" + clientId + " " + configFile + " " + IP + " " + port + " " + policy + "\"");

        try {
            
            Process process = builder.start();
            
            long Time0 = System.currentTimeMillis();
            long Time1;
            long runTime = 0;
            while (runTime < 2000) {
                Time1 = System.currentTimeMillis();
                runTime = Time1 - Time0;
            }

            OutputStream outputStream = process.getOutputStream();

            // Write input data to the subprocess
            try (Writer writer = new OutputStreamWriter(outputStream)) {
                String inputData = "append ola\n";
                writer.write(inputData);
            }
    
            process.waitFor(30, TimeUnit.SECONDS);
            process.destroy();    

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
