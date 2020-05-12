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

    private Socket remoteSocket;
    private final Socket clientSocket;
    private String requestHeaderFirstLine = "";

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            requestHeaderFirstLine = readHeader(clientSocket);
            Matcher connect_matcher = CONNECT_PATTERN.matcher(requestHeaderFirstLine);
            Matcher get_matcher = GET_PATTERN.matcher(requestHeaderFirstLine);
            if (connect_matcher.matches()) {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream(),
                        StandardCharsets.ISO_8859_1);
                outputStreamWriter.write("HTTP/" + connect_matcher.group(3) + " 200 Connection established\r\n");
                outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                outputStreamWriter.write("\r\n");
                outputStreamWriter.flush();
                outputStreamWriter.close();

                remoteSocket = new Socket(connect_matcher.group(1), Integer.parseInt(connect_matcher.group(2)));

                Thread remoteToClient = new Thread(() -> forwardData(remoteSocket, clientSocket));
                remoteToClient.start();

                Thread clientToRemote = new Thread(() -> forwardData(clientSocket, remoteSocket));
                clientToRemote.start();

                remoteToClient.join();
                clientToRemote.join();

                remoteSocket.close();
            } else if (get_matcher.matches()) {
                URL url = new URL(get_matcher.group(1));

                HttpURLConnection proxyToServerCon = (HttpURLConnection) url.openConnection();
                proxyToServerCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                proxyToServerCon.setRequestProperty("Content-Language", "en-US");
                proxyToServerCon.setUseCaches(false);
                proxyToServerCon.setDoOutput(true);

                InputStream inputStream = proxyToServerCon.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();
                forwardDataUtil(inputStream, outputStream);
                inputStream.close();
                outputStream.close();
            }
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void forwardData(Socket inputSocket, Socket outputSocket) {
        try {
            InputStream inputStream = inputSocket.getInputStream();
            OutputStream outputStream = outputSocket.getOutputStream();
            forwardDataUtil(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void forwardDataUtil(InputStream inputStream, OutputStream outputStream) {
        try {
            byte[] buffer = new byte[4096];
            int read;
            do {
                read = inputStream.read(buffer);
                if (read > 0) {
                    outputStream.write(buffer, 0, read);
                    if (inputStream.available() < 1)
                        outputStream.flush();
                }
            } while (read >= 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String readHeader(Socket socket) {
        String headerString = "";
        InputStream inputStream;
        try {
            byte[] buffer = new byte[4096];
            inputStream = socket.getInputStream();
            int length = inputStream.read(buffer);
            if (length > -1) {
                headerString = new String(buffer);
                // System.out.println(headerString);
                headerString = headerString.substring(0, headerString.indexOf("\n")).strip();
                System.out.println(headerString);
            }
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return headerString;
    }
}
