package io.github.defective4.sdr.rtlbcast.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServer implements AutoCloseable {

    public static interface HttpListener {
        String request(String path, Map<String, String> query);
    }

    private final String host;
    private final List<HttpListener> listeners = new CopyOnWriteArrayList<>();
    private final int port;
    private final ServerSocket server;
    private final ExecutorService svc = Executors.newCachedThreadPool();

    public HttpServer(String host, int port) throws IOException {
        server = new ServerSocket();
        this.host = host;
        this.port = port;
    }

    public void addListener(HttpListener ls) {
        if (!listeners.contains(ls)) listeners.add(ls);
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    public List<HttpListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    public void removeListener(HttpListener ls) {
        listeners.remove(ls);
    }

    public void start() throws IOException {
        server.bind(new InetSocketAddress(host, port));
        while (!server.isClosed()) {
            Socket accepted = server.accept();
            accepted.setSoTimeout(1000);
            svc.submit(() -> {
                try (Socket client = accepted;
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                        OutputStream output = client.getOutputStream();
                        PrintWriter writer = new PrintWriter(output, false, StandardCharsets.UTF_8)) {
                    String status = "403 Forbidden";
                    String content = "";

                    String line = reader.readLine();
                    if (line != null && line.startsWith("GET /") && line.endsWith(" HTTP/1.1")) {
                        String path = line.substring(line.indexOf(' ') + 1);
                        path = path.substring(0, path.lastIndexOf(' '));
                        Map<String, String> queryParams = new HashMap<>();
                        int index = path.indexOf('?');
                        if (index != -1) {
                            String[] pairs = path.substring(index + 1).split("&");
                            for (String pair : pairs) {
                                String[] split = pair.split("=");
                                if (split.length > 1) {
                                    String key = split[0];
                                    String value = URLDecoder
                                            .decode(String.join("=", Arrays.copyOfRange(split, 1, split.length)),
                                                    StandardCharsets.UTF_8);
                                    queryParams.put(key, value);
                                }
                            }
                            path = path.substring(0, index);
                        }
                        path = URLDecoder.decode(path, StandardCharsets.UTF_8);
                        String response = null;
                        for (HttpListener ls : listeners)
                            response = ls.request(path, Collections.unmodifiableMap(queryParams));
                        if (response != null) {
                            content = response;
                            status = content.isEmpty() ? "204 No Content" : "200 OK";
                        }
                    }

                    byte[] contentData = content.getBytes(StandardCharsets.UTF_8);
                    writer.println("Access-Control-Allow-Origin: *");
                    writer.println("HTTP/1.1 " + status);
                    writer.println("Server: rtl-bcast");
                    writer.println("Connection: close");
                    writer.println("Content-Length: " + contentData.length);
                    if (contentData.length != 0) writer.println("Content-Type: text/plain");
                    writer.println();
                    writer.flush();
                    output.write(contentData);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

}
