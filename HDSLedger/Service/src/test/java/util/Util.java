package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

public class Util {

    private static final String terminal = "kitty";

    private static final String pathToScript = "src/test/java/scripts/script1.sh";

    public static Process SpawnNewNode(String nodeId, String configFile) {

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "mvn exec:java -Dexec.args=\"" + nodeId + " " + configFile + "\"");

        try {
            Process process = builder.inheritIO().start();
            return process;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static void SpawnNewClient(String clientId, String configFile, String IP, String port, String policy) {

        ProcessBuilder builder = new ProcessBuilder(terminal, "--", "sh", "-c",
                "cd ..; cd Client; mvn exec:java -Dexec.args=\"" + clientId + " " + configFile + " " + IP + " " + port + " " + policy + "\"");

        try {
            Process process = builder.inheritIO().start();
            

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
