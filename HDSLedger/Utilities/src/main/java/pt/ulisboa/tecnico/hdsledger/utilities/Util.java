package pt.ulisboa.tecnico.hdsledger.utilities;

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
    
    
    
}
