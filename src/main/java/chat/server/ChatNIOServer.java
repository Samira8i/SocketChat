package chat.server;

import chat.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.function.Consumer;

public class ChatNIOServer {
    private volatile boolean running = false; // флаг работы сервера
    private Selector selector;
    private ServerSocketChannel serverChannel; //серверный канал
    private Consumer<String> logListener = System.out::println;

    // комнаты: имя - список каналов
    private final Map<String, Set<SocketChannel>> rooms = new HashMap<>();
    // пользователи: канал - имя
    private final Map<SocketChannel, String> users = new HashMap<>();
    // текущая комната пользователя
    private final Map<SocketChannel, String> currentRooms = new HashMap<>();
    //реализую слушатель который позволяет более гибко работать с логами и можно настроить на консоль допустим
    public void setLogListener(Consumer<String> listener) {
        this.logListener = (listener != null) ? listener : System.out::println;
    }

    public void start(int port) throws IOException {
        if (running) {
            throw new IllegalStateException("сервер уже запущен");
        }

        selector = Selector.open(); //создаем селектор
        serverChannel = ServerSocketChannel.open(); //создаем канал
        serverChannel.configureBlocking(false); //установка неблокирующего режима
        serverChannel.socket().setReuseAddress(true); //на всякий
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT); //регистрируем селектор на событие accept

        running = true;
        new Thread(this::runServer, "server-thread").start();

