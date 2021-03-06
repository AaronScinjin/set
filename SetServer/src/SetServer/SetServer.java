/*
 *                    SetServer
 *                    /   |   \
 *       ServerMessenger  |   ServerLogic (done by SetServer's functions)
 *                 ConnectionAcceptor
 *                        |
 * spawns multiple threads (one for each client connection)
 *
 * users - hashMap keying users by their client ID, 
           managed by SetServer functions
         - it is always a 1-1 mapping of clientID to a User because when a user
           logs out, the user removed from the map
 * gameRooms - hashMap mapping room numbers to actual rooms
 * sockets - maps clientIDs to their sockets
 *
 *
 */

package SetServer;

import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import SetServer.src.gamebackend.*;
import java.util.ArrayList;
import java.util.List;

public class SetServer {
  
  Boolean isrunning;
  Connection dbConnection = null;
  final Map<Integer, User> users;
  final Map<Integer, GameRoom> gameRooms;
  final ConcurrentMap<Integer, Socket> sockets;
  final BlockingQueue<Message> incomingMessages;
  final BlockingQueue<Message> outgoingMessages;
  ServerConnectionAcceptor scAcceptor;
  ServerMessenger sMessenger;
  int numRooms;
 
  public SetServer() {
    isrunning = true;
    users = new HashMap<>();
    gameRooms = new HashMap<>();
    sockets = new ConcurrentHashMap<>();
    incomingMessages = new LinkedBlockingQueue<>();
    outgoingMessages = new LinkedBlockingQueue<>();
    scAcceptor = new ServerConnectionAcceptor(isrunning,
                                              sockets,
                                              incomingMessages);
    sMessenger = new ServerMessenger(isrunning,
                                     sockets,
                                     outgoingMessages);
    numRooms = 0;
  }
  
  public void runServer() {
    scAcceptor.start();
    sMessenger.start();
    while (isrunning) {
      try {
        Message incomingMessage = incomingMessages.take();
        String [] messagePieces;
        messagePieces = incomingMessage.message.split("~");
        try {
          switch(messagePieces[0].charAt(0)) {
            case 'L': 
              pLogin(incomingMessage.clientID, messagePieces); 
              break;
            case 'R':
              pRegistration(incomingMessage.clientID, messagePieces);
              break;
            case 'D':
              pDisconnection(incomingMessage.clientID, messagePieces);
              break;
            case 'N':
              pCreateGame(incomingMessage.clientID, messagePieces);
              break;
            case 'J':
              pJoinGame(incomingMessage.clientID, messagePieces);
              break;
            case 'G':
              pStartGame(incomingMessage.clientID, messagePieces);
              break;
            case 'S':
              pSetRequest(incomingMessage.clientID, messagePieces);
              break;
            case 'E':
              pExitGame(incomingMessage.clientID, messagePieces);
              break;
            case 'C':
              pLobbyChat(incomingMessage.clientID, messagePieces);
              break;
            case 'T':
              pGameChat(incomingMessage.clientID, messagePieces);
              break;
          }
        } catch (SQLException e) {
          System.out.println("Database access error");
        }
      } catch (InterruptedException except) {
        System.out.println("Interrupted");
      }
    }
  }
  /*
   * L~username~password          :Login
   * R~username~password          :Registration
   * D                            :Disconnection
   * N~[room name]~maxNumPlayers  :Create Game
   * J~[room number]              :Join Game
   * G                            :Start Game
   * S~card1~card2~card3          :Set request
   * E                            :Exit Game
   * C~Message                    :Lobby Chat
   * T~Message                    :Game Chat
   *
   */
  //p stands for process
  
  //First three are functions that require SQL connection
  //have to connect and close after each query because of connection timeout
  //accepts message: L~username~password
  //sends either an error to client (X~[message])
  //or a request to update everyone's lobby tables (P~A~[logged in user])
  void pLogin (int clientID, String [] messagePieces) 
          throws SQLException, InterruptedException {
    
    if (messagePieces.length != 3) {
      System.err.println("Message length error!");
      return;
    }
    
    dbConnection = DriverManager.getConnection(
            "jdbc:mysql://IP:Port", "userName", "passWord");     
    Statement stmt = dbConnection.createStatement();
    ResultSet rs = stmt.executeQuery("INSERT QUERY HERE");
    
    if (rs.next()) {
      outgoingMessages.put(new Message(clientID, "X~Username does not exist"));
      System.out.println("Username not found!");      
    } else {
      
      //check if user is already online; if user is send error message to client
      for (User current : users.values()) {
        if (messagePieces[1].equals(current.username)) {
          outgoingMessages.put(new Message(clientID, "X~"
                  + "Username is already online"));
          return;
        }
      }
                 
      //if not already online 
      //verify the password and add the user to the lobby + list of users
      String password = rs.getString("password");
      if (messagePieces[2].equals(password)) {
        int rating = rs.getInt("rating");
        users.put(clientID, new User(messagePieces[1], -1, rating));
        outgoingMessages.put(new Message(-1, "P~A~" + messagePieces[1]));
      } else {
        outgoingMessages.put(new Message(clientID, "X~Invalid password"));
      }
    }
    
    stmt.close();
    dbConnection.close();
  }
  
