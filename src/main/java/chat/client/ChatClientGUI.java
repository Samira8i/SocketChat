package chat.client;

import chat.Message;
import chat.client.network.NetworkClient;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ChatClientGUI {
    private JFrame frame;
    private JTextPane chatArea;
    private JTextField messageField;
    private JTextField roomField;
    private JButton sendButton;
    private JButton joinButton;
    private JButton createButton;
    private JLabel statusLabel;

    private NetworkClient networkClient;
    private String username;
    private String currentRoom = "";

    private Color pinkLight = new Color(255, 240, 245);
    private Color pinkMedium = new Color(255, 182, 193);
    private Color pinkDark = new Color(219, 112, 147);
    private Color purple = new Color(186, 85, 211);
    private Color green = new Color(0, 150, 0);

    public ChatClientGUI(String host, int port, String username) {
        this.username = username;
        initializeNetworkClient();
        createGUI();
        connectToServer(host, port);
    }

    private void initializeNetworkClient() {
        networkClient = new NetworkClient();
        networkClient.setMessageListener(new NetworkClient.MessageListener() {
            @Override
            public void onMessage(Message message) {
                handleIncomingMessage(message);
            }

            @Override
            public void onStatusChanged(boolean connected) {
                SwingUtilities.invokeLater(() -> {
                    if (connected) {
                        statusLabel.setText("Подключено");
                        statusLabel.setForeground(green);
                        messageField.setEnabled(true);
                        sendButton.setEnabled(true);
                        appendSystemMessage("🌸 Подключено к серверу");
                    } else {
                        statusLabel.setText("Отключено");
                        statusLabel.setForeground(pinkDark);
                        messageField.setEnabled(false);
                        sendButton.setEnabled(false);
                        appendSystemMessage("🌸 Отключено от сервера");
                    }
                });
            }
        });
    }

    private void createGUI() {
        frame = new JFrame("🌸 Чат - " + username);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(pinkLight);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        statusLabel = new JLabel("Подключение...");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        statusLabel.setForeground(Color.ORANGE);

        JLabel nameLabel = new JLabel("Пользователь: " + username);
        nameLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        nameLabel.setForeground(purple);

        topPanel.add(statusLabel, BorderLayout.WEST);
        topPanel.add(nameLabel, BorderLayout.EAST);
        frame.add(topPanel, BorderLayout.NORTH);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 13));
        chatArea.setBackground(new Color(255, 250, 250));

        StyledDocument doc = chatArea.getStyledDocument();
        Style defaultStyle = doc.addStyle("default", null);
        StyleConstants.setFontFamily(defaultStyle, "Arial");
        StyleConstants.setFontSize(defaultStyle, 13);

        Style systemStyle = doc.addStyle("system", defaultStyle);
        StyleConstants.setForeground(systemStyle, pinkDark);
        StyleConstants.setItalic(systemStyle, true);

        Style myNameStyle = doc.addStyle("myname", defaultStyle);
        StyleConstants.setForeground(myNameStyle, green);
        StyleConstants.setBold(myNameStyle, true);

        Style otherNameStyle = doc.addStyle("othername", defaultStyle);
        StyleConstants.setForeground(otherNameStyle, purple);
        StyleConstants.setBold(otherNameStyle, true);

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("💬 Сообщения"));
        frame.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        bottomPanel.setBackground(pinkLight);

        JPanel roomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        roomPanel.setBackground(pinkLight);

        roomField = new JTextField(12);
        roomField.setFont(new Font("Arial", Font.PLAIN, 12));

        joinButton = new JButton("🌸 Присоединиться");
        joinButton.setBackground(pinkMedium);
        joinButton.setForeground(Color.BLACK);
        joinButton.addActionListener(e -> joinRoom());

        createButton = new JButton("🌸 Создать");
        createButton.setBackground(pinkMedium);
        createButton.setForeground(Color.BLACK);
        createButton.addActionListener(e -> createRoom());

        roomPanel.add(new JLabel("Комната:"));
        roomPanel.add(roomField);
        roomPanel.add(joinButton);
        roomPanel.add(createButton);

        JPanel messagePanel = new JPanel(new BorderLayout(5, 0));
        messagePanel.setBackground(pinkLight);

        messageField = new JTextField();
        messageField.setEnabled(false);
        messageField.addActionListener(e -> sendMessage());

        sendButton = new JButton("🌸 Отправить");
        sendButton.setBackground(pinkDark);
        sendButton.setForeground(Color.BLACK);
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        bottomPanel.add(roomPanel, BorderLayout.NORTH);
        bottomPanel.add(messagePanel, BorderLayout.SOUTH);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
        messageField.requestFocus();
    }

    private void connectToServer(String host, int port) {
        new Thread(() -> {
            try {
                networkClient.connect(host, port, username);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Ошибка подключения");
                    statusLabel.setForeground(Color.RED);
                    appendSystemMessage("🌸 Не удалось подключиться: " + e.getMessage());
                    JOptionPane.showMessageDialog(frame,
                            "Не удалось подключиться к серверу",
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void handleIncomingMessage(Message message) {
        SwingUtilities.invokeLater(() -> {
            switch (message.getType()) {
                case TEXT:
                    displayChatMessage(message);
                    break;

                case SYSTEM:
                    appendSystemMessage("🌸 " + message.getContent());
                    break;

                case JOIN_ROOM:
                    if (message.getUsername().equals(username)) {
                        currentRoom = message.getRoom();
                        appendSystemMessage("🌸 Вы присоединились к комнате: " + currentRoom);
                    } else {
                        appendSystemMessage("🌸 " + message.getUsername() + " присоединился к комнате");
                    }
                    break;

                case CREATE_ROOM:
                    if (message.getUsername().equals(username)) {
                        currentRoom = message.getRoom();
                        appendSystemMessage("🌸 Вы создали комнату: " + currentRoom);
                    } else {
                        appendSystemMessage("🌸 " + message.getUsername() + " создал комнату");
                    }
                    break;
            }
        });
    }

    private void displayChatMessage(Message message) {
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            boolean isMyMessage = message.getUsername().equals(username);

            // Имя пользователя (цветное)
            if (isMyMessage) {
                doc.insertString(doc.getLength(), "[Вы] ", doc.getStyle("myname"));
            } else {
                doc.insertString(doc.getLength(), "[" + message.getUsername() + "] ",
                        doc.getStyle("othername"));
            }

            doc.insertString(doc.getLength(), message.getContent() + "\n",
                    doc.getStyle("default"));

            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void appendSystemMessage(String text) {
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            doc.insertString(doc.getLength(), text + "\n", doc.getStyle("system"));
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        if (currentRoom.isEmpty()) {
            appendSystemMessage("🌸 Сначала выберите комнату!");
            return;
        }

        // Сразу показываем свое сообщение в чате
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            doc.insertString(doc.getLength(), "[Вы] ", doc.getStyle("myname"));
            doc.insertString(doc.getLength(), text + "\n", doc.getStyle("default"));
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        networkClient.sendMessage(text, currentRoom);
        messageField.setText("");
    }

    private void joinRoom() {
        String roomName = roomField.getText().trim();
        if (roomName.isEmpty()) {
            appendSystemMessage("🌸 Введите имя комнаты!");
            return;
        }

        networkClient.joinRoom(roomName);
    }

    private void createRoom() {
        String roomName = roomField.getText().trim();
        if (roomName.isEmpty()) {
            appendSystemMessage("🌸 Введите имя комнаты!");
            return;
        }

        networkClient.createRoom(roomName);
    }

    private void disconnect() {
        int confirm = JOptionPane.showConfirmDialog(frame,
                "Выйти из чата?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (networkClient != null) {
                networkClient.disconnect();
            }
            frame.dispose();
        }
    }

    public static void showLogin() {
        SwingUtilities.invokeLater(() -> {
            Color pinkLight = new Color(255, 240, 245);
            Color pinkMedium = new Color(255, 182, 193);

            JFrame loginFrame = new JFrame("🌸 Вход в чат");
            loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            loginFrame.setSize(350, 200);
            loginFrame.setLocationRelativeTo(null);
            loginFrame.getContentPane().setBackground(pinkLight);

            JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            panel.setBackground(pinkLight);

            panel.add(new JLabel("Сервер:"));
            JTextField hostField = new JTextField("localhost");
            panel.add(hostField);

            panel.add(new JLabel("Порт:"));
            JTextField portField = new JTextField("1234");
            panel.add(portField);

            panel.add(new JLabel("Имя:"));
            JTextField usernameField = new JTextField();
            panel.add(usernameField);

            JButton connectButton = new JButton("🌸 Подключиться");
            connectButton.setBackground(pinkMedium);
            panel.add(new JLabel());
            panel.add(connectButton);

            loginFrame.add(panel);
            loginFrame.setVisible(true);

            connectButton.addActionListener(e -> {
                String host = hostField.getText().trim();
                String portStr = portField.getText().trim();
                String username = usernameField.getText().trim();

                if (username.isEmpty()) {
                    JOptionPane.showMessageDialog(loginFrame,
                            "Введите имя пользователя",
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    int port = portStr.isEmpty() ? 1234 : Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) throw new NumberFormatException();

                    loginFrame.dispose();
                    new ChatClientGUI(host, port, username);

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(loginFrame,
                            "Неверный порт",
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    public static void main(String[] args) {
        showLogin();
    }
}