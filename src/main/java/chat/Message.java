package chat;

import java.nio.charset.StandardCharsets;

public class Message {
    private final String username;
    private final String message;
    //тут создала 2 конструктора, один будет работать с байтами, а другой с сообщением и юзернеймом
    public Message(String username, String message) {
        this.username = username;
        this.message = message;

    }
    public Message(byte[] data) {
        String fullMessage = new String(data, StandardCharsets.UTF_8);
        int splitIndex = fullMessage.indexOf(':');
        if (splitIndex != -1) {
            this.username = fullMessage.substring(0, splitIndex);
            this.message = fullMessage.substring(splitIndex + 1);
        } else {
            this.username = "кто-то";
            this.message = fullMessage;
        }
    }

    public byte[] toBytes() {
        String fullMessage = username + ':' + message;
        return fullMessage.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "[" + username + "] " + message;
    }
    public String getUsername() {
        return username;
    }
    public String getMessage() {
        return message;
    }
}
