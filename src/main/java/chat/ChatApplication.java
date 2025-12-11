package chat;

import javax.swing.*;
import java.awt.*;

public class ChatApplication {
    public static void main(String[] args) {
        //строчки для установки привычного мак стиля у свинга
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        //специальный метод чтобы гуи работал асинхронно , через лямбду пишу
        SwingUtilities.invokeLater(() -> {
            Color pinkLight = new Color(255, 240, 245);
            Color pinkMedium = new Color(255, 182, 193);
            Color pinkDark = new Color(219, 112, 147);
            Color purple = new Color(186, 85, 211);

            JFrame choiceFrame = new JFrame("🌸 Чат Самиры"); //главное окно
            choiceFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); //завершение при закрытии
            choiceFrame.setSize(400, 200);
            choiceFrame.setLocationRelativeTo(null); //центрация
            choiceFrame.getContentPane().setBackground(pinkLight);
            //справка для себя: BorderLayout делит пространство на 5 зон: NORTH, SOUTH, EAST, WEST, CENTER
            JPanel mainPanel = new JPanel(new BorderLayout()); //контейнер для компонентов
            mainPanel.setBackground(pinkLight);
            mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30)); //отступы

            JLabel titleLabel = new JLabel("🌸 Выберите режим работы:", JLabel.CENTER);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
            titleLabel.setForeground(purple);
            mainPanel.add(titleLabel, BorderLayout.NORTH);

            //панель для кнопок
            JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
            buttonPanel.setBackground(pinkLight);

            JButton serverButton = new JButton("🌸 Запустить сервер");
            serverButton.setBackground(pinkMedium);
            serverButton.setForeground(Color.black);
            serverButton.setFont(new Font("Arial", Font.BOLD, 14));
            serverButton.addActionListener(e -> {
                choiceFrame.dispose(); //закрывает окно
                new chat.server.ChatNIOServerGUI(); //запускает гуи сервер
            });

            JButton clientButton = new JButton("🌸 Запустить клиент");
            clientButton.setBackground(pinkDark);
            clientButton.setForeground(Color.black);
            clientButton.setFont(new Font("Arial", Font.BOLD, 14));
            clientButton.addActionListener(e -> {
                choiceFrame.dispose();
                chat.client.ChatClientGUI.showLogin(); //вызывает метод для показа окна
            });

            buttonPanel.add(serverButton);
            buttonPanel.add(clientButton);

            mainPanel.add(buttonPanel, BorderLayout.CENTER);

            choiceFrame.add(mainPanel);
            choiceFrame.setVisible(true); //делает видимым!
        });
    }
}