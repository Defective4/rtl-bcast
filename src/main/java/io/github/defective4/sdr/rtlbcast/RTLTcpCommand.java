package io.github.defective4.sdr.rtlbcast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

public class RTLTcpCommand {
    private InputStream input;
    private OutputStream output;
    private Process process;
    private final String[] rtlTcpArgs;

    private final String rtlTcpPath;
    private final int rtlTcpPort;
    private Socket socket;

    public RTLTcpCommand(String rtlTcpPath, int rtlTcpPort, String[] rtlTcpArgs) {
        Objects.requireNonNull(rtlTcpPath);
        if (rtlTcpPort <= 0) throw new IllegalArgumentException("rtl_tcp port <= 0");
        if (rtlTcpPort > Short.MAX_VALUE << 1)
            throw new IllegalArgumentException("rtl_tcp port > " + (Short.MAX_VALUE << 1));
        this.rtlTcpPath = rtlTcpPath;
        this.rtlTcpArgs = rtlTcpArgs == null ? new String[4] : new String[rtlTcpArgs.length + 4];
        this.rtlTcpPort = rtlTcpPort;
        if (rtlTcpArgs != null) System.arraycopy(rtlTcpArgs, 0, this.rtlTcpArgs, 4, rtlTcpArgs.length);
        this.rtlTcpArgs[0] = "-a";
        this.rtlTcpArgs[1] = "127.0.0.1";
        this.rtlTcpArgs[2] = "-p";
        this.rtlTcpArgs[3] = Integer.toString(rtlTcpPort);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (process != null) process.destroyForcibly(); }));
    }

    public void forceGain(byte gain) throws IOException {
        if (!isAlive()) throw new IOException("rtl_tcp is not started");
        byte[] command = {
                13, 0, 0, 0, (byte) Math.min(28, Math.max(gain, 0))
        };
        writeData(command, command.length);
    }

    public InputStream getInput() {
        return input;
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public void start() throws IOException {
        if (isAlive()) throw new IllegalStateException("Already started");
        String[] procArgs = new String[rtlTcpArgs.length + 1];
        System.arraycopy(rtlTcpArgs, 0, procArgs, 1, rtlTcpArgs.length);
        procArgs[0] = rtlTcpPath;
        process = new ProcessBuilder(procArgs).directory(new File(System.getProperty("user.home"))).start();
        int attempts = 5;
        while (attempts > 0) {
            if (!process.isAlive()) throw new IOException("rtl_tcp process died");
            try {
                Socket sock = new Socket();
                sock.connect(new InetSocketAddress("127.0.0.1", rtlTcpPort));
                socket = sock;
                input = sock.getInputStream();
                output = sock.getOutputStream();
                return;
            } catch (Exception e) {}
            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
            attempts--;
        }
        throw new IOException("Failed to connect to rtl_tcp server after 5 attempts");
    }

    public void stop() {
        if (process != null) process.destroyForcibly();
        if (input != null) try {
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (output != null) try {
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (socket != null) try {
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        socket = null;
        process = null;
    }

    public void writeData(byte[] data, int len) throws IOException {
        if (!isAlive() || output == null) return;
        synchronized (output) {
            output.write(data, 0, len);
        }
    }
}