  //accepts message: R~Username~Password
  //sends either an error to client (X~[message])
  //or a request to update everyone's lobby tables (P~A~[logged in user])
  void pRegistration(int clientID, String [] messagePieces) 
          throws SQLException, InterruptedException {
    if (messagePieces.length != 3) {
      System.err.println("Message error!");
      outgoingMessages.put(new Message(clientID, "X~"
              + "Invalid username! Probably contains '~'"));
      return;
    }
    
    dbConnection = DriverManager.getConnection(
            "jdbc:mysql://IP:Port", "userName", "passWord");     
    Statement stmt = dbConnection.createStatement();
    //get the user's data from the database
    ResultSet rs = stmt.executeQuery("INSERT QUERY HERE");
    
    if (rs.next()) {
      outgoingMessages.put(new Message(clientID, "X~Username already exists!"));
      System.out.println("Username already exists!");      
    } else {
      //insert user into database
      stmt.executeUpdate("Insert into database");
      System.out.println("Created new user: " + messagePieces[1]);
      users.put(clientID, new User(messagePieces[1], -1, 100));
      outgoingMessages.put(new Message(-1, "P~A~" + messagePieces[1]));
    }
    
    stmt.close();
    dbConnection.close();
  }
  
  //accepts message: D
  //which is sent by each UserConnection thread when attempting to read
  void pDisconnection(int clientID, String [] messagePieces) 
          throws InterruptedException, SQLException {
    
    if (messagePieces.length != 1) {
      System.err.println("Disconnection message length error!");
      return;
    }
    sockets.remove(clientID);
    User disconnected = users.get(clientID);
    
    //check if disconnected client was in a GameRoom or not
    //if the client was, message GameRoom and remove the corresponding player
    //(update username string)
    if (disconnected != null) {
      outgoingMessages.put(new Message(-1, "P~R~" + disconnected.username));
      
      if (disconnected.currentGameRoom >= 0) {
        GameRoom currentRm = gameRooms.get(disconnected.currentGameRoom);
        
        if (currentRm != null) {
          currentRm.removePlayer(clientID);
          if (currentRm.getNumPlayers() > 0) {
            messageGameRoom(currentRm, "T~" + disconnected.username 
                    + "disconnected");
            //update users in game room + their scores
            messageGameRoom(currentRm, currentRm.encodeNamesToString());
            
            //if game is in progress; force it to complete
            //lower disconnected player's score
            if (currentRm.isPlaying()) {
              dbConnection = DriverManager.getConnection(
                      "jdbc:mysql://IP:Port", "userName", "passWord");     
              Statement stmt = dbConnection.createStatement();
              ResultSet rs = stmt.executeQuery("INSERT_LOWER_RATING_QUERY HERE");
              stmt.close();
              dbConnection.close();
              
              //if there's only one player left it's game over
              if (currentRm.getNumPlayers() == 1) {
                currentRm.setCompleted();
                handleGameOver(currentRm);
              }
            } else {
              //the game has not started yet
              messageGameRoom(currentRm, "T~" + disconnected.username
                      + "disconnected! "
                      + "Ready players have been reset, press ready again!");
              currentRm.resetNumReady();
              messageGameRoom(currentRm, "G~R");
            }
            
          } else {
            gameRooms.remove(disconnected.currentGameRoom);
          }
          
        } else {
          System.err.println("Bug!");
        }
      }
      
      users.remove(clientID);
    }
  }
  
