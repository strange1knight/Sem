package chat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import chat.client.exceptions.ConnectionException;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientController {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String serverAddress = "localhost";
    private int serverPort = 8080;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(100);
    private Thread messageReaderThread;
    private Thread keepAliveThread;
    private final Object connectionLock = new Object();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean keepAliveRunning = new AtomicBoolean(false);
    
    public ClientController() {
    }
    
    public ClientController(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }
    
    public boolean connect() throws ConnectionException {
        synchronized (connectionLock) {
            if (connected.get() && socket != null && !socket.isClosed() && socket.isConnected()) {
                return true;
            }
            
            try {
                socket = new Socket();
                socket.setSoTimeout(30000);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                socket.setReuseAddress(true);
                
                socket.connect(new InetSocketAddress(serverAddress, serverPort), 5000);
                
                in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), "UTF-8"), true);
                
                connected.set(true);
                shutdown.set(false);
                
                messageQueue.clear();
                
                startMessageReader();
                startKeepAlive();
                
                System.out.println("DEBUG: Successfully connected to server " + 
                    serverAddress + ":" + serverPort);
                return true;
            } catch (SocketTimeoutException e) {
                connected.set(false);
                throw new ConnectionException("Connection timeout to server: " + e.getMessage());
            } catch (ConnectException e) {
                connected.set(false);
                throw new ConnectionException("Cannot connect to server. Make sure server is running on port " + serverPort);
            } catch (IOException e) {
                connected.set(false);
                throw new ConnectionException("Cannot connect to server: " + e.getMessage());
            }
        }
    }
    
    private void startMessageReader() {
        if (messageReaderThread != null && messageReaderThread.isAlive()) {
            messageReaderThread.interrupt();
            try {
                messageReaderThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        messageReaderThread = new Thread(() -> {
            System.out.println("DEBUG: Message reader thread started");
            try {
                String message;
                while (connected.get() && !shutdown.get() && socket != null && !socket.isClosed()) {
                    try {
                        message = in.readLine();
                        if (message == null) {
                            System.out.println("DEBUG: Server closed connection");
                            break;
                        }
                        
                        if (!message.trim().isEmpty()) {
                            System.out.println("DEBUG: Received from server: " + 
                                (message.length() > 100 ? message.substring(0, 100) + "..." : message));
                            
                            boolean added = messageQueue.offer(message, 100, TimeUnit.MILLISECONDS);
                            if (!added) {
                                System.err.println("WARN: Message queue full, dropping message");
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        continue;
                    } catch (IOException e) {
                        if (connected.get() && !shutdown.get()) {
                            System.err.println("DEBUG: Connection lost in reader: " + e.getMessage());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("ERROR in message reader: " + e.getMessage());
            } finally {
                connected.set(false);
                System.out.println("DEBUG: Message reader thread stopped");
            }
        });
        messageReaderThread.setName("ClientController-MessageReader");
        messageReaderThread.setDaemon(true);
        messageReaderThread.start();
    }
    
    private void startKeepAlive() {
        if (keepAliveThread != null && keepAliveThread.isAlive()) {
            keepAliveThread.interrupt();
        }
        
        keepAliveRunning.set(true);
        keepAliveThread = new Thread(() -> {
            System.out.println("DEBUG: Keep-alive thread started");
            while (keepAliveRunning.get() && connected.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(15000);
                    
                    if (isConnected()) {
                        synchronized (connectionLock) {
                            if (out != null) {
                                JsonObject heartbeat = new JsonObject();
                                heartbeat.addProperty("type", "heartbeat");
                                heartbeat.addProperty("timestamp", System.currentTimeMillis());
                                
                                out.println(heartbeat.toString());
                                out.flush();
                                System.out.println("DEBUG: Sent heartbeat to server");
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("DEBUG: Heartbeat error: " + e.getMessage());
                    if (e instanceof IllegalStateException) {
                        break;
                    }
                }
            }
            System.out.println("DEBUG: Keep-alive thread stopped");
        });
        keepAliveThread.setName("ClientController-KeepAlive");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
    }
    
    public String login(String username, String password) throws ConnectionException, InterruptedException {
        if (!connect()) {
            throw new ConnectionException("Not connected to server");
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("type", "login");
        json.addProperty("username", username);
        json.addProperty("password", password);
        
        System.out.println("DEBUG: Sending login request for user: " + username);
        out.println(json.toString());
        out.flush();
        
        if (out.checkError()) {
            throw new ConnectionException("Error sending login request");
        }
        
        return waitForSpecificResponse("loginResponse", 10);
    }
    
    public String register(String username, String password) throws ConnectionException, InterruptedException {
        if (!connect()) {
            throw new ConnectionException("Not connected to server");
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("type", "register");
        json.addProperty("username", username);
        json.addProperty("password", password);
        
        System.out.println("DEBUG: Sending register request for user: " + username);
        out.println(json.toString());
        out.flush();
        
        if (out.checkError()) {
            throw new ConnectionException("Error sending register request");
        }
        
        return waitForSpecificResponse("registerResponse", 10);
    }
    
    private String waitForSpecificResponse(String expectedType, int timeoutSeconds) 
            throws InterruptedException, ConnectionException {
        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000);
        long pollTimeout = 1000;
        
        System.out.println("DEBUG: Waiting for " + expectedType + " response");
        
        while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            String response = messageQueue.poll(pollTimeout, TimeUnit.MILLISECONDS);
            
            if (response != null && !response.trim().isEmpty()) {
                try {
                    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                    if (json.has("type")) {
                        String responseType = json.get("type").getAsString();
                        System.out.println("DEBUG: Received response type: " + responseType);
                        
                        if (responseType.equals(expectedType)) {
                            System.out.println("DEBUG: Found matching " + expectedType + " response");
                            return response;
                        } else if (responseType.equals("heartbeat")) {
                            continue;
                        } else {
                            messageQueue.put(response);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("ERROR parsing response: " + e.getMessage());
                    System.err.println("Raw response: " + response);
                    return response;
                }
            }
            
            if (!isConnected()) {
                throw new ConnectionException("Connection lost while waiting for " + expectedType);
            }
            
            pollTimeout = Math.max(100, pollTimeout / 2);
        }
        
        throw new ConnectionException("Timeout waiting for " + expectedType + " after " + timeoutSeconds + " seconds");
    }
    
    public void sendMessage(String message) throws ConnectionException {
        if (!isConnected()) {
            throw new ConnectionException("Not connected to server");
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("type", "message");
        json.addProperty("message", message);
        
        synchronized (connectionLock) {
            if (out != null) {
                out.println(json.toString());
                out.flush();
                
                if (out.checkError()) {
                    throw new ConnectionException("Error sending message to server");
                }
                
                System.out.println("DEBUG: Message sent to server: " + 
                    (message.length() > 50 ? message.substring(0, 50) + "..." : message));
            }
        }
    }
    
    public void sendLogout() {
        if (out != null && isConnected()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "logout");
            
            synchronized (connectionLock) {
                out.println(json.toString());
                out.flush();
                System.out.println("DEBUG: Logout request sent to server");
            }
        }
    }
    
    public void logout() {
        System.out.println("DEBUG: Logging out...");
        sendLogout();
        disconnect();
    }
    
    public void disconnect() {
        System.out.println("DEBUG: Disconnecting from server...");
        
        keepAliveRunning.set(false);
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
        }
        
        shutdown.set(true);
        synchronized (connectionLock) {
            connected.set(false);
            
            try {
                if (messageReaderThread != null) {
                    messageReaderThread.interrupt();
                    try {
                        messageReaderThread.join(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                if (out != null) {
                    out.close();
                }
                
                if (in != null) {
                    in.close();
                }
                
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                
                System.out.println("DEBUG: Disconnected from server");
            } catch (IOException e) {
                System.err.println("ERROR during disconnect: " + e.getMessage());
            } finally {
                messageQueue.clear();
                in = null;
                out = null;
                socket = null;
            }
        }
    }
    
    public boolean isConnected() {
        synchronized (connectionLock) {
            return connected.get() && 
                   socket != null && 
                   !socket.isClosed() && 
                   socket.isConnected() &&
                   !shutdown.get();
        }
    }
    
    public String getNextMessage() throws InterruptedException {
        return messageQueue.take();
    }
    
    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }
    
    public String pollMessage(long timeoutMillis) throws InterruptedException {
        return messageQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }
    
    public boolean hasMessages() {
        return !messageQueue.isEmpty();
    }
    
    public void clearMessageQueue() {
        messageQueue.clear();
    }
    
    public void setServerAddress(String address) {
        this.serverAddress = address;
    }
    
    public void setServerPort(int port) {
        this.serverPort = port;
    }
    
    public String getServerAddress() {
        return serverAddress;
    }
    
    public int getServerPort() {
        return serverPort;
    }
}