        log("сервер запущен на порту " + port);
    }

    private void runServer() {
        log("поток сервера запущен");

        while (running) {
            try {
                int ready = selector.select(100);
                if (ready == 0) continue;

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove(); //удаление обработанных ключей

                    if (!key.isValid()) continue;

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    } catch (IOException e) {
                        log("ошибка: " + e.getMessage());
                        closeClient(key);
                    }
                }
            } catch (IOException e) {
                log("ошибка селектора: " + e.getMessage());
            }
        }

        log("сервер остановлен");
    }
    //Обработка входящих соединений
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel(); //возвращаю канал связанный с ключом
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel == null) return;

        clientChannel.configureBlocking(false); //клиентский канал в неблокирующий режим
        clientChannel.register(selector, SelectionKey.OP_READ); //регистрирует на чтение данных

        log("новое подключение: " + clientChannel.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(4096);

        int bytesRead = channel.read(buffer);
        if (bytesRead == -1) { //если разрыв
            String username = users.get(channel);
            if (username != null) {
                log(username + " отключился");
                leaveRoom(channel, username);
            }
            closeClient(key);
            return;
        }

        if (bytesRead > 0) {
            buffer.flip(); //переключение на чтение
            processMessage(channel, buffer);
        }
    }

    private void processMessage(SocketChannel channel, ByteBuffer buffer) {
        try {
            // читаем длину сообщения
            if (buffer.remaining() < 4) return;
            int length = buffer.getInt();

            if (length <= 0 || length > 65536) {
                log("некорректная длина сообщения");
                return;
            }

            // читаем само сообщение
            if (buffer.remaining() < length) return;
            byte[] data = new byte[length];
            buffer.get(data);

            Message message = new Message(data);
            handleMessage(channel, message);

        } catch (Exception e) {
            log("ошибка обработки сообщения: " + e.getMessage());
        }
    }

    private void handleMessage(SocketChannel channel, Message message) throws IOException {
        String username = users.get(channel);

        // регистрация пользователя
        if (username == null) {
            username = message.getUsername();
            if (username == null || username.trim().isEmpty()) {
                sendMessage(channel, new Message("система", "введите имя", ""));
                return;
            }

            // проверка уникальности
            if (users.containsValue(username)) {
                sendMessage(channel, new Message("система", "имя занято", ""));
                return;
            }

            users.put(channel, username);
            log(username + " зарегистрировался");

            // приветствие
            sendMessage(channel, new Message("система", "добро пожаловать, " + username, ""));
            return;
        }

        // обработка по типу
        switch (message.getType()) {
            case TEXT:
                handleTextMessage(channel, username, message);
                break;

            case JOIN_ROOM:
                joinRoom(channel, username, message.getRoom());
                break;

            case CREATE_ROOM:
                createRoom(channel, username, message.getRoom());
                break;
        }
    }

    private void handleTextMessage(SocketChannel channel, String username, Message message) {
        String room = currentRooms.get(channel);
        if (room == null) {
            sendMessage(channel, new Message("система", "сначала войдите в комнату", ""));
            return;
        }

        String text = message.getContent();
        if (text.trim().isEmpty()) return;

        // рассылка по комнате
        broadcastToRoom(room, new Message(username, text, room), channel);
        log("[" + room + "] " + username + ": " + text);
    }

    private void joinRoom(SocketChannel channel, String username, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) return;

        // выходим из старой комнаты
        String oldRoom = currentRooms.get(channel);
        if (oldRoom != null) {
            leaveRoom(channel, username, oldRoom);
        }

        // входим в новую
        rooms.computeIfAbsent(roomName, k -> new HashSet<>()).add(channel); // удобный метод Map: если ключа нет, создает значение
        currentRooms.put(channel, roomName);

        // уведомляем
        sendMessage(channel, new Message(Message.Type.JOIN_ROOM, username, roomName));
        broadcastToRoom(roomName, new Message(Message.Type.SYSTEM, "система", username + " присоединился", roomName), channel);

        log(username + " вошел в комнату " + roomName);
    }

    private void createRoom(SocketChannel channel, String username, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) return;

        if (rooms.containsKey(roomName)) {
            // комната уже есть, просто входим
            joinRoom(channel, username, roomName);
            return;
        }

        // создаем новую комнату
        rooms.put(roomName, new HashSet<>());
        log("создана комната: " + roomName);

        // и входим в нее
        joinRoom(channel, username, roomName);
    }

    private void leaveRoom(SocketChannel channel, String username, String roomName) {
        Set<SocketChannel> roomClients = rooms.get(roomName);
        if (roomClients != null) {
            roomClients.remove(channel);
            if (roomClients.isEmpty()) {
                rooms.remove(roomName);
                log("комната " + roomName + " удалена (пустая)");
            }
        }

        broadcastToRoom(roomName, new Message(Message.Type.SYSTEM, "система", username + " отсоединился", roomName), null);
    }

    private void leaveRoom(SocketChannel channel, String username) {
        String room = currentRooms.get(channel);
        if (room != null) {
            leaveRoom(channel, username, room);
        }
        currentRooms.remove(channel);
        users.remove(channel);
    }

    private void broadcastToRoom(String roomName, Message message, SocketChannel exclude) {
        Set<SocketChannel> roomClients = rooms.get(roomName);
        if (roomClients == null) return;

        for (SocketChannel client : roomClients) {
            if (client != exclude && client.isOpen()) {
                sendMessage(client, message);
            }
        }
    }

    private void sendMessage(SocketChannel channel, Message message) {
        if (!channel.isOpen()) return;

        try {
            byte[] data = message.toBytes();
            ByteBuffer buffer = ByteBuffer.allocate(4 + data.length);
            buffer.putInt(data.length);
            buffer.put(data);
            buffer.flip();

            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } catch (IOException e) {
            log("ошибка отправки: " + e.getMessage());
        }
    }

    private void closeClient(SelectionKey key) {
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            String username = users.get(channel);

            if (username != null) {
                leaveRoom(channel, username);
            }

            key.cancel();
            channel.close();
        } catch (IOException e) {
            // игнорируем
        }
    }

    public void stop() {
        if (!running) return;

        log("остановка сервера...");
        running = false;

        // закрываем все соединения
        for (SelectionKey key : selector.keys()) {
            if (key.isValid()) {
                closeClient(key);
            }
        }

        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (IOException e) {
            log("ошибка закрытия: " + e.getMessage());
        }

        rooms.clear();
        users.clear();
        currentRooms.clear();

        log("сервер остановлен");
    }

    private void log(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        logListener.accept("[" + timestamp + "] " + message);
    }
}