  //accepts message: N~[room name]~maxNumPlayers
  //sends message: A if already in a game room
  //or U~A~[room number]~[room name]~[current numPlayers]~[max players]~[status]
  void pCreateGame(int clientID, String [] messagePieces) 
          throws InterruptedException {
    if (messagePieces.length != 3) {
      System.err.println("Create game message length error!");
      return;
    }
    User rmCreator = users.get(clientID);
    if (rmCreator.currentGameRoom >= 0) {
      outgoingMessages.put(new Message(clientID, "A"));
      return;
    }
    rmCreator.currentGameRoom = numRooms;
    GameRoom newRm = new GameRoom(
            messagePieces[1], Integer.parseInt(messagePieces[2]));
    newRm.addPlayer(clientID, rmCreator.username);
    //update gameroom window
    outgoingMessages.put(new Message(clientID, newRm.encodeNamesToString()));
    //send an update of list of tables to all clients of new table
    outgoingMessages.put(new Message(-1, 
            "U~A~"+numRooms+"~"
            +messagePieces[1]+"~"+newRm.getNumPlayers()
            +"~"+newRm.getMaxNumPlayers()+"~Inactive"));
    outgoingMessages.put(new Message(-1, 
            "C~"+rmCreator.username+"created a game: "+ newRm.getName()));
    gameRooms.put(numRooms, newRm);
    ++numRooms;
  }
  
  //accepts a message: J~[room number]
  //sends out "J~I" if game in progress or "J~F" if it's full
  //updates the board's names + scores otherwise
  void pJoinGame(int clientID, String [] messagePieces) 
          throws InterruptedException {
    if (messagePieces.length != 2) {
      System.err.println("Join game message length error!");
      return;
    }
    User joining = users.get(clientID);
    if (joining.currentGameRoom >= 0) {
      outgoingMessages.put(new Message(clientID, "A"));
      return;
    }
    joining.currentGameRoom = Integer.parseInt(messagePieces[1]);
    GameRoom room = gameRooms.get(joining.currentGameRoom);
    if (room == null) {
      System.err.println("Room does not exist, possible bug!");
      joining.currentGameRoom = -1;
    } else {
      if (room.getNumPlayers() < room.getMaxNumPlayers()) {
        if (room.isPlaying()) {
          joining.currentGameRoom = -1;
          outgoingMessages.put(new Message(clientID, "J~I"));
        } else {
          room.addPlayer(clientID, joining.username);
          messageGameRoom(room, room.encodeNamesToString());
          outgoingMessages.put(new Message(-1, 
                  "L~"+joining.username
                  +"joined game room: "+ messagePieces[1] 
                  +" " + room.getName()));
          messageGameRoom(room, "U~A~" + joining.username);
        }
      } else {
        joining.currentGameRoom = -1;
        outgoingMessages.put(new Message(clientID, "J~F"));
      }
    }
  }
  
  //accepts message: G
  //which is sent when a player presses the ready button
  //Requires both players to be in room and ready
  //the clients will already be in the room
  //if not everyone is ready, game will not start but rather increment numready
  //sends out messages of T~[username]~is ready!
  //and a message with G~S~board~scores when everyone is ready
  void pStartGame(int clientID, String [] messagePieces) {
    if (messagePieces.length != 1)
      System.err.println("Start game message length error!");
    User starter = users.get(clientID);
    GameRoom room = gameRooms.get(starter.currentGameRoom);
    if (room == null) {
      System.err.println("Bug!");
    } else {
      room.incNumReady();
      messageGameRoom(room, "T~" + users.get(clientID).username + " is ready!");
      if (room.getNumPlayers() == room.getNumReady()) {
        messageGameRoom(room, "T~All users are ready. Game start!");
        messageGameRoom(room, room.InitializeGame());
      }
    }
  }
  
  //accepts message: S~card1~card2~card3
  //sends a message of form G~flag~board~scores
  void pSetRequest(int clientID, String [] messagePieces) throws SQLException{
    if (messagePieces.length != 4) {
      System.err.println("Set message length error!");
      return;
    }
    User sender = users.get(clientID);
    GameRoom room = gameRooms.get(sender.currentGameRoom);
    String updateMessage = room.CheckSetAndUpdate(clientID,
            messagePieces[1], messagePieces[2], messagePieces[3]);
    messageGameRoom(room, updateMessage);
    
    //check if game's over
    if (room.isCompleted())
      handleGameOver(room);
  }
  
