package chat.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.nio.channels.SocketChannel;

public class ChatNIOServer {
    private volatile boolean running = false; // флаг работы сервера
    private Selector selector;
    private ServerSocketChannel serverChannel; //серверный канал
    private ConnectionHandler connectionHandler; //обработчик соединений

    private final Map<SocketChannel, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<ClientSession>> rooms = new ConcurrentHashMap<>();

    private Thread serverThread; //поток работы сервера
    private Consumer<String> logListener = System.out::println;
    //использовала паттерн слушатель, то есть логи могут выводиться и в консоль и в гуи в зависимости от парматера
    public void setLogListener(Consumer<String> listener) {
        this.logListener = (listener != null) ? listener : System.out::println;
    }

    public void start(int port) throws IOException {
        if (running) {
            throw new IllegalStateException("Сервер уже запущен");
        }

        selector = Selector.open(); //создаем селектор
        serverChannel = ServerSocketChannel.open(); //создаем канал
        serverChannel.configureBlocking(false); //установка неблокирующего режима
        serverChannel.socket().setReuseAddress(true); //на всякий
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT); //регестрирует селектор на событие accept


        connectionHandler = new ConnectionHandler(selector, sessions, rooms, logListener);
        running = true;

        serverThread = new Thread(this::runServer, "NIOServer-Main");
        serverThread.start();

        log("Сервер запущен на порту " + port);
    }

    private void runServer() {
        log("Поток сервера запущен");

        while (running) {
            try {
                connectionHandler.handleEvents();
            } catch (IOException e) {
                if (running) {
                    log("Ошибка селектора: " + e.getMessage());
                }
            } catch (Exception e) {
                log("Неожиданная ошибка: " + e.getMessage());
                e.printStackTrace();
            }
        }

        shutdown();
    }

    public void stop() {
        if (!running) return;

        log("Остановка сервера...");
        running = false;

        // Закрываем все соединения
        if (connectionHandler != null) {
            connectionHandler.closeAllConnections();
            connectionHandler.wakeupSelector();
        }

        // Останавливаем поток сервера
        if (serverThread != null) {
            try {
                serverThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        closeServerResources();
        log("Сервер остановлен");
    }

    private void shutdown() {
        log("Завершение работы сервера...");

        // Отправляем сообщение об остановке всем клиентам
        for (ClientSession session : sessions.values()) {
            if (session.channel.isOpen()) {
                try {
                    chat.Message shutdownMsg = chat.Message.createSystemMessage("Сервер останавливается");
                    connectionHandler.sendToClient(session, shutdownMsg);
                } catch (Exception e) {
                    // Игнорируем ошибки отправки
                }
            }
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        closeServerResources();
    }

    private void closeServerResources() {
        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
        } catch (IOException e) {
            log("Ошибка при закрытии селектора: " + e.getMessage());
        }

        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (IOException e) {
            log("Ошибка при закрытии серверного канала: " + e.getMessage());
        }

        sessions.clear();
        rooms.clear();
    }

    public boolean isRunning() {
        return running;
    }

    public int getClientCount() {
        return sessions.size();
    }

    public Set<String> getRooms() {
        return rooms.keySet();
    }

    private void log(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        logListener.accept("[" + timestamp + "] " + message);
    }
}