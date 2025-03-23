package io.github.defective4.sdr.rtlbcast;

import java.io.IOException;

import io.github.defective4.sdr.rtlbcast.http.HttpServer;
import io.github.defective4.sdr.rtlbcast.server.BroadcastServer;
import io.github.defective4.sdr.rtlbcast.server.ClientListener;
import io.github.defective4.sdr.rtlbcast.server.ClientSession;

public class Main {

    private static byte lastGain = 0;

    public static void main(String[] args) {
        try {
            ServerProperties props = new ServerProperties();
            try (BroadcastServer bcast = new BroadcastServer(props)) {
                if (props.httpEnable) {
                    HttpServer server = new HttpServer(props.httpServerHost, props.httpServerPort);
                    server.addListener((path, query) -> {
                        if ("/sdrctl/gain".equals(path)) {
                            if (!bcast.getRtlTcp().isAlive()) return null;
                            try {
                                if (!query.containsKey("gain")) throw new IllegalStateException();
                                lastGain = Byte.parseByte(query.get("gain"));
                                bcast.getRtlTcp().forceGain(lastGain);
                                return "";
                            } catch (Throwable e) {
                                return Byte.toString(lastGain);
                            }
                        }
                        return null;
                    });
                    Thread.startVirtualThread(() -> {
                        try {
                            server.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                            try {
                                server.close();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                    });
                }
                bcast.addListener(new ClientListener() {
                    @Override
                    public void clientAdded(ClientSession session) {
                        System.err
                                .println("Client [" + session.getInetAddress() + "] connected. Total clients: "
                                        + bcast.getConnectedClients().size());
                    }

                    @Override
                    public void clientRemoved(ClientSession session) {
                        System.err
                                .println("Client [" + session.getInetAddress() + "] disconnected. Total clients: "
                                        + bcast.getConnectedClients().size());
                    }
                });
                bcast.start();
            }
        } catch (

        Exception e) {
            e.printStackTrace();
        }
    }
}
