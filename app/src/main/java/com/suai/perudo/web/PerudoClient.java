package com.suai.perudo.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.suai.perudo.model.Player;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;

/**
 * Created by dmitry on 11.09.18.
 */

public class PerudoClient extends Thread {

    private GsonBuilder builder = new GsonBuilder();
    private Gson gson = builder.create();

    private Socket socket;
    private Player player;

    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;

    private String inetAddress;
    private int port;
    private boolean isOffline;

    public PerudoClient(String inetAddress, int port, Player player, boolean isOffline) {
        this.inetAddress = inetAddress;
        this.port = port;
        this.player = player;
        this.isOffline = isOffline;
    }

    @Override
    public void run() {
        super.run();
        try {
            int i = 0;
            byte[] bytes = new byte[4];
            String[] b = inetAddress.split(Pattern.quote("."));
            for (String s : b) {
                bytes[i] = (byte)Integer.parseInt(s);
                ++i;
            }
            InetAddress address = InetAddress.getByAddress(bytes);
            this.socket = new Socket(address, port);

            if (isOffline) {
                this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataOutputStream.writeUTF(player.toJson());

                this.dataInputStream = new DataInputStream(socket.getInputStream());
                PerudoServerResponse perudoServerResponse = gson.fromJson(dataInputStream.readUTF(), PerudoServerResponse.class);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() throws IOException {
//        thread.stop(); //TODO ExecutorService
        dataInputStream.close();
        dataOutputStream.close();
        socket.close();
    }

    public void sendCommand(PerudoClientCommand perudoClientCommand) throws IOException {
        dataOutputStream.writeUTF(perudoClientCommand.toJson());
    }

    public PerudoServerResponse getResponse() {
        PerudoServerResponse perudoServerResponse = null;
        try {
            perudoServerResponse = gson.fromJson(dataInputStream.readUTF(), PerudoServerResponse.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return perudoServerResponse;
    }
}
