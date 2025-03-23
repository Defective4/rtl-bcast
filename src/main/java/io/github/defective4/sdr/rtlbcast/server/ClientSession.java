package io.github.defective4.sdr.rtlbcast.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Objects;

import io.github.defective4.sdr.rtlbcast.RTLTcpCommand;

public class ClientSession implements AutoCloseable {

    private boolean authorized;
    private final InputStream is;
    private final OutputStream os;
    private final RTLTcpCommand rtlTcp;
    private final Socket socket;

    public ClientSession(Socket socket, RTLTcpCommand cmd) throws IOException {
        rtlTcp = cmd;
        Objects.requireNonNull(socket);
        this.socket = socket;
        is = socket.getInputStream();
        os = socket.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    public InetAddress getInetAddress() {
        return socket.getInetAddress();
    }

    public OutputStream getOs() {
        return os;
    }

    public int getPort() {
        return socket.getPort();
    }

    public void handle() throws IOException {
        byte[] data = new byte[1024];
        int read;
        while (!socket.isClosed()) {
            read = is.read(data);
            if (read <= 0) break;
            if (authorized) rtlTcp.writeData(data, read);
        }
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

}
