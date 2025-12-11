mvn clean compile

# Сервер 
mvn exec:java -Dexec.mainClass="chat.io.server.ChatServer"

# Клиент 
mvn exec:java -Dexec.mainClass="chat.io.client.ChatClient"


mvn exec:java -Dexec.mainClass="chat.server.ChatServer"
mvn exec:java -Dexec.mainClass="chat.client.ChatNIOClient"

mvn exec:java -Dexec.mainClass="chat.ChatApplication"