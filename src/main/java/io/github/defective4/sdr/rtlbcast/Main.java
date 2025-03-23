package io.github.defective4.sdr.rtlbcast;

import java.io.File;
import java.io.IOException;

import io.github.defective4.sdr.rtlbcast.http.HttpServer;
import io.github.defective4.sdr.rtlbcast.server.BroadcastServer;
import io.github.defective4.sdr.rtlbcast.server.ClientListener;
import io.github.defective4.sdr.rtlbcast.server.ClientSession;

public class Main {

    private static byte lastGain = 0;

    public static void main(String[] args) {
        try {
            File propsFile = new File("rtl-bcast.properties");
            ServerProperties props = new ServerProperties(propsFile);
            if (!propsFile.exists()) {
                props.save();
                System.err.println("Saved default " + propsFile.getName());
                System.exit(1);
                return;
            }
            if (props.load()) System.err.println("Loaded properties from " + propsFile.getName());
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
                            System.err
                                    .println("Starting HTTP server on " + props.httpServerHost + ":"
                                            + props.httpServerPort);
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
                System.err.println("Starting rtl-bcast server on " + props.bindHost + ":" + props.bindPort);
                if (props.rtlTcpOnDemand) System.err
                        .println("rtl-bcast is on on-demand mode. rtl_tcp won't start until the first connection.");
                System.err.println("Auth model is " + props.getAuthModel());
                bcast.start();
            }
        } catch (Exception e) {
            System.exit(2);
            e.printStackTrace();
        }
    }
}
