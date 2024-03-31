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

public class Test5_ClientInvalidSignature {

    private final static String COMMANDS_FILE = "client_invalid_signature_test.json";

    private final static String CONFIG_PATH = "../Common/src/main/resources/client_invalid_signature_test_config.json";

    private final static String TEST_OUTPUT = "src/test/java/output/client_invalid_signature_test_output.txt";

    private final static String EXPECTED_OUTPUT = "";

    private List<Process> processes = new ArrayList<>();
    
    /*
     * In this text the client sends two append requests to the nodes, but the signature is invalid (it will use the wrong private key to sign the request).
     * Therefore, nodes should not act upon the requests and the ledger should remain empty.
     * 
     * This test serves to verify if the nodes are correctly validating the signature of the requests from clients.
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

        // Wait 10 seconds

        Util.Delay(10);

        // Check if the output file is the same as the expected output

        try {
            assertEquals(EXPECTED_OUTPUT, Files.readString(Paths.get(TEST_OUTPUT)));
            System.out.println("Client Invalid Signature Test: Success");
        } catch (IOException e) {
            throw e;
        }

        // Kill all processes

        Util.KillAllProcesses(processes);

        // Wait 4 seconds
        Util.Delay(4);
        
    }
}
