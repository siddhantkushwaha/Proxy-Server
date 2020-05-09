package com.siddhantkushwaha.proxy;


import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) {

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(8899);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (serverSocket == null)
            return;

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandler.start();

            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
