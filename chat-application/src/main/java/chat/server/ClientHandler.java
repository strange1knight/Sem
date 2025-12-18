package chat.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private Socket socket;
    private ChatServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private boolean authenticated = false;
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private Thread messageSenderThread;
    private volatile boolean running = true;
    private final Object sendLock = new Object();
    private final AtomicBoolean sendingMessage = new AtomicBoolean(false);
    
    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        try {
            in = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8), true);
            
            socket.setSoTimeout(30000);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            
            logger.info("Client handler created for {}", getClientAddress());
        } catch (IOException e) {
            logger.error("Error creating client handler: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void run() {
        logger.info("Starting client handler for {}", getClientAddress());
        
        try {
            startMessageSender();
            
            String message;
            while (running && (message = in.readLine()) != null) {
                if (!message.trim().isEmpty()) {
                    logger.debug("Received from {}: {}", 
                        username != null ? username : getClientAddress(), 
                        message.length() > 100 ? message.substring(0, 100) + "..." : message);
                    processMessage(message);
                }
            }
        } catch (SocketTimeoutException e) {
            logger.warn("Socket timeout for client {}: {}", getClientAddress(), e.getMessage());
        } catch (IOException e) {
            if (running) {
                logger.error("Connection error with client {}: {}", getClientAddress(), e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in client handler {}: {}", getClientAddress(), e.getMessage(), e);
        } finally {
            close();
        }
    }
    
    private void startMessageSender() {
        messageSenderThread = new Thread(() -> {
            try {
                while (running) {
                    String message = messageQueue.take();
                    sendMessageDirectly(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Message sender interrupted for {}", getClientAddress());
            } catch (Exception e) {
                if (running) {
                    logger.error("Error in message sender for {}: {}", getClientAddress(), e.getMessage(), e);
                }
            }
        });
        messageSenderThread.setDaemon(true);
        messageSenderThread.setName("MessageSender-" + getClientAddress());
        messageSenderThread.start();
    }
    
    private void sendMessageDirectly(String message) {
        synchronized (sendLock) {
            if (out != null && running && !sendingMessage.get()) {
                sendingMessage.set(true);
                try {
                    out.println(message);
                    out.flush();
                    
                    if (out.checkError()) {
                        logger.error("Error detected in PrintWriter for {}", getClientAddress());
                        close();
                        return;
                    }
                    
                    logger.debug("Successfully sent message to {}: {}", 
                        username != null ? username : getClientAddress(), 
                        message.length() > 100 ? message.substring(0, 100) + "..." : message);
                } catch (Exception e) {
                    logger.error("Failed to send message to {}: {}", getClientAddress(), e.getMessage());
                    if (e instanceof IllegalStateException) {
                        close();
                    }
                } finally {
                    sendingMessage.set(false);
                }
            }
        }
    }
    
    private void processMessage(String jsonMessage) {
        try {
            JsonObject json = JsonParser.parseString(jsonMessage).getAsJsonObject();
            if (!json.has("type")) {
                logger.warn("Invalid message format from {}: no type field", getClientAddress());
                return;
            }
            
            String type = json.get("type").getAsString();
            logger.debug("Processing message type '{}' from {}", type, getClientAddress());
            
            switch (type) {
                case "login":
                    handleLogin(json);
                    break;
                case "register":
                    handleRegister(json);
                    break;
                case "message":
                    handleMessage(json);
                    break;
                case "logout":
                    logger.info("User {} requested logout", username != null ? username : getClientAddress());
                    close();
                    break;
                case "heartbeat":
                    logger.trace("Received heartbeat from {}", 
                        username != null ? username : getClientAddress());
                    JsonObject heartbeatResponse = new JsonObject();
                    heartbeatResponse.addProperty("type", "heartbeat");
                    heartbeatResponse.addProperty("status", "ok");
                    sendMessageDirectly(heartbeatResponse.toString());
                    break;
                default:
                    logger.warn("Unknown message type '{}' from {}", type, getClientAddress());
            }
        } catch (Exception e) {
            logger.error("Error processing message from {}: {}", getClientAddress(), e.getMessage(), e);
        }
    }
    
    private void handleLogin(JsonObject json) {
        if (!json.has("username") || !json.has("password")) {
            sendErrorResponse("loginResponse", "Invalid login request format");
            return;
        }
        
        String username = json.get("username").getAsString();
        String password = json.get("password").getAsString();
        
        logger.info("Processing login request for user {} from {}", username, getClientAddress());
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "loginResponse");
        
        try {
            if (server.isUserAlreadyLoggedIn(username)) {
                response.addProperty("success", false);
                response.addProperty("message", "User " + username + " is already logged in from another location");
                logger.warn("User {} already logged in. Rejecting login from {}", 
                    username, getClientAddress());
                sendMessageDirectly(response.toString());
                return;
            }
            
            if (server.loginUser(username, password)) {
                this.username = username;
                this.authenticated = true;
                
                if (server.addLoggedInUser(username, this)) {
                    response.addProperty("success", true);
                    response.addProperty("message", "Login successful");
                    logger.info("User {} successfully logged in from {}", username, getClientAddress());
                    
                    sendMessageDirectly(response.toString());
                    
                    JsonObject welcome = new JsonObject();
                    welcome.addProperty("type", "system");
                    welcome.addProperty("message", username + " joined the chat");
                    server.broadcastToAuthenticated(welcome.toString(), this);
                    
                    server.updateUserCount();
                    
                    JsonObject userCount = new JsonObject();
                    userCount.addProperty("type", "userCount");
                    userCount.addProperty("count", server.getConnectedUserCount());
                    sendMessageDirectly(userCount.toString());
                    
                    logger.debug("Login process completed for {}", username);
                    return;
                } else {
                    response.addProperty("success", false);
                    response.addProperty("message", "Login failed - could not add user to session");
                    logger.error("Failed to add user {} to logged in users", username);
                }
            } else {
                response.addProperty("success", false);
                response.addProperty("message", "Invalid username or password");
                logger.warn("Failed login attempt for user {} from {}", username, getClientAddress());
            }
        } catch (Exception e) {
            response.addProperty("success", false);
            response.addProperty("message", "Server error: " + e.getMessage());
            logger.error("Error during login for {}: {}", username, e.getMessage(), e);
        }
        
        sendMessageDirectly(response.toString());
    }
    
    private void handleRegister(JsonObject json) {
        if (!json.has("username") || !json.has("password")) {
            sendErrorResponse("registerResponse", "Invalid registration request format");
            return;
        }
        
        String username = json.get("username").getAsString();
        String password = json.get("password").getAsString();
        
        logger.info("Processing registration request for user {} from {}", username, getClientAddress());
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "registerResponse");
        
        try {
            if (server.registerUser(username, password)) {
                response.addProperty("success", true);
                response.addProperty("message", "Registration successful");
                logger.info("New user registered: {} from {}", username, getClientAddress());
            } else {
                response.addProperty("success", false);
                response.addProperty("message", "Username already exists");
                logger.warn("Failed registration attempt for user {} from {}", username, getClientAddress());
            }
        } catch (Exception e) {
            response.addProperty("success", false);
            response.addProperty("message", "Server error: " + e.getMessage());
            logger.error("Error during registration for {}: {}", username, e.getMessage(), e);
        }
        
        sendMessageDirectly(response.toString());
    }
    
    private void handleMessage(JsonObject json) {
        if (!authenticated || username == null) {
            logger.warn("Unauthorized message attempt from {}", getClientAddress());
            sendErrorResponse("error", "You must be logged in to send messages");
            return;
        }
        
        if (!json.has("message")) {
            logger.warn("Invalid message format from {}", username);
            return;
        }
        
        String message = json.get("message").getAsString();
        
        if (message.trim().isEmpty()) {
            return;
        }
        
        if (message.length() > 1000) {
            message = message.substring(0, 1000) + "... [trimmed]";
        }
        
        JsonObject chatMessage = new JsonObject();
        chatMessage.addProperty("type", "chat");
        chatMessage.addProperty("sender", username);
        chatMessage.addProperty("message", message);
        chatMessage.addProperty("timestamp", System.currentTimeMillis());
        
        String messageJson = chatMessage.toString();
        
        server.broadcastToAuthenticated(messageJson);
        
        logger.info("Message from '{}' broadcasted to {} authenticated users (including sender)", 
            username, server.getConnectedUserCount());
    }
    
    private void sendErrorResponse(String type, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("type", type);
        error.addProperty("success", false);
        error.addProperty("message", message);
        sendMessageDirectly(error.toString());
    }
    
    public void sendMessage(String message) {
        if (running && authenticated && !sendingMessage.get()) {
            try {
                boolean added = messageQueue.offer(message, 100, TimeUnit.MILLISECONDS);
                if (!added) {
                    logger.warn("Message queue full for user {}", username);
                    sendMessageDirectly(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Interrupted while queuing message for {}", username);
            }
        }
    }
    
    public String getUsername() {
        return username;
    }
    
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    private String getClientAddress() {
        if (socket == null || socket.isClosed()) {
            return "disconnected";
        }
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }
    
    public void close() {
        if (!running) {
            return;
        }
        
        running = false;
        
        if (messageSenderThread != null) {
            messageSenderThread.interrupt();
        }
        
        messageQueue.clear();
        
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing connection for {}: {}", getClientAddress(), e.getMessage());
        }
        
        if (authenticated && username != null) {
            server.removeLoggedInUser(username, this);
            
            logger.info("User {} disconnected from {}", username, getClientAddress());
            
            JsonObject leaveMessage = new JsonObject();
            leaveMessage.addProperty("type", "system");
            leaveMessage.addProperty("message", username + " left the chat");
            server.broadcastToAuthenticated(leaveMessage.toString(), this);
            
            server.updateUserCount();
        }
        
        server.removeClient(this);
        
        logger.info("Client handler closed for {}", getClientAddress());
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public Socket getSocket() {
        return socket;
    }
}