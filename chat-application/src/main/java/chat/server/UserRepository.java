package chat.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

import chat.server.model.User;

public class UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);
    private static final String USERS_FILE = "users.json";
    private Map<String, User> users = new HashMap<>();
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public UserRepository() {
        loadUsers();
    }
    
    private synchronized void loadUsers() {
        try {
            File file = new File(USERS_FILE);
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                Type type = new TypeToken<Map<String, User>>(){}.getType();
                users = gson.fromJson(reader, type);
                if (users == null) {
                    users = new HashMap<>();
                    logger.info("Created new empty user database");
                } else {
                    logger.info("Loaded {} users from file", users.size());
                }
                reader.close();
            } else {
                logger.info("User file not found. New file will be created.");
                users = new HashMap<>();
                saveUsers();
            }
        } catch (IOException e) {
            logger.error("Error loading users: {}", e.getMessage(), e);
            users = new HashMap<>();
        }
    }
    
    private synchronized void saveUsers() {
        try {
            FileWriter writer = new FileWriter(USERS_FILE);
            gson.toJson(users, writer);
            writer.close();
            logger.debug("Saved {} users to file", users.size());
        } catch (IOException e) {
            logger.error("Error saving users: {}", e.getMessage(), e);
        }
    }
    
    public boolean register(String username, String password) {
        if (users.containsKey(username)) {
            logger.warn("Registration failed: user {} already exists", username);
            return false;
        }
        
        String hashedPassword = SecurityUtil.hashPassword(password);
        if (hashedPassword == null) {
            logger.error("Failed to hash password for user {}", username);
            return false;
        }
        
        users.put(username, new User(username, hashedPassword));
        saveUsers();
        logger.info("User {} registered successfully", username);
        return true;
    }
    
    public boolean login(String username, String password) {
        User user = users.get(username);
        if (user == null) {
            logger.warn("Login failed: user {} not found", username);
            return false;
        }
        
        boolean success = SecurityUtil.checkPassword(password, user.getPasswordHash());
        if (!success) {
            logger.warn("Login failed: incorrect password for user {}", username);
        }
        return success;
    }
    
    public synchronized boolean userExists(String username) {
        return users.containsKey(username);
    }
    
    public synchronized int getUserCount() {
        return users.size();
    }
}