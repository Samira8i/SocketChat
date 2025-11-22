package chat.io;

import chat.Message;

import java.io.IOException;
import java.io.InputStream;
//декоратор над инпут стримом
public class MessageInputStream extends InputStream {
    private final InputStream inputStream;

    public MessageInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }
    //сначала читаю длину, чтобы потом считать только нужное количество байтов
    public Message readMessage() throws IOException {
        try {
            byte[] lengthBytes = readContent(4);
            if (lengthBytes == null) {
                return null;
            }
            int lengthMessage = byteArrayToInt(lengthBytes);

            if (lengthMessage > 1024) {
                throw new IOException("Сообщение слишком большое");
            }
            byte[] messageData = readContent(lengthMessage);
            if (messageData == null) {
                return null;
            }

            return new Message(messageData);
        } catch (IOException e) {
            throw new IOException("Ошибка чтения сообщения", e);
        }
    }

    private byte[] readContent(int byteCount) throws IOException {
        byte[] buffer = new byte[byteCount];
        int currentRead = 0;

        while (currentRead < byteCount) {
            int bytesRead = inputStream.read(buffer, currentRead, byteCount - currentRead);
            if (bytesRead == -1) {
                return null;
            }
            currentRead += bytesRead;
        }
        return buffer;
    }

    private int byteArrayToInt(byte[] bytes) {
        return (bytes[0] & 0xFF) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }
}