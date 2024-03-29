package pt.ulisboa.tecnico.hdsledger.utilities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class Util {

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // Convert each byte to its hexadecimal representation
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                // Append a leading zero if the hexadecimal string is only one character long
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static byte[] hexToBytes(String hexString) {
        // Ensure that the hexadecimal string has an even number of characters
        if (hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hexadecimal string length");
        }
    
        // Allocate a byte array half the length of the hexadecimal string
        byte[] bytes = new byte[hexString.length() / 2];
    
        // Iterate over the hexadecimal string, converting each pair of characters to a byte
        for (int i = 0; i < hexString.length(); i += 2) {
            // Extract the substring representing a pair of characters
            String hexPair = hexString.substring(i, i + 2);
            // Convert the hexadecimal pair to a byte and store it in the byte array
            bytes[i / 2] = (byte) Integer.parseInt(hexPair, 16);
        }
    
        return bytes;
    }

    // Utility method to serialize an object to a byte array
    public static byte[] serialize(Object obj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            // Handle serialization error
            e.printStackTrace();
        } finally {
            try {
                bos.close();
            } catch (IOException e) {
                // Handle stream closing error
                e.printStackTrace();
            }
        }
        return null;
}

    
    
    
    
}
