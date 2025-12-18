package chat.server.exceptions;

public class UserExistsException extends ChatException {
    public UserExistsException(String username) {
        super("User " + username + " already exists");
    }
    
    public UserExistsException(String username, Throwable cause) {
        super("User " + username + " already exists", cause);
    }
}