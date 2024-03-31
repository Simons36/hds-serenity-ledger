package pt.ulisboa.tecnico.hdsledger.service.models.util;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoIO;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServiceConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.enums.TypeOfProcess;

public class ByzantineUtils {

    public static Block GenerateRandomBlockForTestBehavior1(ServiceConfig serviceConfig,
            ProcessConfig[] allProcessConfigs, ProcessConfig byzantineConfig) {

        int numOfTransactions = serviceConfig.getNumTransactionsInBlock();

        Transaction[] transactions = new Transaction[numOfTransactions];
        
        for (int i = 0; i < numOfTransactions; i++) {
            ProcessConfig senderConfig = getRandomClientConfig(allProcessConfigs);
            ProcessConfig receiverConfig = getRandomClientConfig(allProcessConfigs);

            try{
                transactions[i] = new Transaction(CryptoIO.readPrivateKey("../" + byzantineConfig.getPrivateKeyPath()), 
                                                  CryptoIO.readPublicKey("../" + senderConfig.getPublicKeyPath()),
                                                  CryptoIO.readPublicKey("../" + receiverConfig.getPublicKeyPath()),
                                                  Math.random() * serviceConfig.getInitialAccountBalance(),
                                                  Base64.getEncoder().encodeToString(CryptoUtil.generateNonce(0)));
            }catch(Exception e){
                e.printStackTrace();
            }

        }

        Block block = new Block(0, transactions);

        //set receiver node id
        block.setNodeIdOfFeeReceiver(byzantineConfig.getId());

        // set hash
        block.setHash(block.generateHash());

        // set signature (with bizantine's node private key)
        try {
            block.signBlock(CryptoIO.readPrivateKey("../" + byzantineConfig.getPrivateKeyPath()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return block;


    }

    public static String DoubleAmountOfTransaction(String blockInString){

        Block block = new Gson().fromJson(blockInString, Block.class);
        Transaction transaction = block.getTransactions()[0];

        double originalValueOfFee = transaction.getFee();

        System.out.println("[BYZANTINE] Changing the amount of transaction " + transaction.getTransactionIdInHex() + " to double");

        transaction.setAmount(transaction.getAmount() * 2);
        transaction.setFee(transaction.getFee() * 2);

        try {
            transaction.setTransactionId(Transaction.CreateTransactionId(transaction.getSenderPublicKey(), 
                                                                        transaction.getReceiverPublicKey(), 
                                                                        transaction.getAmount(), 
                                                                        transaction.getNonceInBase64()));
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        //lastly, update the block

        block.getTransactions()[0] = transaction;

        //change total fees of block
        block.setTotalFees(block.getTotalFees() - originalValueOfFee + transaction.getFee());

        //set new hash
        block.setHash(block.generateHash());
        
        return new Gson().toJson(block);

    }

    private static ProcessConfig getRandomClientConfig(ProcessConfig[] allProcessConfigs) {
        
        // Get only clients
        List<ProcessConfig> clients = Arrays.stream(allProcessConfigs).filter(p -> p.getType().equals(TypeOfProcess.client)).collect(Collectors.toList());

        // Get random client
        int randomIndex = (int) (Math.random() * clients.size());
        return clients.get(randomIndex);

    }

}
