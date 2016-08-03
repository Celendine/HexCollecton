package risterral;

import java.net.ServerSocket;

public class Main {

    public static void main(String[] args) {
        try {
            final ServerSocket serverSocket = new ServerSocket(7777);
            final HexListener hexListener = new HexListener(serverSocket);
            new Thread(hexListener).start();
            System.out.println("Started to listening for Hex events on port: 7777\nPress any key to stop");
            System.in.read();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
