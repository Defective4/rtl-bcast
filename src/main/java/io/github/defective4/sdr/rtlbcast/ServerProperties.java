package io.github.defective4.sdr.rtlbcast;

public class ServerProperties {
    public String bindHost = "127.0.0.1";
    public int bindPort = 55556;
    public int internalPort = 55555;
    public int maxClients = 0;
    public String rtlTcpArgs = "";
    public String rtlTcpPath = "rtl_tcp";
    protected String authModel = "LAST";

    public AuthModel getAuthModel() {
        try {
            return AuthModel.valueOf(authModel.toUpperCase());
        } catch (Exception e) {
            return AuthModel.NONE;
        }
    }
}
