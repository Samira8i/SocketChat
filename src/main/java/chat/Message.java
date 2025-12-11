package chat;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Message implements Serializable {
    public enum Type {
        TEXT,       // обычное сообщение
        JOIN_ROOM,  // войти в комнату
        CREATE_ROOM, // создать комнату
        SYSTEM      // системное сообщение
    }

    private Type type;
    private String username;
    private String content;
    private String room;

    // конструкторы
    public Message(Type type, String username, String content, String room) {
        this.type = type;
        this.username = (username != null) ? username : "Unknown";
        this.content = (content != null) ? content : "";
        this.room = (room != null) ? room : "general";
    }

    // для обычного сообщения
    public Message(String username, String content, String room) {
        this(Type.TEXT, username, content, room);
    }

    // для входа/создания комнаты
    public Message(Type type, String username, String room) {
        this(type, username, "", room);
    }

    // сериализация в байты
    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(type.ordinal());
            writeString(dos, username);
            writeString(dos, content);
            writeString(dos, room);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("ошибка сериализации", e);
        }
    }

    // десериализация из байтов
    public Message(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {

            int typeOrdinal = dis.readInt();
            this.type = Type.values()[typeOrdinal];
            this.username = readString(dis);
            this.content = readString(dis);
            this.room = readString(dis);
        }
    }

    private void writeString(DataOutputStream dos, String str) throws IOException {
        if (str == null) {
            dos.writeInt(0);
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(bytes.length);
            dos.write(bytes);
        }
    }

    private String readString(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        if (length == 0) return "";
        byte[] bytes = new byte[length];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public Type getType() { return type; }
    public String getUsername() { return username; }
    public String getContent() { return content; }
    public String getRoom() { return room; }

    @Override
    public String toString() {
        if (type == Type.TEXT) {
            return String.format("[%s] %s: %s", room, username, content);
        } else if (type == Type.JOIN_ROOM) {
            return String.format("%s вошел в комнату %s", username, room);
        } else {
            return String.format("%s создал комнату %s", username, room);
        }
    }
}