  //Accepts "E"
  //sends out "T~[username]~left the game" to the room
  //removes users from the room and handles game over if necessary
  void pExitGame(int clientID, String [] messagePieces) 
          throws InterruptedException, SQLException {
    if (messagePieces.length != 1) {
      System.err.println("Leave game message length error!");
      return;
    }
    User user = users.get(clientID);
    if (user.currentGameRoom < 0) {
      System.err.println("leave room bug!!!");
      return;
    }
    GameRoom room = gameRooms.get(user.currentGameRoom);
    outgoingMessages.put(new Message(clientID, "E"));
    if (room == null) {
      System.err.println("leave room bug!!!");
    } else {
      room.removePlayer(clientID);
      if (room.isRoomEmpty()) {
        gameRooms.remove(user.currentGameRoom);
        outgoingMessages.put(new Message(-1, "U~R~"+user.currentGameRoom));
      } else {
        messageGameRoom(room, "T~" + user.username + "left the game");
        messageGameRoom(room, room.encodeNamesToString());
        if (room.isPlaying()) {
          //lower the score of the player who forfeited
          dbConnection = DriverManager.getConnection(
                  "jdbc:mysql://IP:Port", "userName", "passWord");     
          Statement stmt = dbConnection.createStatement();
          ResultSet rs = stmt.executeQuery("INSERT_LOWER_RATING_QUERY HERE");
          stmt.close();
          dbConnection.close();
          
          //handle game over if there's only 1 player left
          if (room.getNumPlayers() == 1) {
            room.setCompleted();
            handleGameOver(room);
          }
        }
      }
    }
    user.currentGameRoom = -1;
  }
  
  //accepts mesage C~message
  //sends out message C~[sender username]~message
  void pLobbyChat(int clientID, String [] messagePieces) 
          throws InterruptedException {
    if (messagePieces.length != 2) {
      System.err.println("Lobby chat message length error!");
      return;
    }
    User sender = users.get(clientID);
    outgoingMessages.put(new Message(-1, "C~" + sender.username + '~' +
            messagePieces[1]));
  }  
  
  //accepts message T~message
  //sends out message T~[sender username]~message
  void pGameChat(int clientID, String [] messagePieces) {
    if (messagePieces.length != 2) {
      System.err.println("Game chat message length error!");
      return;
    }
    User sender = users.get(clientID);
    GameRoom current = gameRooms.get(sender.currentGameRoom);
    messageGameRoom(current, "T~" + sender.username + '~' + messagePieces[1]);
  }
  
  void messageGameRoom(GameRoom room, String message) {
    List<Integer> pids = room.getPlayerIds();
    for (Integer pid : pids) {
      try {
        outgoingMessages.put(new Message(pid, message));
      } catch (InterruptedException except) {
        System.err.println("Could not send message to player with id: " + pid);
      }
    }
  }
  
  //handle cases for only 1 player left (everyone else left)
  //handle cases for actually completed
  // Game is over (players received F in their messages so they know)
  // Send results to database
  // Decide what to do with game room
  //handle what to do upon game over
  void handleGameOver(GameRoom room) throws SQLException {
    if (room.isCompleted() == false)
      System.err.println("Bug!");
    messageGameRoom(room, "The game is over. Ratings updating...");
    List<Integer> winners = new ArrayList<>();
    List<Integer> losers = new ArrayList<>();
    room.getWinners(winners, losers);
    int addedScore=((room.getNumPlayers()-winners.size())*10)/winners.size();
    int subtractedScore=((room.getNumPlayers()-losers.size())*10)/losers.size();
    dbConnection = DriverManager.getConnection(
            "jdbc:mysql://IP:Port", "userName", "passWord");     
    Statement stmt = dbConnection.createStatement();
    int updatedScore;
    for (int i = 0; i != winners.size(); ++i) {
      User current = users.get(winners.get(i));
      updatedScore = current.rating + addedScore;
      messageGameRoom(room, "T~" + current.username + "'s rating: " +
              current.rating + " -> " + updatedScore);
      current.rating = updatedScore;
      stmt.executeUpdate("UPDATE QUERY");
    }
    for (int i = 0; i != losers.size(); ++i) {
      User current = users.get(losers.get(i));
      updatedScore = current.rating - subtractedScore;
      messageGameRoom(room, "T~" + current.username + "'s rating: " +
              current.rating + " -> " + updatedScore);
      current.rating = updatedScore;
      stmt.executeUpdate("UPDATE QUERY");
    }
    stmt.close();
    dbConnection.close();
    room.resetRoom();
    messageGameRoom(room, "G~R");
  }
  
}
