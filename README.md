mvn clean compile

# Сервер 
mvn exec:java -Dexec.mainClass="chat.server.ChatServer"

# Клиент 
mvn exec:java -Dexec.mainClass="chat.client.ChatClient"
