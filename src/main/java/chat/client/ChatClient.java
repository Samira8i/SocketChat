package chat.client;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

import chat.Message;
import chat.io.MessageInputStream;
import chat.io.MessageOutputStream;

public class ChatClient {
    private volatile boolean running = true;
    private String username;

    public void start(String host, int port) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Введите ваше имя:");
        this.username = sc.nextLine();

        try (Socket clientSocket = new Socket(host, port);
             MessageOutputStream output = new MessageOutputStream(clientSocket.getOutputStream());
             MessageInputStream input = new MessageInputStream(clientSocket.getInputStream())) {

            // Регистрирую имя на сервере
            output.writeMessage(new Message(username, ""));
            Message response = input.readMessage();

            if (response == null) {
                System.out.println("Сервер разорвал соединение");
                return;
            }

            System.out.println(response);

            // Если сервер вернул ошибку имени - выход
            if (response.getUsername().equals("Сервер") &&
                    (response.getMessage().contains("занято") || response.getMessage().contains("пустым"))) {
                return;
            }

            // Поток для чтения сообщений от сервера
            Thread readerThread = new Thread(() -> {
                try {
                    while (running) {
                        Message message = input.readMessage();
                        if (message == null) break;
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    if (running) {
                        System.out.println("Ошибка соединения: " + e.getMessage());
                    }
                }
                running = false;
            });
            readerThread.setDaemon(true);
            readerThread.start();

            // Основной цикл отправки сообщений
            while (running) {
                String text = sc.nextLine().trim();

                if (text.isEmpty()) continue;

                if ("/exit".equalsIgnoreCase(text)) {
                    System.out.println("Выход из чата...");
                    break;
                }

                try {
                    output.writeMessage(new Message(username, text));
                } catch (IOException e) {
                    System.out.println("Не удалось отправить сообщение: " + e.getMessage());
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println("Не удалось подключиться к серверу: " + e.getMessage());
        } finally {
            running = false;
            sc.close();
        }
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        Scanner scanner = new Scanner(System.in);

        System.out.print("Введите адрес сервера (по умолчанию localhost): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) host = "localhost";

        int port = 1234;
        while (true) {
            try {
                System.out.print("Введите порт сервера (по умолчанию 1234): ");
                String portInput = scanner.nextLine().trim();
                if (!portInput.isEmpty()) {
                    port = Integer.parseInt(portInput);
                }
                break;
            } catch (NumberFormatException e) {
                System.out.println("Неверный формат порта. Введите число.");
            }
        }

        client.start(host, port);
        scanner.close();
    }
}