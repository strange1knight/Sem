package chat.server;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ClientHandler> loggedInUsers = new ConcurrentHashMap<>();
    private UserRepository userRepository;
    private int port;
    private Thread serverThread;
    
    public ChatServer() {
        loadConfig();
        userRepository = new UserRepository();
    }
    
    private void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                prop.load(input);
                port = Integer.parseInt(prop.getProperty("server.port", "8080"));
                logger.info("Server configuration loaded. Port: {}", port);
            } else {
                port = 8080;
                logger.warn("Configuration file not found. Using default port: {}", port);
            }
        } catch (Exception e) {
            port = 8080;
            logger.error("Error loading configuration: {}", e.getMessage(), e);
        }
    }
    
    public void start() {
        if (running) {
            logger.warn("Server is already running");
            return;
        }
        
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            running = true;
            logger.info("Chat server started on port {}", port);
            
            serverThread = new Thread(this::acceptConnections);
            serverThread.setName("ChatServer-Acceptor");
            serverThread.start();
            
            startConnectionMonitor();
            
        } catch (IOException e) {
            logger.error("Error starting server on port {}: {}", port, e.getMessage(), e);
            running = false;
        }
    }
    
    private void acceptConnections() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(30000);
                clientSocket.setKeepAlive(true);
                clientSocket.setTcpNoDelay(true);
                
                String clientAddress = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                logger.info("New connection from: {}", clientAddress);
                
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                addClient(clientHandler);
                
                Thread clientThread = new Thread(clientHandler);
                clientThread.setName("ClientHandler-" + clientAddress);
                clientThread.setDaemon(true);
                clientThread.start();
                
            } catch (SocketException e) {
                if (running) {
                    logger.info("Server socket closed: {}", e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting connection: {}", e.getMessage(), e);
                }
            }
        }
    }
    
    private void startConnectionMonitor() {
        Thread monitorThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(30000);
                    
                    int totalClients = clients.size();
                    int authenticatedUsers = loggedInUsers.size();
                    
                    logger.debug("Server status - Total clients: {}, Authenticated users: {}", 
                                totalClients, authenticatedUsers);
                    
                    cleanupInactiveClients();
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitorThread.setName("ConnectionMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    private void cleanupInactiveClients() {
        synchronized (clients) {
            Iterator<ClientHandler> iterator = clients.iterator();
            while (iterator.hasNext()) {
                ClientHandler client = iterator.next();
                if (!client.isRunning()) {
                    iterator.remove();
                    logger.debug("Removed inactive client handler");
                }
            }
        }
    }
    
    public synchronized void addClient(ClientHandler client) {
        clients.add(client);
        logger.debug("Client added. Total clients: {}", clients.size());
    }
    
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        logger.debug("Client removed. Total clients: {}", clients.size());
    }
    
    public void broadcastMessageToAll(String messageJson) {
        List<ClientHandler> clientsCopy;
        synchronized (clients) {
            clientsCopy = new ArrayList<>(clients);
        }
        
        for (ClientHandler client : clientsCopy) {
            try {
                client.sendMessage(messageJson);
            } catch (Exception e) {
                logger.error("Error broadcasting to client: {}", e.getMessage(), e);
            }
        }
    }
    
    public void broadcastToAuthenticated(String messageJson) {
        broadcastToAuthenticated(messageJson, null);
    }
    
    public void broadcastToAuthenticated(String messageJson, ClientHandler exclude) {
        List<ClientHandler> clientsCopy;
        synchronized (clients) {
            clientsCopy = new ArrayList<>(clients);
        }
        
        int sentCount = 0;
        int failedCount = 0;
        
        for (ClientHandler client : clientsCopy) {
            if (client.isAuthenticated()) {
                if (exclude != null && client == exclude) {
                    continue;
                }
                
                try {
                    client.sendMessage(messageJson);
                    sentCount++;
                    logger.debug("Broadcast message sent to user: {}", 
                        client.getUsername() != null ? client.getUsername() : "unknown");
                } catch (Exception e) {
                    failedCount++;
                    logger.error("Error broadcasting to client {}: {}", 
                        client.getUsername() != null ? client.getUsername() : "unknown", 
                        e.getMessage());
                    
                    if (e instanceof IllegalStateException) {
                        client.close();
                    }
                }
            }
        }
        
        logger.debug("Broadcast completed: sent to {} clients, failed for {} clients", 
            sentCount, failedCount);
       
        if (exclude != null && exclude.isAuthenticated() && 
            messageJson.contains("\"type\":\"chat\"")) {
            try {
                exclude.sendMessage(messageJson);
                logger.debug("Successfully sent message to excluded client (sender): {}", 
                    exclude.getUsername());
            } catch (Exception e) {
                logger.error("Failed to send to excluded client {}: {}", 
                    exclude.getUsername(), e.getMessage());
            }
        }
    }
    
    public boolean isUserAlreadyLoggedIn(String username) {
        boolean isLoggedIn = loggedInUsers.containsKey(username);
        logger.debug("Check if user {} is already logged in: {}", username, isLoggedIn);
        return isLoggedIn;
    }
    
    public synchronized boolean addLoggedInUser(String username, ClientHandler client) {
        if (loggedInUsers.containsKey(username)) {
            ClientHandler existingClient = loggedInUsers.get(username);
            if (existingClient != null && !existingClient.isRunning()) {
                loggedInUsers.remove(username);
                logger.warn("Removed inactive user {} before adding new connection", username);
            } else {
                logger.warn("Cannot add user {}: already logged in from {}", 
                    username, existingClient != null ? existingClient.getSocket().getInetAddress() : "unknown");
                return false;
            }
        }
        
        loggedInUsers.put(username, client);
        logger.info("User {} successfully added to logged in users. Total users: {}", 
            username, loggedInUsers.size());
        return true;
    }
    
    public synchronized void removeLoggedInUser(String username, ClientHandler client) {
        ClientHandler storedClient = loggedInUsers.get(username);
        if (storedClient != null && storedClient == client) {
            loggedInUsers.remove(username);
            logger.info("User {} removed from logged in users. Remaining: {}", 
                username, loggedInUsers.size());
        } else if (storedClient != null) {
            logger.warn("Attempt to remove user {} with different client handler. " +
                       "Stored: {}, Requested: {}", 
                       username, storedClient, client);
        } else {
            logger.debug("User {} not found in logged in users during removal", username);
        }
    }
    
    public void updateUserCount() {
        int count = loggedInUsers.size();
        JsonObject countMessage = new JsonObject();
        countMessage.addProperty("type", "userCount");
        countMessage.addProperty("count", count);
        
        String messageJson = countMessage.toString();
        
        List<ClientHandler> clientsCopy;
        synchronized (clients) {
            clientsCopy = new ArrayList<>(clients);
        }
        
        for (ClientHandler client : clientsCopy) {
            if (client.isAuthenticated()) {
                try {
                    client.sendMessage(messageJson);
                } catch (Exception e) {
                    logger.error("Error sending user count to {}: {}", 
                        client.getUsername(), e.getMessage());
                }
            }
        }
        
        logger.info("Updated connected users count: {}", count);
    }
    
    public boolean registerUser(String username, String password) {
        boolean result = userRepository.register(username, password);
        logger.info("Registration for user {}: {}", username, result ? "successful" : "failed");
        return result;
    }
    
    public boolean loginUser(String username, String password) {
        boolean result = userRepository.login(username, password);
        logger.info("Login attempt for user {}: {}", username, result ? "successful" : "failed");
        return result;
    }
    
    public void stop() {
        if (!running) {
            return;
        }
        
        logger.info("Stopping chat server...");
        running = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            synchronized (clients) {
                logger.info("Closing {} client connections...", clients.size());
                for (ClientHandler client : new ArrayList<>(clients)) {
                    client.close();
                }
                clients.clear();
            }
            
            loggedInUsers.clear();
            
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.join(5000);
            }
            
            logger.info("Server stopped successfully");
            
        } catch (IOException e) {
            logger.error("Error stopping server: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Server stop interrupted");
        }
    }
    
    public int getConnectedUserCount() {
        return loggedInUsers.size();
    }
    
    public List<String> getLoggedInUsernames() {
        return new ArrayList<>(loggedInUsers.keySet());
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in thread {}: {}", thread.getName(), 
                       throwable.getMessage(), throwable);
        });
        
        ChatServer server = new ChatServer();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered");
            server.stop();
        }));
        
        server.start();
        
        try {
            if (server.isRunning()) {
                synchronized (server) {
                    while (server.isRunning()) {
                        server.wait(1000);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Server main thread interrupted");
        }
        
        logger.info("Chat server application exiting");
    }
}