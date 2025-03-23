package io.github.defective4.sdr.rtlbcast.server;

public interface ClientListener {
    void clientAdded(ClientSession session);

    void clientRemoved(ClientSession session);
}
