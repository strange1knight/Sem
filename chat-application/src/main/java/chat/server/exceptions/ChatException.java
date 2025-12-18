package chat.server.exceptions;

public class ChatException extends Exception {
    public ChatException(String message) {
        super(message);
    }
    
    public ChatException(String message, Throwable cause) {
        super(message, cause);
    }
}