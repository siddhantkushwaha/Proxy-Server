package com.siddhantkushwaha.proxy;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ClientHandler extends Thread {

    public static final Pattern CONNECT_PATTERN = Pattern.compile("CONNECT (.+):(.+) HTTP/(1\\.[01])",
            Pattern.CASE_INSENSITIVE);
    public static final Pattern GET_PATTERN = Pattern.compile("GET (.+) HTTP/(1\\.[01])",
            Pattern.CASE_INSENSITIVE);

    private final Socket clientSocket;
    private Socket remoteSocket;

    private String fullHeader = "";
    private String headerString = "";

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            readRequestHeader(clientSocket);
            Matcher connect_matcher = CONNECT_PATTERN.matcher(headerString);
            Matcher get_matcher = GET_PATTERN.matcher(headerString);
            if (connect_matcher.matches()) {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream(),
                        StandardCharsets.ISO_8859_1);
                outputStreamWriter.write("HTTP/" + connect_matcher.group(3) + " 200 Connection established\r\n");
                outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                outputStreamWriter.write("\r\n");
                outputStreamWriter.flush();

                remoteSocket = new Socket(connect_matcher.group(1), Integer.parseInt(connect_matcher.group(2)));

                Thread remoteToClient = new Thread(() -> forwardData(remoteSocket, clientSocket));
                remoteToClient.start();

                Thread clientToRemote = new Thread(() -> forwardData(clientSocket, remoteSocket));
                clientToRemote.start();

                remoteToClient.join();
                clientToRemote.join();

            } else if (get_matcher.matches()) {
                URL url = new URL(get_matcher.group(1));

                HttpURLConnection proxyToServerCon = (HttpURLConnection) url.openConnection();
                proxyToServerCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                proxyToServerCon.setRequestProperty("Content-Language", "en-US");
                proxyToServerCon.setUseCaches(false);
                proxyToServerCon.setDoOutput(true);

                forwardDataUtil(proxyToServerCon.getInputStream(), clientSocket.getOutputStream(), true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void forwardData(Socket inputSocket, Socket outputSocket) {
        try {
            InputStream inputStream = inputSocket.getInputStream();
            OutputStream outputStream = outputSocket.getOutputStream();
            forwardDataUtil(inputStream, outputStream, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void forwardDataUtil(InputStream inputStream, OutputStream outputStream, boolean intercept) {
        try {
            byte[] buffer = new byte[4096];
            int read;
            do {
                read = inputStream.read(buffer);
                if (read > 0) {

                    System.out.println(headerString);
                    if (intercept) {
                        System.out.println(new String(buffer));
                    }

                    outputStream.write(buffer, 0, read);
                    if (inputStream.available() < 1)
                        outputStream.flush();
                }
            } while (read >= 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readRequestHeader(Socket socket) {
        try {
            byte[] buffer = new byte[4096];
            if (socket.getInputStream().read(buffer) > -1) {
                fullHeader = new String(buffer);
                headerString = fullHeader.substring(0, fullHeader.indexOf("\n")).strip();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
