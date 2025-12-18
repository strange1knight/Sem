package chat.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import chat.client.exceptions.ConnectionException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel statusLabel;
    private chat.client.ClientController controller;
    private JButton loginButton;
    private JButton registerButton;
    
    public LoginFrame() {
        try {
            controller = new chat.client.ClientController();
            setupUI();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error initializing application: " + e.getMessage(),
                "Initialization Error",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void setupUI() {
        setTitle("Chat Application - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 350);
        setLocationRelativeTo(null);
        setResizable(false);
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel titleLabel = new JLabel("Welcome to Chat", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JLabel subtitleLabel = new JLabel("Please login or register", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(subtitleLabel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Login Information"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        usernameField = new JTextField(15);
        inputPanel.add(usernameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        inputPanel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        passwordField = new JPasswordField(15);
        inputPanel.add(passwordField, gbc);
        
        inputPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(inputPanel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        
        loginButton = new JButton("Login");
        loginButton.setPreferredSize(new Dimension(100, 35));
        loginButton.setFont(new Font("Arial", Font.BOLD, 12));
        loginButton.addActionListener(e -> performLogin());
        
        registerButton = new JButton("Register");
        registerButton.setPreferredSize(new Dimension(100, 35));
        registerButton.setFont(new Font("Arial", Font.BOLD, 12));
        registerButton.addActionListener(e -> openRegister());
        
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(buttonPanel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(statusLabel);
        
        add(mainPanel);
        
        passwordField.addActionListener(e -> performLogin());
        
        usernameField.requestFocus();
    }
    
    private void performLogin() {
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);
        
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Please fill all fields", Color.RED);
            loginButton.setEnabled(true);
            registerButton.setEnabled(true);
            return;
        }
        
        showStatus("Connecting...", Color.BLUE);
        
        new Thread(() -> {
            try {
                boolean connected = controller.connect();
                
                if (!connected) {
                    SwingUtilities.invokeLater(() -> {
                        showStatus("Cannot connect to server", Color.RED);
                        loginButton.setEnabled(true);
                        registerButton.setEnabled(true);
                    });
                    return;
                }
                
                SwingUtilities.invokeLater(() -> {
                    showStatus("Authenticating...", Color.BLUE);
                });
                
                String response = controller.login(username, password);
                
                SwingUtilities.invokeLater(() -> {
                    if (response != null && !response.trim().isEmpty()) {
                        System.out.println("Raw login response: " + response);
                        
                        try {
                            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                            
                            if (json.has("type") && json.get("type").getAsString().equals("loginResponse")) {
                                if (json.has("success") && json.has("message")) {
                                    boolean success = json.get("success").getAsBoolean();
                                    String message = json.get("message").getAsString();
                                    
                                    if (success) {
                                        showStatus("Login successful!", new Color(0, 150, 0));
                                        Timer timer = new Timer(1000, e -> {
                                            openChat(username);
                                            dispose();
                                        });
                                        timer.setRepeats(false);
                                        timer.start();
                                    } else {
                                        showStatus("Error: " + message, Color.RED);
                                        loginButton.setEnabled(true);
                                        registerButton.setEnabled(true);
                                    }
                                } else {
                                    showStatus("Invalid response format from server", Color.RED);
                                    loginButton.setEnabled(true);
                                    registerButton.setEnabled(true);
                                }
                            } else {
                                showStatus("Unexpected response type: " + json.toString(), Color.RED);
                                loginButton.setEnabled(true);
                                registerButton.setEnabled(true);
                            }
                        } catch (Exception e) {
                            showStatus("Error parsing response: " + e.getMessage(), Color.RED);
                            System.err.println("Parse error: " + e.getMessage());
                            System.err.println("Response was: " + response);
                            loginButton.setEnabled(true);
                            registerButton.setEnabled(true);
                        }
                    } else {
                        showStatus("No response from server", Color.RED);
                        loginButton.setEnabled(true);
                        registerButton.setEnabled(true);
                    }
                });
                
            } catch (ConnectionException e) {
                SwingUtilities.invokeLater(() -> {
                    showStatus("Connection error: " + e.getMessage(), Color.RED);
                    loginButton.setEnabled(true);
                    registerButton.setEnabled(true);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                SwingUtilities.invokeLater(() -> {
                    showStatus("Login interrupted", Color.RED);
                    loginButton.setEnabled(true);
                    registerButton.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showStatus("Error: " + e.getMessage(), Color.RED);
                    loginButton.setEnabled(true);
                    registerButton.setEnabled(true);
                });
                e.printStackTrace();
            }
        }).start();
    }
    
    private void openRegister() {
        RegisterFrame registerFrame = new RegisterFrame();
        registerFrame.setVisible(true);
        this.dispose();
    }
    
    private void openChat(String username) {
        try {
            ChatFrame chatFrame = new ChatFrame(controller, username);
            chatFrame.setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error opening chat: " + e.getMessage(),
                "Chat Error",
                JOptionPane.ERROR_MESSAGE);
            loginButton.setEnabled(true);
            registerButton.setEnabled(true);
        }
    }
    
    private void showStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }
    
    private void showStatus(String message) {
        showStatus(message, Color.RED);
    }
}