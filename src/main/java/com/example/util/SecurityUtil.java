package com.example.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for security-related operations such as password hashing.
 */
public class SecurityUtil {

    private static final Logger logger = Logger.getLogger(SecurityUtil.class.getName());
    
    // For future implementation: salt length for password hashing
    private static final int SALT_LENGTH = 16;
    
    /**
     * Hashes a password using SHA-256 algorithm.
     * This is a simple implementation for demonstration purposes.
     * In a production environment, a more secure algorithm with salt should be used.
     * 
     * @param password The password to hash
     * @return The hashed password, or null if hashing fails
     */
    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            
            // Convert bytes to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "Error hashing password", e);
            return null;
        }
    }
    
    /**
     * Generates a random salt for password hashing.
     * This method is prepared for future implementation of salted password hashing.
     * 
     * @return A Base64 encoded random salt
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Hashes a password with a salt using SHA-256 algorithm.
     * This method is prepared for future implementation of salted password hashing.
     * 
     * @param password The password to hash
     * @param salt The salt to use
     * @return The salted and hashed password, or null if hashing fails
     */
    public static String hashPasswordWithSalt(String password, String salt) {
        if (password == null || password.isEmpty() || salt == null || salt.isEmpty()) {
            return null;
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedBytes = md.digest(password.getBytes());
            
            // Convert bytes to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "Error hashing password with salt", e);
            return null;
        }
    }
    
    /**
     * Validates a password against a stored hash.
     * 
     * @param password The password to validate
     * @param storedHash The stored hash to validate against
     * @return True if the password matches the stored hash, false otherwise
     */
    public static boolean validatePassword(String password, String storedHash) {
        if (password == null || password.isEmpty() || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        
        String hashedPassword = hashPassword(password);
        return hashedPassword != null && hashedPassword.equals(storedHash);
    }
}
