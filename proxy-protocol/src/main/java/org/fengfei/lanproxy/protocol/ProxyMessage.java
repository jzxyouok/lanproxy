package org.fengfei.lanproxy.protocol;

import java.util.Arrays;

/**
 *
 * @author fengfei
 *
 */
public class ProxyMessage {

    public static final byte TYPE_HEARTBEAT = 0x07;

    public static final byte TYPE_AUTH = 0x01;

    public static final byte TYPE_ACK = 0x02;

    public static final byte TYPE_CONNECT = 0x03;

    public static final byte TYPE_DISCONNECT = 0x04;

    public static final byte TYPE_TRANSFER = 0x05;

    public static final byte TYPE_WRITE_CONTROL = 0x06;

    private byte type;

    private long serialNumber;

    private String uri;

    private byte[] data;

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(long serialNumber) {
        this.serialNumber = serialNumber;
    }

    @Override
    public String toString() {
        return "ProxyMessage [type=" + type + ", serialNumber=" + serialNumber + ", uri=" + uri + ", data="
                + Arrays.toString(data) + "]";
    }

}
