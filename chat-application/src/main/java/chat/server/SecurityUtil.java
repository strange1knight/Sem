package chat.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class SecurityUtil {
    private static final Logger logger = LoggerFactory.getLogger(SecurityUtil.class);
    private static final SecureRandom random = new SecureRandom();
    private static final int SALT_LENGTH = 32;
    private static final int HASH_ITERATIONS = 10000;
    
    public static String hashPassword(String password) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            
            byte[] hashedPassword = hashWithIterations(password, salt, HASH_ITERATIONS);
            
            byte[] combined = new byte[salt.length + hashedPassword.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hashedPassword, 0, combined, salt.length, hashedPassword.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("Error hashing password: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private static byte[] hashWithIterations(String password, byte[] salt, int iterations) 
            throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        md.update(password.getBytes(StandardCharsets.UTF_8));
        byte[] hash = md.digest();
        
        for (int i = 1; i < iterations; i++) {
            md.reset();
            hash = md.digest(hash);
        }
        
        return hash;
    }
    
    public static boolean checkPassword(String password, String storedHash) {
        try {
            byte[] combined = Base64.getDecoder().decode(storedHash);
            
            if (combined.length < SALT_LENGTH) {
                logger.error("Stored hash is too short");
                return false;
            }
            
            byte[] salt = new byte[SALT_LENGTH];
            System.arraycopy(combined, 0, salt, 0, salt.length);
            
            byte[] hashedPassword = hashWithIterations(password, salt, HASH_ITERATIONS);
            
            for (int i = 0; i < hashedPassword.length; i++) {
                if (hashedPassword[i] != combined[salt.length + i]) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Error checking password: {}", e.getMessage(), e);
            return false;
        }
    }
}