package io.github.defective4.sdr.rtlbcast;

public class Main {
    public static void main(String[] args) {
        try {
            ServerProperties props = new ServerProperties();

            try (BroadcastServer server = new BroadcastServer(props)) {
                server.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
