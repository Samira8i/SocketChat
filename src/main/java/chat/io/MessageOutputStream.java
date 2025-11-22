package chat.io;
//делаю декоратор над OutputStream аналогично как в вашем примере
import chat.Message;

import java.io.IOException;
import java.io.OutputStream;

public class MessageOutputStream extends OutputStream {
    private final OutputStream outputStream;

    public MessageOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }
    //не самый нужный метод в нашем случае, но раз обязательный, то пусть будет
    @Override
    public void write(int b) throws IOException {
        outputStream.write(b);
    }

    public void writeMessage(Message message) throws IOException {
        byte[] messageData = message.toBytes();
        //сначала длину сообщения пишу, а потом уже само сообщение
        byte[] lengthBytes = intToByteArray(messageData.length);
        outputStream.write(lengthBytes);
        outputStream.write(messageData);
        outputStream.flush();
    }

    private byte[] intToByteArray(int value) {
        return new byte[] {
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }
}
