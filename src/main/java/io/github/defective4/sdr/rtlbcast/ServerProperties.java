package io.github.defective4.sdr.rtlbcast;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ServerProperties extends Properties {
    public String bindHost = "127.0.0.1";
    public int bindPort = 55556;
    public boolean httpEnable = false;
    public String httpServerHost = "localhost";
    public int httpServerPort = 55557;
    public int internalPort = 55555;
    public int maxClients = 0;
    public String rtlTcpArgs = "";
    public boolean rtlTcpOnDemand = false;
    public String rtlTcpPath = "rtl_tcp";
    protected String authModel = "LAST";

    private final File file;

    public ServerProperties(File file) {
        this.file = file;
    }

    public AuthModel getAuthModel() {
        try {
            return AuthModel.valueOf(authModel.toUpperCase());
        } catch (Exception e) {
            return AuthModel.NONE;
        }
    }

    public boolean load() {
        if (file.isFile()) {
            try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                load(reader);
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (Field field : getClass().getDeclaredFields()) {
                try {
                    Class<?> type = field.getType();
                    if (type == File.class) continue;
                    String prop = getProperty(field.getName());
                    if (prop == null) continue;
                    Object val = null;
                    if (type == String.class) val = prop;
                    else if (type == int.class) val = Integer.parseInt(prop);
                    else if (type == boolean.class) val = Boolean.parseBoolean(prop);
                    if (val != null) field.set(this, val);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
        return false;
    }

    public void save() {
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            for (Field field : getClass().getDeclaredFields()) {
                try {
                    String val = null;
                    Class<?> type = field.getType();
                    if (type == File.class) continue;
                    if (type == String.class) val = (String) field.get(this);
                    else if (type == int.class) val = Integer.toString(field.getInt(this));
                    else if (type == boolean.class) val = Boolean.toString(field.getBoolean(this));
                    setProperty(field.getName(), val);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            store(writer, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
