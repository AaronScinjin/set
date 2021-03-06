/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package SetServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Listener that will handle all incoming connections
 * @author Harrison
 */
public class ServerConnectionAcceptor extends Thread {
  Boolean isrunning;
  final ConcurrentMap<Integer, Socket> sockets;
  final BlockingQueue<Message> incomingMessages;
  
  public ServerConnectionAcceptor(Boolean isrunning,
                                  ConcurrentMap<Integer, Socket> sockets,
                                  BlockingQueue<Message> incomingMessages) {
    super("ServerConnectionAcceptor");
    this.isrunning= isrunning;
    this.sockets = sockets;
    this.incomingMessages = incomingMessages;
  }
  
  @Override
  public void run() {
    int totalClientCount = 0;
    ServerSocket sSocket = null;
    try {
      sSocket = new ServerSocket(10000);
    } catch (IOException except) {
      System.err.println("Failed to listen on port 10000!");
    }
    while (isrunning){
      try {
        Socket cSocket = null;  
        cSocket = sSocket.accept();
        sockets.put(totalClientCount, cSocket);
        ClientConnection connection;
        connection = new ClientConnection(totalClientCount,
                                          isrunning,
                                          cSocket,
                                          incomingMessages);
        ++totalClientCount;
        connection.start();
      } catch (IOException except) {
        System.err.println("Failed to accept incoming connection!");
      }
    }
  }
  
}
