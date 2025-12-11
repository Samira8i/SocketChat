package chat.server;

import chat.Message;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class ChatNIOServer {
    private volatile boolean running = false;
    private Selector selector; // следит за всеми соединениями
    private ServerSocketChannel serverChannel; // главный канал для приема подключений

    // комнаты: имя комнаты - список кто в ней сидит
    private final Map<String, Set<SocketChannel>> rooms = new HashMap<>();
    // пользователи: канал связи - имя пользователя
    private final Map<SocketChannel, String> users = new HashMap<>();
    // текущая комната пользователя: канал - имя комнаты
    private final Map<SocketChannel, String> currentRooms = new HashMap<>();

    // слушатель событий сервера - кто будет получать уведомления
    private ServerListener listener;

    public interface ServerListener {
        void onLogMessage(String message); // когда нужно записать лог
        void onUserRegistered(String username); // когда пользователь зарегистрировался
        void onUserDisconnected(String username); // когда пользователь отключился
        void onRoomCreated(String roomName); // когда создана комната
        void onUserJoinedRoom(String username, String roomName); // когда пользователь вошел в комнату
        void onUserLeftRoom(String username, String roomName); // когда пользователь вышел из комнаты
        void onChatMessage(String username, String roomName, String message); // когда отправлено сообщение в чат
    }

    // устанавливаем слушателя - кто будет получать события сервера
    public void setServerListener(ServerListener listener) {
        this.listener = listener;
    }

    // внутренний метод для логирования - использует слушатель если есть, иначе System.out
    private void log(String message) {
        if (listener != null) {
            listener.onLogMessage(message); // сообщаю слушателю
        } else {
            System.out.println(message); // если слушателя нет - пишу в консоль
        }
    }

    // подготавливаем сервер к работе
    public void start(int port) throws IOException {
        if (running) {
            throw new IllegalStateException("сервер уже запущен");
        }

        selector = Selector.open(); // создаю наблюдателя
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false); // делаю неблокирующей
        serverChannel.bind(new InetSocketAddress(port)); // привязываю к порту
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        running = true;
        log("Сервер запущен на порту " + port); // использую наш метод log
    }

    // главный цикл работы сервера
    public void runServer() {
        log("Сервер начал работу");

        while (running) { // пока флажок "работаю" поднят
            try {
                int ready = selector.select(100); // проверяю: есть ли новые события? жду 100мс
                if (ready == 0) continue;

                Set<SelectionKey> keys = selector.selectedKeys(); // получаю список событий
                Iterator<SelectionKey> it = keys.iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove(); // убираю обработанное событие

                    if (!key.isValid()) continue; // если ключ неактивен - пропускаю

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key); // пришел новый гость
                        } else if (key.isReadable()) {
                            handleRead(key); // кто-то прислал сообщение
                        }
                    } catch (IOException e) {
                        log("ошибка: " + e.getMessage());
                        closeClient(key); // закрываю проблемное соединение
                    }
                }
            } catch (IOException e) {
                log("ошибка селектора: " + e.getMessage());
            }
        }

        log("Сервер остановлен");
    }

    // обработка нового подключения
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel(); // беру главный канал
        SocketChannel clientChannel = serverChannel.accept(); // принимаю нового клиента

        if (clientChannel == null) return; // если никто не пришел - выхожу

        clientChannel.configureBlocking(false); // делаю клиентский канал неблокирующим
        clientChannel.register(selector, SelectionKey.OP_READ);
        log("новое подключение: " + clientChannel.getRemoteAddress());
    }

    // чтение сообщения от клиента
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel(); // беру канал клиента
        ByteBuffer buffer = ByteBuffer.allocate(4096); // готовлю коробку для данных

        int bytesRead = channel.read(buffer); // читаю что прислал клиент
        if (bytesRead == -1) { // если -1, значит клиент отключился
            String username = users.get(channel);
            if (username != null) {
                log(username + " отключился");
                if (listener != null) {
                    listener.onUserDisconnected(username); // сообщаю слушателю об отключении
                }
                leaveRoom(channel, username); // удаляю из комнаты
            }
            closeClient(key); // закрываю соединение
            return;
        }

        if (bytesRead > 0) { // если что-то прочитала
            buffer.flip(); // переворачиваю буфер для чтения
            processMessage(channel, buffer); // обрабатываю сообщение
        }
    }

    // разбор полученного сообщения
    private void processMessage(SocketChannel channel, ByteBuffer buffer) {
        try {
            // сначала читаю длину сообщения (4 байта)
            if (buffer.remaining() < 4) return; // если данных мало - жду еще
            int length = buffer.getInt(); // читаю сколько байт в сообщении

            if (length <= 0 || length > 65536) { // проверяю чтобы длина была нормальная
                log("некорректная длина сообщения");
                return;
            }

            // проверяю, все ли данные пришли
            if (buffer.remaining() < length) return; // если не все - жду следующую порцию

            byte[] data = new byte[length]; // создаю массив под сообщение
            buffer.get(data); // копирую данные

            Message message = new Message(data); // создаю объект сообщения
            handleMessage(channel, message); // обрабатываю по типу

        } catch (Exception e) {
            log("ошибка обработки сообщения: " + e.getMessage());
        }
    }

    // обработка сообщения по типу
    private void handleMessage(SocketChannel channel, Message message) throws IOException {
        String username = users.get(channel); // смотрю, есть ли такой пользователь

        // если пользователь еще не зарегистрирован
        if (username == null) {
            username = message.getUsername(); // беру имя из сообщения
            if (username == null || username.trim().isEmpty()) {
                sendMessage(channel, new Message("система", "Введите имя", "")); // прошу ввести имя
                return;
            }

            // проверяю, не занято ли имя
            if (users.containsValue(username)) {
                sendMessage(channel, new Message("система", "Имя занято", ""));
                return;
            }

            // регистрирую нового пользователя
            users.put(channel, username);
            log(username + " зарегистрировался");
            if (listener != null) {
                listener.onUserRegistered(username); // сообщаю слушателю о регистрации
            }
            sendMessage(channel, new Message("система", "Добро пожаловать, " + username, ""));
            return;
        }

        // если уже зарегистрирован - смотрю тип сообщения
        switch (message.getType()) {
            case TEXT:
                handleTextMessage(channel, username, message); // обычное сообщение в чат
                break;
            case JOIN_ROOM:
                joinRoom(channel, username, message.getRoom()); // вход в комнату
                break;
            case CREATE_ROOM:
                createRoom(channel, username, message.getRoom()); // создание комнаты
                break;
        }
    }

    // обработка текстового сообщения
    private void handleTextMessage(SocketChannel channel, String username, Message message) {
        String room = currentRooms.get(channel); // в какой комнате находится пользователь
        if (room == null) { // если ни в какой
            sendMessage(channel, new Message("система", "Сначала войдите в комнату", ""));
            return;
        }

        String text = message.getContent(); // текст сообщения
        if (text.trim().isEmpty()) return; // если пустое - игнорирую

        // рассылаю сообщение всем в комнате
        broadcastToRoom(room, new Message(username, text, room), channel);
        log("[" + room + "] " + username + ": " + text);

        if (listener != null) {
            listener.onChatMessage(username, room, text); // сообщаю слушателю о сообщении в чате
        }
    }

    // присоединение к комнате
    private void joinRoom(SocketChannel channel, String username, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            sendMessage(channel, new Message("система", "Введите имя комнаты", ""));
            return;
        }

        // проверяю, существует ли такая комната
        if (!rooms.containsKey(roomName)) {
            sendMessage(channel, new Message("система", "Комната '" + roomName + "' не существует", ""));
            return;
        }

        // если пользователь уже в какой-то комнате - выхожу из нее
        String oldRoom = currentRooms.get(channel);
        if (oldRoom != null) {
            leaveRoom(channel, username, oldRoom);
        }

        // добавляю пользователя в комнату
        rooms.get(roomName).add(channel);
        currentRooms.put(channel, roomName);

        // уведомляю пользователя и всех в комнате
        sendMessage(channel, new Message(Message.Type.JOIN_ROOM, username, roomName));
        broadcastToRoom(roomName, new Message(Message.Type.SYSTEM, "система", username + " присоединился", roomName), channel);

        log(username + " вошел в комнату " + roomName);

        if (listener != null) {
            listener.onUserJoinedRoom(username, roomName); // сообщаю слушателю о входе в комнату
        }
    }

    // создание новой комнаты
    private void createRoom(SocketChannel channel, String username, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            sendMessage(channel, new Message("система", "Введите имя комнаты", ""));
            return;
        }

        // проверяю, нет ли уже такой комнаты
        if (rooms.containsKey(roomName)) {
            sendMessage(channel, new Message("система", "Комната '" + roomName + "' уже существует", ""));
            return;
        }

        // создаю новую комнату
        rooms.put(roomName, new HashSet<>());
        log("Создана комната: " + roomName);

        if (listener != null) {
            listener.onRoomCreated(roomName); // сообщаю слушателю о создании комнаты
        }

        // автоматически вхожу в созданную комнату
        joinRoom(channel, username, roomName);
    }

    // выход из комнаты
    private void leaveRoom(SocketChannel channel, String username, String roomName) {
        Set<SocketChannel> roomClients = rooms.get(roomName); // кто в комнате
        if (roomClients != null) {
            roomClients.remove(channel); // убираю пользователя
            // если комната пустая - удаляю ее
            if (roomClients.isEmpty()) {
                rooms.remove(roomName);
                log("Комната " + roomName + " удалена (пустая)");
            }
        }

        // уведомляю всех о выходе
        broadcastToRoom(roomName, new Message(Message.Type.SYSTEM, "система", username + " отсоединился", roomName), null);

        if (listener != null) {
            listener.onUserLeftRoom(username, roomName); // сообщаю слушателю о выходе из комнаты
        }
    }

    // полный выход пользователя
    private void leaveRoom(SocketChannel channel, String username) {
        String room = currentRooms.get(channel); // в какой комнате был
        if (room != null) {
            leaveRoom(channel, username, room); // выхожу из комнаты
        }
        currentRooms.remove(channel); // убираю из текущих комнат
        users.remove(channel); // убираю из пользователей
    }

    // рассылка сообщения всем в комнате
    private void broadcastToRoom(String roomName, Message message, SocketChannel exclude) {
        Set<SocketChannel> roomClients = rooms.get(roomName); // кто в комнате
        if (roomClients == null) return; // если комнаты нет - выхожу

        for (SocketChannel client : roomClients) {
            if (client != exclude && client.isOpen()) { // всем кроме исключения и если канал открыт
                sendMessage(client, message); // отправляю сообщение
            }
        }
    }

    // отправка сообщения одному клиенту
    private void sendMessage(SocketChannel channel, Message message) {
        if (!channel.isOpen()) return; // если канал закрыт - ничего не делаю

        try {
            byte[] data = message.toBytes(); // превращаю сообщение в байты
            ByteBuffer buffer = ByteBuffer.allocate(4 + data.length); // готовлю буфер
            buffer.putInt(data.length); // сначала пишу длину
            buffer.put(data); // потом само сообщение
            buffer.flip(); // готовлю к отправке

            // отправляю пакетиками, пока все не отправлю
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } catch (IOException e) {
            log("Ошибка отправки: " + e.getMessage());
        }
    }

    // закрытие соединения с клиентом
    private void closeClient(SelectionKey key) {
        try {
            SocketChannel channel = (SocketChannel) key.channel(); // беру канал
            String username = users.get(channel); // имя пользователя

            if (username != null) {
                leaveRoom(channel, username); // вывожу из комнаты
            }

            key.cancel(); // отменяю ключ
            channel.close(); // закрываю канал
        } catch (IOException e) {
            // ничего не делаю при ошибке закрытия
        }
    }

    // остановка всего сервера
    public void stop() {
        if (!running) return; // если уже не работает - выхожу

        log("Остановка сервера...");
        running = false; // опускаю флажок "работаю"

        // закрываю все соединения
        for (SelectionKey key : selector.keys()) {
            if (key.isValid()) {
                closeClient(key);
            }
        }

        try {
            if (selector != null && selector.isOpen()) {
                selector.close(); // закрываю наблюдателя
            }
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close(); // закрываю главную дверь
            }
        } catch (IOException e) {
            log("Ошибка закрытия: " + e.getMessage());
        }

        // очищаю все списки
        rooms.clear();
        users.clear();
        currentRooms.clear();

        log("Сервер остановлен");
    }
}