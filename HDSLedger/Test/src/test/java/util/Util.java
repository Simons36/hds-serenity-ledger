package util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.enums.TypeOfProcess;

public class Util {

    private static final String terminal = "kitty";

    private static final String pathToNodeConfigFolder = "../Common/src/main/resources/";

    private static final String definedSendingPolicyForClients = "all";

    public static Process SpawnNewNode(String nodeId, String configFile) {

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "cd ..; cd Service; mvn exec:java -Dexec.args=\"" + nodeId + " " + configFile + "\"");

        try {
            Process process = builder.inheritIO().start();
            return process;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static Process SpawnNewClient(String clientId, String configFile, String IP, String port, String policy) {

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "cd ..; cd Client; mvn exec:java -Dexec.args=\"" + clientId + " " + configFile + " " + IP + " " + port
                        + " " + policy + " " + "true" +  "\"");

        try {
            Process process = builder.inheritIO().start();
            return process;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static Process SpawnNewClient(String clientId, String configFile, String IP, String port, String policy,
            String commandsFilename) {

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "cd ..; cd Client; mvn exec:java -Dexec.args=\"" + clientId + " " + configFile + " " + IP + " " + port
                        + " " + policy + " " + "true" + " " + commandsFilename + "\"");

        try {
            return builder.inheritIO().start();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static Process[] LaunchAllNodes(String pathToConfig) {
        ProcessConfig[] nodeConfigs = Arrays.stream(new ProcessConfigBuilder().fromFile(pathToConfig))
                .filter(config -> config.getType().equals(TypeOfProcess.node))
                .toArray(ProcessConfig[]::new);

        Process[] processList = new Process[nodeConfigs.length];

        // get the name of the config file
        int k = pathToConfig.split("/").length - 1;
        String nameOfConfig = pathToConfig.split("/")[k];

        for (int i = 0; i < nodeConfigs.length; i++) {
            processList[i] = SpawnNewNode(nodeConfigs[i].getId(), nameOfConfig);
        }

        return processList;

    }

    public static Process[] LaunchAllClients(String pathToConfig, Map<String, String> clientIdToCommandsFilenameMap) {
        ProcessConfig[] clientConfigs = Arrays.stream(new ProcessConfigBuilder().fromFile(pathToConfig))
                .filter(config -> config.getType().equals(TypeOfProcess.client))
                .toArray(ProcessConfig[]::new);

        Process[] processList = new Process[clientConfigs.length];

        // get the name of the config file
        int k = pathToConfig.split("/").length - 1;
        String nameOfConfig = pathToConfig.split("/")[k];

        for (int i = 0; i < clientConfigs.length; i++) {
            String clientId = clientConfigs[i].getId();
            String commandsFilename = clientIdToCommandsFilenameMap.get(clientId);

            String IP = clientConfigs[i].getHostname();

            String port = Integer.toString(clientConfigs[i].getPort());

            if (commandsFilename != null)
                processList[i] = SpawnNewClient(clientId, nameOfConfig, IP, port, definedSendingPolicyForClients,
                        commandsFilename);
            else
                processList[i] = SpawnNewClient(clientConfigs[i].getId(), nameOfConfig, IP, port,
                        definedSendingPolicyForClients);
        }

        return processList;

    }

    public static void KillAllProcesses(List<Process> processes) {
        for (Process p : processes) {
            p.destroy();
        }
    }

    public static void Delay(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
