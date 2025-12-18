package chat.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import chat.client.exceptions.ConnectionException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class RegisterFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private JLabel statusLabel;
    private chat.client.ClientController controller;
    private JButton registerButton;
    private JButton backButton;
    
    public RegisterFrame() {
        setupUI();
        controller = new chat.client.ClientController();
    }
    
    private void setupUI() {
        setTitle("Chat Application - Register");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(400, 400);
        setLocationRelativeTo(null);
        setResizable(false);
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel titleLabel = new JLabel("Create Account", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JLabel subtitleLabel = new JLabel("Please fill in all fields", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(subtitleLabel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Registration Details"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel usernameLabel = new JLabel("Username:");
        usernameLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        inputPanel.add(usernameLabel, gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        usernameField = new JTextField(15);
        usernameField.setFont(new Font("Arial", Font.PLAIN, 12));
        inputPanel.add(usernameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        inputPanel.add(passwordLabel, gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        passwordField = new JPasswordField(15);
        passwordField.setFont(new Font("Arial", Font.PLAIN, 12));
        inputPanel.add(passwordField, gbc);
        
        gbc.gridx = 2;
        gbc.gridy = 1;
        JLabel passwordHint = new JLabel("(min 6 chars)");
        passwordHint.setFont(new Font("Arial", Font.ITALIC, 10));
        passwordHint.setForeground(Color.GRAY);
        inputPanel.add(passwordHint, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        JLabel confirmLabel = new JLabel("Confirm:");
        confirmLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        inputPanel.add(confirmLabel, gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 2;
        confirmPasswordField = new JPasswordField(15);
        confirmPasswordField.setFont(new Font("Arial", Font.PLAIN, 12));
        inputPanel.add(confirmPasswordField, gbc);
        
        gbc.gridx = 2;
        gbc.gridy = 2;
        JLabel confirmHint = new JLabel("(re-enter)");
        confirmHint.setFont(new Font("Arial", Font.ITALIC, 10));
        confirmHint.setForeground(Color.GRAY);
        inputPanel.add(confirmHint, gbc);
        
        inputPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(inputPanel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        
        registerButton = new JButton("Register");
        registerButton.setPreferredSize(new Dimension(100, 35));
        registerButton.setFont(new Font("Arial", Font.BOLD, 12));
        registerButton.addActionListener(e -> performRegister());
        
        backButton = new JButton("Back to Login");
        backButton.setPreferredSize(new Dimension(120, 35));
        backButton.setFont(new Font("Arial", Font.PLAIN, 12));
        backButton.addActionListener(e -> goBack());
        
        buttonPanel.add(registerButton);
        buttonPanel.add(backButton);
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(buttonPanel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(statusLabel);
        
        confirmPasswordField.addActionListener(e -> performRegister());
        
        add(mainPanel);
        
        usernameField.requestFocus();
    }
    
    private void performRegister() {
        registerButton.setEnabled(false);
        backButton.setEnabled(false);
        
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());
        
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showStatus("Please fill all fields", Color.RED);
            registerButton.setEnabled(true);
            backButton.setEnabled(true);
            return;
        }
        
        if (username.length() < 3) {
            showStatus("Username must be at least 3 characters", Color.RED);
            registerButton.setEnabled(true);
            backButton.setEnabled(true);
            return;
        }
        
        if (password.length() < 6) {
            showStatus("Password must be at least 6 characters", Color.RED);
            registerButton.setEnabled(true);
            backButton.setEnabled(true);
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            showStatus("Passwords do not match", Color.RED);
            registerButton.setEnabled(true);
            backButton.setEnabled(true);
            return;
        }
        
        showStatus("Connecting to server...", Color.BLUE);
        
        new Thread(() -> {
            try {
                controller.clearMessageQueue();
                
                boolean connected = controller.connect();
                
                if (!connected) {
                    SwingUtilities.invokeLater(() -> {
                        showStatus("Cannot connect to server", Color.RED);
                        registerButton.setEnabled(true);
                        backButton.setEnabled(true);
                    });
                    return;
                }
                
                SwingUtilities.invokeLater(() -> {
                    showStatus("Registering account...", Color.BLUE);
                });
                
                Thread.sleep(500);
                
                String response = controller.register(username, password);
                
                SwingUtilities.invokeLater(() -> {
                    if (response != null && !response.trim().isEmpty()) {
                        try {
                            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                            
                            String responseType = json.has("type") ? json.get("type").getAsString() : "";
                            
                            if ("registerResponse".equals(responseType)) {
                                if (json.has("success") && json.has("message")) {
                                    boolean success = json.get("success").getAsBoolean();
                                    String message = json.get("message").getAsString();
                                    
                                    if (success) {
                                        showStatus("Registration successful! Returning to login...", 
                                                  new Color(0, 150, 0));
                                        
                                        Timer timer = new Timer(2000, e -> {
                                            goBack();
                                        });
                                        timer.setRepeats(false);
                                        timer.start();
                                    } else {
                                        showStatus("Error: " + message, Color.RED);
                                        registerButton.setEnabled(true);
                                        backButton.setEnabled(true);
                                    }
                                } else {
                                    showStatus("Invalid server response format", Color.RED);
                                    registerButton.setEnabled(true);
                                    backButton.setEnabled(true);
                                }
                            } else {
                                showStatus("Unexpected response type: " + responseType, Color.RED);
                                registerButton.setEnabled(true);
                                backButton.setEnabled(true);
                            }
                        } catch (Exception e) {
                            showStatus("Error parsing server response", Color.RED);
                            System.err.println("Parse error: " + e.getMessage());
                            System.err.println("Response was: " + response);
                            registerButton.setEnabled(true);
                            backButton.setEnabled(true);
                        }
                    } else {
                        showStatus("No response from server", Color.RED);
                        registerButton.setEnabled(true);
                        backButton.setEnabled(true);
                    }
                });
                
            } catch (ConnectionException e) {
                SwingUtilities.invokeLater(() -> {
                    showStatus("Connection error: " + e.getMessage(), Color.RED);
                    registerButton.setEnabled(true);
                    backButton.setEnabled(true);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                SwingUtilities.invokeLater(() -> {
                    showStatus("Registration interrupted", Color.RED);
                    registerButton.setEnabled(true);
                    backButton.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showStatus("Error: " + e.getMessage(), Color.RED);
                    registerButton.setEnabled(true);
                    backButton.setEnabled(true);
                });
                e.printStackTrace();
            }
        }).start();
    }
    
    private void goBack() {
        LoginFrame loginFrame = new LoginFrame();
        loginFrame.setVisible(true);
        this.dispose();
    }
    
    private void showStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }
    
    private void showStatus(String message) {
        showStatus(message, Color.RED);
    }
    
    public void clearFields() {
        usernameField.setText("");
        passwordField.setText("");
        confirmPasswordField.setText("");
        statusLabel.setText(" ");
        registerButton.setEnabled(true);
        backButton.setEnabled(true);
        usernameField.requestFocus();
    }
}