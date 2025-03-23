package io.github.defective4.sdr.rtlbcast;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BroadcastServer implements AutoCloseable {

    private final AuthModel authModel;
    private final List<ClientSession> connectedClients = Collections.synchronizedList(new ArrayList<>());
    private final String host;
    private final int maxClients;
    private final int port;
    private final ServerProperties props;
    private final RTLTcpCommand rtlTcp;
    private Thread rtlTcpReader;
    private final ExecutorService service;

    private final ServerSocket srv;

    public BroadcastServer(ServerProperties props) throws IOException {
        this.props = props;
        authModel = props.getAuthModel();
        host = props.bindHost;
        port = props.bindPort;
        maxClients = props.maxClients;
        srv = new ServerSocket();
        service = Executors.newFixedThreadPool(maxClients <= 0 ? Integer.MAX_VALUE : maxClients);
        rtlTcp = new RTLTcpCommand(props.rtlTcpPath, props.internalPort,
                props.rtlTcpArgs.isEmpty() ? null : props.rtlTcpArgs.split(" "));
    }

    @Override
    public void close() throws IOException {
        srv.close();
    }

    public RTLTcpCommand getRtlTcp() {
        return rtlTcp;
    }

    public void start() throws IOException {
        if (!props.rtlTcpOnDemand) startRTL();
        srv.bind(new InetSocketAddress(host, port));
        while (!srv.isClosed()) {
            Socket client = srv.accept();
            service.submit(() -> {
                ClientSession localSes = null;
                try (ClientSession ses = new ClientSession(client, rtlTcp)) {
                    boolean auth;
                    switch (authModel) {
                        case LAST -> {
                            synchronized (connectedClients) {
                                connectedClients.forEach(c -> c.setAuthorized(false));
                            }
                            auth = true;
                        }
                        case FIRST -> auth = connectedClients.isEmpty();
                        case ALL -> auth = true;
                        default -> auth = false;
                    }
                    ses.setAuthorized(auth);
                    localSes = ses;
                    addClient(ses);
                    ses.handle();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (localSes == null) return;
                    removeClient(localSes);
                    if (localSes.isAuthorized()) synchronized (connectedClients) {
                        if (!connectedClients.isEmpty()) switch (authModel) {
                            case FIRST -> connectedClients.get(0).setAuthorized(true);
                            case LAST -> connectedClients.get(connectedClients.size() - 1).setAuthorized(true);
                            default -> {}
                        }
                    }
                }
            });
        }
    }

    private void addClient(ClientSession ses) throws IOException {
        synchronized (connectedClients) {
            connectedClients.add(ses);
        }
        if (props.rtlTcpOnDemand) startRTL();
    }

    private void removeClient(ClientSession ses) {
        synchronized (connectedClients) {
            connectedClients.remove(ses);
        }
        if (props.rtlTcpOnDemand && connectedClients.isEmpty()) stopRTL();
    }

    private synchronized void startRTL() throws IOException {
        if (rtlTcp.isAlive()) return;
        System.err.println("Starting rtl_tcp server...");
        rtlTcp.start();
        rtlTcpReader = new Thread(() -> {
            try (InputStream is = rtlTcp.getInput()) {
                byte[] data = new byte[1024];
                int read;
                while (rtlTcp.isAlive()) {
                    read = is.read(data);
                    if (read <= 0) continue;
                    synchronized (connectedClients) {
                        for (ClientSession client : connectedClients) {
                            try {
                                client.getOs().write(data, 0, read);
                            } catch (IOException e) {
                                try {
                                    client.close();
                                } catch (IOException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        });
        rtlTcpReader.start();
        System.err.println("rtl_tcp started!");
    }

    private synchronized void stopRTL() {
        System.err.println("Stopping rtl_tcp server...");
        rtlTcp.stop();
        if (rtlTcpReader != null) {
            rtlTcpReader.interrupt();
            rtlTcpReader = null;
        }
        System.err.println("rtl_tcp stopped!");
    }

}
