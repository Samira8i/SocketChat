package chat.server;

import chat.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ChatServer {
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static final int MAX_CLIENTS = 10;

    //в этом случае реализация с LinkedBlockingQueue самая потокобезопасная
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();

    public void start(int port) {
        Scanner scanner = new Scanner(System.in);

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Сервер запущен на порту: " + port);
            //запускаю поток рассылки сообщений
            Thread broadcaster = new Thread(this::broadcastMessages);
            //завершаем даемон потоки чтобы не было того, чтобы программа зависала, их использую так как рассылка и обработка команрд фоновые процессыц
            broadcaster.setDaemon(true);
            broadcaster.start();
            //поток который отвечает за команды
            Thread commandHandler = new Thread(() -> handleCommands(scanner));
            commandHandler.setDaemon(true);
            commandHandler.start();
            //для каждого клиента новый поток в клиеннтхэндлере
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    if (clients.size() >= MAX_CLIENTS) {
                        System.out.println("Достигнут лимит клиентов. Отклоняем подключение");
                        clientSocket.close();
                        continue;
                    }

                    System.out.println("Подключился новый клиент: " + clientSocket.getInetAddress());

                    ClientHandler clientHandler = new ClientHandler(
                            clientSocket, messageQueue, clients
                    );

                    new Thread(clientHandler).start();

                } catch (IOException e) {
                    if (running) {
                        System.out.println("Ошибка при принятии подключения: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("Не удалось запустить сервер: " + e.getMessage());
        } finally {
            scanner.close();
            stop();
        }
    }

    private void broadcastMessages() {
        System.out.println("Запущен поток рассылки сообщений");

        while (running) {
            try {
                Message message = messageQueue.take();

                System.out.println("Рассылаем: " + message);
                //копию использую чтобы избежать ошибок из-за многопоточки
                Set<ClientHandler> clientsCopy = Set.copyOf(clients);
                for (ClientHandler client : clientsCopy) {
                    try {
                        if (client.isRunning()) {
                            client.sendMessage(message);
                        }
                    } catch (Exception e) {
                        System.out.println("Ошибка отправки пользователю " + client.getUsername());
                        client.disconnect();
                    }
                }

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void handleCommands(Scanner scanner) {
        while (running) {
            String command = scanner.nextLine().trim();
            if ("/exit".equalsIgnoreCase(command)) {
                System.out.println("Останавливаем сервер...");
                stop();
                break;
            }
        }
    }

    public void stop() {
        if (!running) return;

        running = false;
        System.out.println("Закрываем подключения...");

        for (ClientHandler client : clients) {
            client.disconnect();
        }
        clients.clear();

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Ошибка при закрытии сервера: " + e.getMessage());
        }

        System.out.println("Сервер остановлен");
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        Scanner scanner = new Scanner(System.in);

        int port = 0;
        while (true) {
            try {
                System.out.print("Введите порт для сервера (например, 1234): ");
                port = Integer.parseInt(scanner.nextLine().trim());

                if (port < 1 || port > 65535) {
                    System.out.println("Порт должен быть от 1 до 65535");
                    continue;
                }

                break;
            } catch (NumberFormatException e) {
                System.out.println("Неверный формат. Введите число.");
            }
        }

        server.start(port);
        scanner.close();
    }
}