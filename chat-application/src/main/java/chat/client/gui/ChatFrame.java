package chat.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import chat.client.exceptions.ConnectionException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ChatFrame extends JFrame {
    private chat.client.ClientController controller;
    private String username;
    private JTextArea chatArea;
    private JTextField messageField;
    private JLabel userCountLabel;
    private Thread messageListener;
    private volatile boolean listening = true;
    
    public ChatFrame(chat.client.ClientController controller, String username) {
        this.controller = controller;
        this.username = username;
        setupUI();
        startMessageListener();
    }
    
    private void setupUI() {
        setTitle("Chat - " + username);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel titleLabel = new JLabel("General Chat");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        userCountLabel = new JLabel("Connected: 1");
        headerPanel.add(userCountLabel, BorderLayout.EAST);
        
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> logout());
        headerPanel.add(logoutButton, BorderLayout.CENTER);
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());
        inputPanel.add(messageField, BorderLayout.CENTER);
        
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        messageField.requestFocus();
    }
    
    private void startMessageListener() {
        listening = true;
        messageListener = new Thread(() -> {
            System.out.println("DEBUG: ChatFrame message listener started for user: " + username);
            while (listening && controller != null && controller.isConnected()) {
                try {
                    String message = controller.pollMessage(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    if (message != null && !message.trim().isEmpty()) {
                        System.out.println("DEBUG: Processing message in chat: " + 
                            (message.length() > 100 ? message.substring(0, 100) + "..." : message));
                        processServerMessage(message);
                    }
                    
                    if (!controller.isConnected()) {
                        System.out.println("DEBUG: Controller reports disconnected");
                        break;
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("DEBUG: Message listener interrupted");
                    break;
                } catch (Exception e) {
                    System.err.println("ERROR in message listener: " + e.getMessage());
                    e.printStackTrace();
                    
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            System.out.println("DEBUG: ChatFrame message listener stopped for user: " + username);
            
            if (listening && (controller == null || !controller.isConnected())) {
                SwingUtilities.invokeLater(() -> {
                    int response = JOptionPane.showConfirmDialog(this,
                        "Connection lost with server. Return to login?",
                        "Connection Error",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE);
                    
                    if (response == JOptionPane.YES_OPTION) {
                        logout();
                    }
                });
            }
        });
        messageListener.setName("ChatFrame-MessageListener-" + username);
        messageListener.setDaemon(true);
        messageListener.start();
    }
    
    private void processServerMessage(String jsonMessage) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject json = JsonParser.parseString(jsonMessage).getAsJsonObject();
                String type = json.get("type").getAsString();
                
                switch (type) {
                    case "chat":
                        String sender = json.get("sender").getAsString();
                        String message = json.get("message").getAsString();
                        
                        chatArea.append(sender + ": " + message + "\n");
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        
                        System.out.println("DEBUG: Displayed message from " + sender + 
                            (sender.equals(this.username) ? " (MY MESSAGE)" : "") + ": " + 
                            (message.length() > 50 ? message.substring(0, 50) + "..." : message));
                        break;
                        
                    case "system":
                        String systemMessage = json.get("message").getAsString();
                        chatArea.append("[System] " + systemMessage + "\n");
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        System.out.println("DEBUG: System message: " + systemMessage);
                        break;
                        
                    case "userCount":
                        int count = json.get("count").getAsInt();
                        userCountLabel.setText("Connected: " + count);
                        System.out.println("DEBUG: User count updated: " + count);
                        break;
                        
                    case "loginResponse":
                    case "registerResponse":
                        System.out.println("DEBUG: Ignoring " + type + " in chat window");
                        break;
                        
                    case "heartbeat":
                        System.out.println("DEBUG: Received heartbeat from server");
                        break;
                        
                    default:
                        System.out.println("DEBUG: Unknown message type: " + type);
                        chatArea.append("[Unknown message type: " + type + "]\n");
                }
            } catch (Exception e) {
                System.err.println("ERROR processing message: " + e.getMessage());
                System.err.println("Problematic message: " + jsonMessage);
                e.printStackTrace();
            }
        });
    }
    
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            System.out.println("DEBUG: User " + username + " sending message: " + message);
            
            try {
                controller.sendMessage(message);
                messageField.setText("");
                messageField.requestFocus();
                System.out.println("DEBUG: Message sent successfully from " + username);
            } catch (ConnectionException e) {
                System.err.println("ERROR: Failed to send message: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "Error sending message: " + e.getMessage(),
                        "Send Error",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }
    
    private void logout() {
        System.out.println("DEBUG: User " + username + " logging out");
        listening = false;
        
        if (messageListener != null) {
            messageListener.interrupt();
        }
        
        if (controller != null) {
            controller.logout();
        }
        
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
            dispose();
        });
    }
    
    @Override
    public void dispose() {
        System.out.println("DEBUG: ChatFrame disposing for user: " + username);
        listening = false;
        if (messageListener != null) {
            messageListener.interrupt();
        }
        super.dispose();
    }
}