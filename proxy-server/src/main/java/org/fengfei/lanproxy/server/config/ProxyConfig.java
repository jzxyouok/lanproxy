package org.fengfei.lanproxy.server.config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.fengfei.lanproxy.common.JsonUtil;
import org.fengfei.lanproxy.common.LangUtil;

import com.google.gson.reflect.TypeToken;

/**
 * server config
 *
 * @author fengfei
 *
 */
public class ProxyConfig {

    private static Integer serverPort;

    private static Map<String, List<Integer>> clientInetPortMapping = new HashMap<String, List<Integer>>();

    private static Map<Integer, String> inetPortLanInfoMapping = new HashMap<Integer, String>();

    @SuppressWarnings("unchecked")
    public static void init() {
        try {
            InputStream in = ProxyConfig.class.getClassLoader().getResourceAsStream("config.json");
            byte[] buf = new byte[1024];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int readIndex;
            while ((readIndex = in.read(buf)) != -1) {
                out.write(buf, 0, readIndex);
            }
            Map<String, Object> config = JsonUtil.json2object(new String(out.toByteArray()),
                    new TypeToken<Map<String, Object>>() {
                    });

            serverPort = LangUtil.parseDouble(config.get("server_port")).intValue();
            List<?> clients = (List<?>) config.get("clients");
            for (Object ct : clients) {
                Map<String, Object> client = (Map<String, Object>) ct;
                String clientKey = (String) client.get("client_key");
                List<Map<String, Object>> mappings = (List<Map<String, Object>>) client.get("proxy_mapping");
                List<Integer> ports = new ArrayList<Integer>();
                clientInetPortMapping.put(clientKey, ports);
                for (Map<String, Object> mapping : mappings) {
                    Integer port = LangUtil.parseDouble(mapping.get("inet_port")).intValue();
                    ports.add(port);
                    if (inetPortLanInfoMapping.containsKey(port)) {
                        throw new IllegalArgumentException("duplicate inet port " + port);
                    }
                    Map<String, Object> lan = (Map<String, Object>) mapping.get("lan");
                    Integer lanPort = LangUtil.parseDouble(lan.get("port")).intValue();
                    inetPortLanInfoMapping.put(port, lan.get("ip") + ":" + lanPort);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Integer getServerPort() {
        return serverPort;
    }

    public static List<Integer> getClientInetPorts(String clientKey) {
        return clientInetPortMapping.get(clientKey);
    }

    public static String getLanInfo(Integer port) {
        return inetPortLanInfoMapping.get(port);
    }

    public static List<Integer> getUserPorts() {
        List<Integer> ports = new ArrayList<Integer>();
        Iterator<Integer> ite = inetPortLanInfoMapping.keySet().iterator();
        while (ite.hasNext()) {
            ports.add(ite.next());
        }
        return ports;
    }
}
