package chat.server;

import javax.swing.*;
import java.awt.*;

public class ChatNIOServerGUI {
    private ChatNIOServer server;

    private JFrame frame;
    private JTextArea logArea;
    private JTextField portField;
    private JButton startButton;
    private JButton stopButton;

    public ChatNIOServerGUI() {
        createGUI();
    }

    private void createGUI() {
        frame = new JFrame("🌸 Чат Сервер 🌸");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLocationRelativeTo(null);

        Color pinkLight = new Color(255, 240, 245);
        Color pinkMedium = new Color(255, 182, 193);
        Color pinkDark = new Color(219, 112, 147);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(pinkLight);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBackground(pinkLight);

        controlPanel.add(new JLabel("Порт:"));
        portField = new JTextField("1234", 8);
        portField.setFont(new Font("Arial", Font.PLAIN, 14));
        controlPanel.add(portField);

        startButton = new JButton("🌸 Запустить");
        startButton.setBackground(pinkMedium);
        startButton.setForeground(Color.BLACK);
        startButton.setFont(new Font("Arial", Font.BOLD, 14));
        startButton.addActionListener(e -> startServer());
        controlPanel.add(startButton);

        stopButton = new JButton("🌸 Остановить");
        stopButton.setBackground(pinkDark);
        stopButton.setForeground(Color.BLACK);
        stopButton.setFont(new Font("Arial", Font.BOLD, 14));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopServer());
        controlPanel.add(stopButton);

        mainPanel.add(controlPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(255, 250, 250));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Лог сервера"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(portField.getText().trim());

            if (port < 1 || port > 65535) {
                JOptionPane.showMessageDialog(frame,
                        "Порт должен быть в диапазоне 1-65535",
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portField.setEnabled(false);

            server = new ChatNIOServer();

            // устанавливаем слушателя событий сервера - чтобы получать логи
            server.setServerListener(new ChatNIOServer.ServerListener() {
                @Override
                public void onLogMessage(String message) {
                    appendLog(message); // когда сервер пишет лог - добавляем в GUI
                }

                @Override
                public void onUserRegistered(String username) {
                    appendLog("👤 " + username + " зарегистрировался");
                }

                @Override
                public void onUserDisconnected(String username) {
                    appendLog("👤 " + username + " отключился");
                }

                @Override
                public void onRoomCreated(String roomName) {
                    appendLog("🏠 Создана комната: " + roomName);
                }

                @Override
                public void onUserJoinedRoom(String username, String roomName) {
                    appendLog("👤 " + username + " вошел в комнату " + roomName);
                }

                @Override
                public void onUserLeftRoom(String username, String roomName) {
                    appendLog("👤 " + username + " вышел из комнаты " + roomName);
                }

                @Override
                public void onChatMessage(String username, String roomName, String message) {
                    appendLog("💬 [" + roomName + "] " + username + ": " + message);
                }
            });

            new Thread(() -> {
                try {
                    server.start(port);
                    server.runServer();
                } catch (Exception e) {
                    appendLog("❌ Ошибка запуска сервера: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                        portField.setEnabled(true);
                    });
                }
            }, "ServerThread").start();

            appendLog("🌸 Сервер запущен на порту " + port);
            appendLog("🌸 Ожидание подключений...");

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame,
                    "Некорректный порт",
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (server == null) return;

        int confirm = JOptionPane.showConfirmDialog(frame,
                "Остановить сервер?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        appendLog("🛑 Остановка сервера...");
        server.stop();

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEnabled(true);

        appendLog("🌸 Сервер остановлен");
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // Автопрокрутка к новому сообщению
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatNIOServerGUI();
        });
    }
}