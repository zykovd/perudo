package com.suai.perudo.web;

import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.suai.perudo.model.PerudoModel;
import com.suai.perudo.model.Player;
import com.suai.perudo.view.GameActivity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by dmitry on 11.09.18.
 */

public class PerudoServer extends Thread{

    private GsonBuilder builder = new GsonBuilder();
    private Gson gson = builder.create();

    private GameActivity view = null;

    private PerudoModel model;
    private String message;
    private boolean newTurn = false;

    private ServerSocket serverSocket;
    private Player onServerPlayer;
    private int port;

    private HashMap<WebUser, Player> players = new HashMap<>();

    public int getNumberOfPlayers(){
        return players.size();
    }

    public PerudoServer(int port, Player onServerPlayer) throws IOException, ClassNotFoundException {
        this.onServerPlayer = onServerPlayer;
        this.port = port;
    }

    public void setView(GameActivity view) {
        this.view = view;
    }

    @Override
    public void run() {
        super.run();
        try {
            this.serverSocket = new ServerSocket(port);
            String address = serverSocket.getLocalSocketAddress().toString();
            System.out.println(address);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (model == null) {
            try {
                System.out.println("PerudoServer.run");
                System.out.println("serverSocket = " + serverSocket);
                Socket clientSocket = serverSocket.accept();

                WebUser webUser = new WebUser(clientSocket);
                DataInputStream dataInputStream = webUser.getDataInputStream();
                Player player = gson.fromJson(dataInputStream.readUTF(), Player.class);

                DataOutputStream dataOutputStream = webUser.getDataOutputStream();
                dataOutputStream.writeUTF(new PerudoServerResponse(model, PerudoServerResponseEnum.CONNECTED, null).toJson());

                players.put(webUser, player);
                new PerudoServerThread(webUser).start();
                System.out.println("player = " + player);
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public PerudoServerResponse startGame() {
        ArrayList<Player> playersList = new ArrayList<>();
        for (Map.Entry<WebUser, Player> entry: players.entrySet()) {
            playersList.add(entry.getValue());
        }
        playersList.add(onServerPlayer);
        this.model = new PerudoModel(playersList, 6);
        this.model.setGameStarted(true);
        this.model.refreshDices();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    resendChangesToClients();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return new PerudoServerResponse(model, PerudoServerResponseEnum.GAME_START, onServerPlayer.getDices());
    }

    public PerudoServerResponse processOnServerPlayerCommand(PerudoClientCommand perudoClientCommand) {
        PerudoServerResponse perudoServerResponse = null;
        if (model == null || !model.isPlayersTurn(onServerPlayer)) {
            perudoServerResponse = new PerudoServerResponse(model, PerudoServerResponseEnum.WRONG_TURN,null);
            return perudoServerResponse;
        }
        boolean isStateChanged = tryProceedCommand(perudoClientCommand, null);
        if (isStateChanged) {
            if (newTurn) {
                perudoServerResponse = new PerudoServerResponse(model, PerudoServerResponseEnum.ROUND_RESULT, onServerPlayer.getDices());
                perudoServerResponse.setMessage(message);
            }
            else {
                perudoServerResponse = new PerudoServerResponse(model, PerudoServerResponseEnum.TURN_ACCEPTED, onServerPlayer.getDices());
            }
            Thread resender = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        resendChangesToClients();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            resender.start();
            try {
                resender.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            newTurn = false;
        }
        else {
            perudoServerResponse = new PerudoServerResponse(model, PerudoServerResponseEnum.INVALID_BID, null);
        }
        return perudoServerResponse;
    }

    synchronized private boolean tryProceedCommand(PerudoClientCommand perudoClientCommand, WebUser webUser) {
        Player player;
        if (webUser == null) {
            player = onServerPlayer;
        }
        else {
            player = players.get(webUser);
        }
        if (perudoClientCommand == null) {
            return false;
        }
        else {
            PerudoClientCommandEnum commandEnum = perudoClientCommand.getCommand();
            switch (commandEnum) {
                case BID:
                    Pair bid = perudoClientCommand.getBid();
                    if (model.tryMakeBid(player, (int)bid.first, (int)bid.second))
                        return true;
                    else
                        return false;
                case DOUBT:
                    String loser;
                    if (model.doubt(player)) {
                        loser = model.getCurrentBidPlayer().getName();
                    }
                    else {
                        loser = player.getName();
                    }
                    message = loser + " loosing one dice!";
                    if (model.getPlayers().size() == 1) {
                        message += "\n" + model.getPlayers().get(0).getName()+" is the winner!";
                    }
                    newTurn = true;
                    return true;
                case LEAVE:
                    if (model.isPlayersTurn(player)) {
                        model.forwardTurnTransition();
                        model.getPlayers().remove(player);
                    }
                    //TODO leave
                    break;
                case DISCONNECT:
                    if (model.isPlayersTurn(player)) {
                        model.forwardTurnTransition();
                        model.getPlayers().remove(player);
                    }
                    //TODO leave
                    break;
                case MAPUTO:
                    model.setMaputo(true);
                    return true;
                case START_GAME:
                    //TODO initial
                    break;
            }
        }
        return false;
    }

    private void sendChangesToOnServerPlayer() {
        if (view != null) {
            PerudoServerResponse response;
            if (newTurn) {
                response = new PerudoServerResponse(model, PerudoServerResponseEnum.ROUND_RESULT, onServerPlayer.getDices());
                response.setMessage(message);
            }
            else {
                response = new PerudoServerResponse(model, PerudoServerResponseEnum.TURN_ACCEPTED, onServerPlayer.getDices());
            }
            view.processResponse(response);
        }
    }

    private void resendChangesToClients() throws IOException {
        for (WebUser webUser: players.keySet()) {
            DataOutputStream dataOutputStream = webUser.getDataOutputStream();
            PerudoServerResponse response;
            if (newTurn) {
                response = new PerudoServerResponse(model, PerudoServerResponseEnum.ROUND_RESULT, players.get(webUser).getDices());
                response.setMessage(message);
            }
            else {
                response = new PerudoServerResponse(model, PerudoServerResponseEnum.TURN_ACCEPTED, players.get(webUser).getDices());
            }
            dataOutputStream.writeUTF(response.toJson());
        }
    }

    private class PerudoServerThread extends Thread {

        private WebUser webUser;

        public PerudoServerThread(WebUser webUser) {
            this.webUser = webUser;
        }

        @Override
        public void run() {
            DataInputStream dataInputStream = webUser.getDataInputStream();
            PerudoClientCommand perudoClientCommand;
            while (true) {
                try {
                    perudoClientCommand = gson.fromJson(dataInputStream.readUTF(), PerudoClientCommand.class);
                    if (perudoClientCommand != null) {
                        if (model == null || !model.isPlayersTurn(players.get(webUser))) {
                            PerudoServerResponse response = new PerudoServerResponse(model, PerudoServerResponseEnum.WRONG_TURN, players.get(webUser).getDices());
                            webUser.getDataOutputStream().writeUTF(response.toJson());
                            continue;
                        }
                        boolean stateChanged = tryProceedCommand(perudoClientCommand, webUser);
                        if (stateChanged) {
                            resendChangesToClients();
                            sendChangesToOnServerPlayer();
                            newTurn = false;
                        }
                        else {
                            PerudoServerResponse response = new PerudoServerResponse(model, PerudoServerResponseEnum.INVALID_BID, players.get(webUser).getDices());
                            webUser.getDataOutputStream().writeUTF(response.toJson());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}