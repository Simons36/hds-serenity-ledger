package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.util.ByzantineUtils;
import pt.ulisboa.tecnico.hdsledger.service.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.ServiceConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServiceConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.enums.TypeOfProcess;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    private static String nodesConfigPath = "../Common/src/main/resources/";

    private static String serviceConfigPath = "src/main/resources/";

    public static void main(String[] args) {

        try {

            String id = args[0];
            nodesConfigPath += args[1];

            // Create configuration instances
            ProcessConfig[] allConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);

            
            // ---- Filter out process configs that aren't from type "node" and put them in clientConfigs ---- //
            ProcessConfig[] clientConfigs = Arrays.stream(allConfigs)
            .filter(processConfig -> !TypeOfProcess.node.equals(processConfig.getType()))
            .toArray(ProcessConfig[]::new);
            

            // Now put the nodes configs in nodeConfigs
            ProcessConfig[] nodeConfigs = Arrays.stream(allConfigs)
            .filter(processConfig -> TypeOfProcess.node.equals(processConfig.getType()))
            .toArray(ProcessConfig[]::new);

            for(int i = 0; i < nodeConfigs.length; i++){
                nodeConfigs[i].setNodePosition(i + 1);
            }

            // Get the leader config
            ProcessConfig leaderConfig = Arrays.stream(nodeConfigs)
            .filter(ProcessConfig::isLeader)
            .findAny().orElse(null);

            // Get this node config
            ProcessConfig nodeConfig = Arrays.stream(nodeConfigs)
            .filter(c -> c.getId().equals(id))
            .findAny().orElse(null);

            // Now import service config (these are parameters specific to the service, like account starting balance)
            ServiceConfig serviceConfig = new ServiceConfigBuilder().fromFile(serviceConfigPath + nodeConfig.getServiceConfig());


            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; is leader: {3}\n",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
                    nodeConfig.isLeader()));

            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), nodeConfigs,
                    ConsensusMessage.class, true, true);

            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(linkToNodes, nodeConfig, leaderConfig,
                    nodeConfigs, clientConfigs, serviceConfig);

            nodeService.listen();


            // Link that will be used for client <-> node communication
            Link linkToClients = new Link(nodeConfig, nodeConfig.getClientRequestPort(), clientConfigs,
                    ConsensusMessage.class, false, true);

            // Service that will be used for client communication; it needs to take nodeService to be able to make calls
            // to consensus algorithm and respond to clients
            ClientService clientService = new ClientService(linkToClients, nodeConfig, clientConfigs, nodeService);

            nodeService.addClientService(clientService);

            clientService.listen();

            if(nodeService.getBehaviourType() == 1){
                // Wait a bit for every node to start
                Thread.sleep(1000);

                Block blockToSend = ByzantineUtils.GenerateRandomBlockForTestBehavior1(serviceConfig, allConfigs, nodeConfig);

                nodeService.startConsensus(blockToSend.toJson());
            }


            // //TODO: remove next lines
            // Thread.sleep(500);

            // if(nodeConfig.isLeader()) {
            // nodeService.startConsensus("aa");
            // }
            // nodeService.startConsensus("aa");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
