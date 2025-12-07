package chat.server;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
// каждый объект этого класса представляет одного подключенного клиента на сервере
public class ClientSession {
    public final SocketChannel channel; //Канал для общения с клиентом, через этот канал сервер читает и пишет данные
    public final SelectionKey key; //ключ регистрации в селекторе
    public String username;
    public final Queue<ByteBuffer> writeQueue = new LinkedList<>(); //Очередь на отправку сообщений клиенту
    public final ByteBuffer readBuffer = ByteBuffer.allocate(4096); //уфер для чтения входящих данных

    public int expectedLength = -1;
    public ByteBuffer messageBuffer; //Буфер для сборки полного сообщения
    public String currentRoom = "general";
    public long lastActivity = System.currentTimeMillis();

    public ClientSession(SocketChannel channel, SelectionKey key, String username) {
        this.channel = channel;
        this.key = key;
        this.username = username;
        this.lastActivity = System.currentTimeMillis();
    }

    public void updateActivity() {
        lastActivity = System.currentTimeMillis();
    }

    public boolean isInactive(long timeout) {
        return (System.currentTimeMillis() - lastActivity) > timeout;
    }
}