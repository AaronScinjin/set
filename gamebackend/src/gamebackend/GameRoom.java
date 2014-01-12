/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package gamebackend;

/**
 *
 * @author David
 */
public class GameRoom {
    StringConverter sc = new StringConverter();
    Board board;
    Scoring score;
    public int state; // 0 inactive, 1 active, 2 complete
    
    public GameRoom(){
        state = 0;
    }
    
    public String InitializeGame(int pid1, int pid2){
        board = new Board(); //Initialize Board with 12 cards
        board.DealUntilSetOrTwelve();
        score = new Scoring(pid1, pid2);
        state = 1;
        return sc.EncodeBoardToString(board, score, "S");
    }
    
    public String CheckSetAndUpdate(String message, int pid){
        Set set = sc.DecodeSetFromString(message);
        if(board.TestAndRemoveSet(set) == 1){
            score.AddToScore(pid, 3);
            if(board.DealUntilSetOrTwelve()){
                return sc.EncodeBoardToString(board, score, "Y");
            }
            else{
                state = 2;
                return sc.EncodeBoardToString(board, score, "F"); // Game over
            }
        }
        else if(board.TestAndRemoveSet(set) == 0){
            return sc.EncodeBoardToString(board, score, "N");
        }
        else{
            // There is a bug somewhere. This shouldn't be possible.
            throw new IllegalArgumentException("Requested set not in board");
        }
    }
}
