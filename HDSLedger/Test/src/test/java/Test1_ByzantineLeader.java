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

public class Test1_ByzantineLeader {

    private final static String COMMANDS_FILE_CLIENT1 = "client1_config_test_behavior_1.json";

    private final static String COMMANDS_FILE_CLIENT2 = "client2_config_test_behavior_1.json";

    private final static String CONFIG_PATH = "../Common/src/main/resources/config_test_behavior_1.json";

    private final static String TEST_OUTPUT_CLIENT1 = "src/test/java/output/test_behavior_1_output_client1.txt";

    private final static String TEST_OUTPUT_CLIENT2 = "src/test/java/output/test_behavior_1_output_client2.txt";

    private final static String EXPECTED_OUTPUT_CLIENT1 = "client1 - Balance: 1,000\n";

    private final static String EXPECTED_OUTPUT_CLIENT2 = "client2 - Balance: 1,000\n";

    private List<Process> processes = new ArrayList<>();

    /*
     * This test launches all nodes, the leader is a byzantine node, and this node tries to propose a random block
     *  (the transactions of this block were not authorized by the senders). Nodes should reject this block
     *  and the client's balances should remain the same.
     */

    
    @Test
    public void testSomething() throws IOException{
        
        // Launch all nodes
        processes.addAll(Arrays.asList(Util.LaunchAllNodes(CONFIG_PATH)));

        // Wait 4 seconds
        Util.Delay(4);

        
        Map<String, String> clientCommandsFileMap = new HashMap<String, String>();
        clientCommandsFileMap.put("client1", COMMANDS_FILE_CLIENT1);
        clientCommandsFileMap.put("client2", COMMANDS_FILE_CLIENT2);

        // Launch client
        processes.addAll(Arrays.asList(Util.LaunchAllClients(CONFIG_PATH, clientCommandsFileMap)));

        // Wait 17 seconds

        Util.Delay(15);

        // Check if the output file is the same as the expected output

        try {
            assertEquals(EXPECTED_OUTPUT_CLIENT1, Files.readString(Paths.get(TEST_OUTPUT_CLIENT1)));
            assertEquals(EXPECTED_OUTPUT_CLIENT2, Files.readString(Paths.get(TEST_OUTPUT_CLIENT2)));
            System.out.println("Byzantine Leader Test: Success");
        } catch (IOException e) {
            throw e;
        }

        // Kill all processes

        Util.KillAllProcesses(processes);


        
    }

}
