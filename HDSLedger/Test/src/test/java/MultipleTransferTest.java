import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import util.Util;

public class MultipleTransferTest {
    private final static String COMMANDS_FILE = "multiple_transfer_test.json";

    private final static String CONFIG_PATH = "../Common/src/main/resources/regular_config.json";

    private final static String TEST_OUTPUT = "src/test/java/output/multiple_transfer_test_output.txt";

    private final static String EXPECTED_OUTPUT = "Server replied with success.\nServer replied with success.\nServer replied with success.\nclient1 - Balance: 650\n";

    private List<Process> processes = new ArrayList<>();

    /*
     * This test launches all nodes and a client that sends multiple transfer requests and checks balance afterwards
     */

    
    @Test
    public void testSomething() throws IOException{
        
        // Launch all nodes
        processes.addAll(Arrays.asList(Util.LaunchAllNodes(CONFIG_PATH)));

        // Wait 4 seconds
        Util.Delay(4);

        Map<String, String> clientCommandsFileMap = new HashMap<String, String>();
        clientCommandsFileMap.put("client1", COMMANDS_FILE);

        // Launch client
        processes.addAll(Arrays.asList(Util.LaunchAllClients(CONFIG_PATH, clientCommandsFileMap)));

        // Wait 30 seconds

        Util.Delay(40);

        // Check if the output file is the same as the expected output

        try {
            assertEquals(EXPECTED_OUTPUT, Files.readString(Paths.get(TEST_OUTPUT)));
            System.out.println("Multiple Transfer Test: Success");
        } catch (IOException e) {
            throw e;
        }

        // Kill all processes

        Util.KillAllProcesses(processes);

        // Wait 4 seconds
        Util.Delay(4);
        
    }
}