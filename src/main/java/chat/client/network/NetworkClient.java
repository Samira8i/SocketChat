package chat.client.network;

import chat.Message;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NetworkClient {
    private SocketChannel socketChannel; // личный канал связи с сервером
    private Selector selector; // следит за событиями
    private boolean connected = false; //
    private String username;

    private MessageListener listener;

    public interface MessageListener {
        void onMessage(Message message); // когда пришло сообщение
        void onStatusChanged(boolean connected); // когда изменился статус подключения
    }

    // кто будет слушать сообщения
    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }

    // подключаюсь к серверу
    public void connect(String host, int port, String username) throws IOException {
        this.username = username; // сохраняю свое имя

        selector = Selector.open(); // создаю наблюдателя
        socketChannel = SocketChannel.open(); // открываю свой канал
        socketChannel.configureBlocking(false); // делаю его неблокирующим

        // начинаю подключение
        socketChannel.connect(new InetSocketAddress(host, port));
        // прошу наблюдателя следить за завершением подключения
        socketChannel.register(selector, SelectionKey.OP_CONNECT);

        // запускаю отдельный поток для сетевого общения
        new Thread(this::networkLoop, "network-client").start();
    }

    // главный цикл сетевого общения
    private void networkLoop() {
        try {
            // пока мой канал открыт
            while (socketChannel.isOpen()) {
                selector.select(100); // проверяю события каждые 100мс
                Set<SelectionKey> keys = selector.selectedKeys(); // получаю список событий
                Iterator<SelectionKey> it = keys.iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove(); // убираю обработанное

                    if (key.isConnectable()) {
                        handleConnect(key); // подключение
                    } else if (key.isReadable()) {
                        handleRead(key); // пришли данные от сервера
                    }
                }
            }
        } catch (Exception e) {
            // если была ошибка и я был подключен - уведомляю об отключении
            if (connected) {
                notifyStatus(false);
            }
        }
    }

    // обработка завершения подключения
    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        if (channel.finishConnect()) { // завершаю подключение
            connected = true; // ставлю флажок "подключен"
            key.interestOps(SelectionKey.OP_READ); // теперь слежу за чтением
            notifyStatus(true); // сообщаю, что подключился

            // отправляю пустое сообщение с именем - это моя регистрация
            sendMessageInternal(new Message(username, "", ""));
        } else {
            notifyStatus(false); // не удалось подключиться
            disconnect(); // закрываю соединение
        }
    }

    // чтение данных от сервера
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(4096); // коробочка для данных

        int bytesRead = channel.read(buffer); // читаю что прислал сервер
        if (bytesRead == -1) { // если -1, значит сервер закрыл соединение
            notifyStatus(false); // сообщаю об отключении
            disconnect(); // закрываюсь
            return;
        }

        if (bytesRead > 0) { // если что-то прочитала
            buffer.flip(); // переворачиваю для чтения
            processIncomingData(buffer); // обрабатываю данные
        }
    }

    // разбор входящих данных
    private void processIncomingData(ByteBuffer buffer) {
        try {
            // пока в буфере достаточно данных для чтения длины сообщения
            while (buffer.remaining() >= 4) {
                buffer.mark(); // ставлю метку, чтобы можно было вернуться
                int length = buffer.getInt(); // читаю длину сообщения

                // проверяю, чтобы длина была нормальной
                if (length <= 0 || length > 65536) {
                    disconnect(); // если нет - отключаюсь
                    return;
                }

                // проверяю, все ли данные сообщения пришли
                if (buffer.remaining() < length) {
                    buffer.reset(); // возвращаюсь к метке
                    break; // жду следующие данные
                }

                // все данные на месте - читаю сообщение
                byte[] data = new byte[length];
                buffer.get(data); // копирую в массив

                Message message = new Message(data); // создаю объект сообщения
                notifyMessage(message); // передаю слушателю
            }
        } catch (Exception e) {
            disconnect(); // при ошибке - отключаюсь
        }
    }

    // отправка обычного текстового сообщения
    public void sendMessage(String text, String room) {
        if (!connected) return; // если не подключен - ничего не делаю
        sendMessageInternal(new Message(username, text, room));
    }

    // запрос на присоединение к комнате
    public void joinRoom(String roomName) {
        if (!connected) return;
        // отправляю специальное сообщение типа JOIN_ROOM
        sendMessageInternal(new Message(Message.Type.JOIN_ROOM, username, roomName));
    }

    // запрос на создание комнаты
    public void createRoom(String roomName) {
        if (!connected) return;
        // отправляю специальное сообщение типа CREATE_ROOM
        sendMessageInternal(new Message(Message.Type.CREATE_ROOM, username, roomName));
    }

    // внутренний метод отправки сообщения
    private void sendMessageInternal(Message message) {
        if (!socketChannel.isOpen()) return; // если канал закрыт - выхожу

        try {
            byte[] data = message.toBytes(); // превращаю сообщение в байты
            ByteBuffer buffer = ByteBuffer.allocate(4 + data.length); // готовлю буфер
            buffer.putInt(data.length); // сначала пишу длину
            buffer.put(data); // потом само сообщение
            buffer.flip(); // готовлю к отправке

            // отправляю пакетиками
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }
        } catch (IOException e) {
            disconnect(); // при ошибке отправки - отключаюсь
        }
    }

    // отключение от сервера
    public void disconnect() {
        try {
            // закрываю канал, если он открыт
            if (socketChannel != null && socketChannel.isOpen()) {
                socketChannel.close();
            }
            // закрываю наблюдателя
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
        } catch (IOException e) {
            // игнорирую ошибки при закрытии
        } finally {
            connected = false; // сбрасываю флажок
            notifyStatus(false); // сообщаю об отключении
        }
    }

    // проверка подключения
    public boolean isConnected() {
        return connected && socketChannel != null && socketChannel.isOpen();
    }

    // уведомление слушателя о новом сообщении
    private void notifyMessage(Message message) {
        if (listener != null) {
            listener.onMessage(message);
        }
    }

    // уведомление слушателя об изменении статуса
    private void notifyStatus(boolean connected) {
        if (listener != null) {
            listener.onStatusChanged(connected);
        }
    }
}