/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package SetClient;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 *
 * @author Harrison
 */
public class SetClient {
  
  boolean listening;
  final Socket socket;
  final BufferedReader istream;
  final DataOutputStream ostream;
  
  
  public SetClient() throws IOException {
    //replace with actual server connection
    listening = true;
    socket = new Socket("127.0.0.1", 1111);
    istream = new BufferedReader(
            new InputStreamReader(socket.getInputStream()));
    ostream = new DataOutputStream(socket.getOutputStream());
  }
  
  public void runClient() throws IOException {
    while (listening) {
      String incomingMessage = istream.readLine();
      String [] messagePieces = incomingMessage.split("~");
      switch (messagePieces[0].charAt(0)) {
        //cases to switch on
      }
    }
  }
  
}
