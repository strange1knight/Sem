package chat.client;

import chat.client.gui.LoginFrame;

public class ChatClient {
    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        java.nio.charset.Charset.defaultCharset();
        
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("File encoding: " + System.getProperty("file.encoding"));
        System.out.println("Default charset: " + java.nio.charset.Charset.defaultCharset().name());
        
        javax.swing.SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}