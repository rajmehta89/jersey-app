package com.example.util;

import java.util.regex.Pattern;

/**
 * Utility class for validating input data.
 */
public class ValidationUtil {

    // Regular expression for validating company codes
    private static final Pattern COMPANY_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    
    // Regular expression for validating email addresses
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
    // Regular expression for validating usernames (alphanumeric, underscore, dash, min 3 chars, max 30 chars)
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");
    
    /**
     * Validates a company code.
     * 
     * @param companyCode The company code to validate
     * @return true if the company code is valid, false otherwise
     */
    public static boolean isValidCompanyCode(String companyCode) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            return false;
        }
        return COMPANY_CODE_PATTERN.matcher(companyCode).matches();
    }
    
    /**
     * Validates an email address.
     * 
     * @param email The email address to validate
     * @return true if the email address is valid, false otherwise
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Validates a username.
     * 
     * @param username The username to validate
     * @return true if the username is valid, false otherwise
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }
    
    /**
     * Validates a password.
     * 
     * @param password The password to validate
     * @return true if the password is valid, false otherwise
     */
    public static boolean isValidPassword(String password) {
        // Password must be at least 8 characters long
        return password != null && password.length() >= 8;
    }
    
    /**
     * Validates that a string is not null or empty.
     * 
     * @param str The string to validate
     * @return true if the string is not null or empty, false otherwise
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    /**
     * Sanitizes a string for use in SQL queries.
     * This is a simple implementation and should not be relied upon for complete SQL injection protection.
     * Prepared statements should be used for all database queries.
     * 
     * @param input The string to sanitize
     * @return The sanitized string
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        // Replace single quotes with two single quotes to escape them
        return input.replace("'", "''");
    }
}
