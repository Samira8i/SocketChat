package chat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Message implements Serializable {
    public enum Type {
        USER_MESSAGE,    // Обычное сообщение
        JOIN_ROOM,       // Присоединиться к комнате
        CREATE_ROOM,     // Создать комнату
        SYSTEM,          // Системное сообщение
        USER_LIST,       // Список пользователей
        ROOM_LIST        // Список комнат
    }

    private Type type;
    private String username;
    private String content;
    private String room;
    private long timestamp;

    public Message(Type type, String username, String content, String room) {
        this.type = type;
        this.username = (username != null) ? username : "Unknown";
        this.content = (content != null) ? content : "";
        this.room = (room != null) ? room : "🌸 general";
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String username, String content, String room) {
        this(Type.USER_MESSAGE, username, content, room);
    }

    public Message(String content) {
        this(Type.SYSTEM, "🌸 Система", content, "global");
    }

    public Message(Type type, String username, String room) { //сообщение для команд комнат
        this(type, username, "", room);
    }

    public Message(String username, String content) { //сообщение от пользователя
        this(username, content, "🌸 general");
    }
    // Сериализация в байты
    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(type.ordinal());
            writeString(dos, username);
            writeString(dos, content);
            writeString(dos, room);
            dos.writeLong(timestamp);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("🌸 Ошибка сериализации сообщения", e);
        }
    }
    //десериализация
    public Message(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("🌸 Пустые данные сообщения");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {

            int typeOrdinal = dis.readInt();
            if (typeOrdinal < 0 || typeOrdinal >= Type.values().length) {
                throw new IOException("🌸 Некорректный тип сообщения: " + typeOrdinal);
            }

            this.type = Type.values()[typeOrdinal];
            this.username = readString(dis);
            this.content = readString(dis);
            this.room = readString(dis);
            this.timestamp = dis.readLong();
        }
    }

    private void writeString(DataOutputStream dos, String str) throws IOException {
        if (str == null) {
            dos.writeInt(0);
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(bytes.length);
            dos.write(bytes);
        } //записывает длину, потом данные
    }
    //todo: если время останется подробно разобраться
    private String readString(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        if (length == 0) return "";
        if (length < 0 || length > 65536) {
            throw new IOException("🌸 Некорректная длина строки: " + length);
        }
        byte[] bytes = new byte[length];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public Type getType() { return type; }
    public String getUsername() { return username; }
    public String getContent() { return content; }
    public String getMessage() { return content; }
    public String getRoom() { return room; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String time = sdf.format(new Date(timestamp));

        switch (type) {
            case SYSTEM:
                return String.format("🌸 [%s] %s", time, content);
            case USER_MESSAGE:
                return String.format("🌸 [%s][%s] %s: %s", room, time, username, content);
            case JOIN_ROOM:
                return String.format("🌸 [%s] %s присоединился к комнате %s", time, username, room);
            case CREATE_ROOM:
                return String.format("🌸 [%s] %s создал комнату %s", time, username, room);
            default:
                return String.format("🌸 [%s] %s: %s", time, username, content);
        }
    }

    public static Message createSystemMessage(String content) {
        return new Message("🌸 " + content);
    }
}