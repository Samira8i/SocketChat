package chat.server;

import chat.Message;
import chat.io.MessageInputStream;
import chat.io.MessageOutputStream;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final BlockingQueue<Message> messageQueue;
    private final Set<ClientHandler> clients;
    private String username;
    private volatile boolean running = true;
    private MessageOutputStream outputStream;

    public ClientHandler(Socket socket, BlockingQueue<Message> messageQueue,
                         Set<ClientHandler> clients) {
        this.socket = socket;
        this.messageQueue = messageQueue;
        this.clients = clients;
    }

    @Override
    public void run() {
        try {
            MessageInputStream input = new MessageInputStream(socket.getInputStream());
            outputStream = new MessageOutputStream(socket.getOutputStream());

            //читаю первое сообщение которое должно быть именем пользователя
            Message usernameMsg = input.readMessage();
            if (usernameMsg == null) {
                System.out.println("Клиент отключился до отправки имени");
                disconnect();
                return;
            }

            this.username = usernameMsg.getUsername();

            if (this.username.isEmpty()) {
                Message errorMsg = new Message("Сервер", "Имя не может быть пустым");
                outputStream.writeMessage(errorMsg);
                disconnect();
                return;
            }
            //добавляю клиента в общий список
            clients.add(this);

            System.out.println("Пользователь '" + this.username + "' подключился");
            Message welcomeMsg = new Message("Сервер", "Добро пожаловать в чат, " + this.username + "!");
            outputStream.writeMessage(welcomeMsg);

            Message joinMsg = new Message("Сервер", "🐣 " + this.username + " присоединился к чату");
            messageQueue.put(joinMsg);

            //основной цикл чтения сообщений от клиента
            while (running) {
                Message message = input.readMessage();
                if (message == null) {
                    System.out.println("Клиент " + username + " отключился");
                    break;
                }

                String content = message.getMessage().trim();
                if (content.isEmpty()) {
                    continue;
                }
                if ("/exit".equalsIgnoreCase(content)) {
                    System.out.println("Клиент " + username + " вышел из чата по команде");
                    break;
                }

                Message broadcastMsg = new Message(username, content);
                messageQueue.put(broadcastMsg);
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода-вывода у клиента " + username + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            disconnect();
        }
    }

    //отправка сообщения конкретному своему клиенту, то есть эти сообщения не идут в общий поток, а просто вызывается этот метод у конкретного клиента
    public void sendMessage(Message message) {
        if (running && outputStream != null) {
            try {
                outputStream.writeMessage(message);
            } catch (IOException e) {
            }
        }
    }

    //отключение клиента и очистка ресурсов
    public void disconnect() {
        if (!running) return;

        running = false;
        //удаляем себя из списка активных клиентов
        clients.remove(this);

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Ошибка при закрытии сокета клиента " + username);
        }

        if (username != null) {
            System.out.println("Пользователь '" + username + "' отключился");
            try {
                Message leaveMsg = new Message("Сервер", username + " покинул чат");
                messageQueue.put(leaveMsg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getUsername() {
        return username;
    }
}