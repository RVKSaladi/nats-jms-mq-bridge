package io.nats.bridge.messages;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static io.nats.bridge.messages.Protocol.*;

public class BaseMessageWithHeaders implements BytesMessage {

    private final long timestamp;
    //TTL plus timestamp
    private final long expirationTime;
    //Delivery time is not instant
    private final long deliveryTime;
    private final int mode;
    private final String type;
    private final boolean redelivered;
    private final int priority;
    private final Map<String, Object> headers;

    private final byte[] bodyBytes;

    private final static ObjectMapper mapper = new ObjectMapper();
    private final Consumer<Message> replyHandler;


    public BaseMessageWithHeaders(long timestamp, long expirationTime, long deliveryTime, int mode, String type,
                                  boolean redelivered, int priority, Map<String, Object> headers, byte[] bytes,
                                  Consumer<Message> replyHandler) {
        this.timestamp = timestamp;
        this.expirationTime = expirationTime;
        this.deliveryTime = deliveryTime;
        this.mode = mode;
        this.type = type;
        this.redelivered = redelivered;
        this.priority = priority;
        this.headers = headers;
        this.bodyBytes = bytes;
        this.replyHandler = replyHandler;
    }


    @Override
    public void reply(final Message reply) {
        replyHandler.accept(reply);
    }

    public byte[] getBodyBytes() {
        return bodyBytes;
    }

    public long timestamp() {
        return timestamp;
    }

    //TTL plus timestamp
    public long expirationTime() {
        return expirationTime;
    }

    //Delivery time is not instant
    public long deliveryTime() {
        return deliveryTime;
    }

    public int mode() {
        return mode;
    }

    public String type() {
        return type;
    }

    public boolean redelivered() {
        return redelivered;
    }

    public int priority() {
        return priority;
    }

    public Map<String, Object> headers() {
        return headers;
    }

    public byte[] getMessageAsBytes() {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream streamOut = new DataOutputStream(baos);

        try {


            streamOut.writeByte(Protocol.MARKER_AB);
            streamOut.writeByte(Protocol.MARKER_CD);
            streamOut.writeByte(Protocol.MESSAGE_VERSION_MAJOR);
            streamOut.writeByte(Protocol.MESSAGE_VERSION_MINOR);
            streamOut.writeByte(Protocol.MARKER_AB);
            streamOut.writeByte(Protocol.MARKER_CD);


            final HashMap<String, Object> outputHeaders = new HashMap<>(9 + (headers != null ? headers.size() : 0));

            if (headers != null) {
                outputHeaders.putAll(headers);
            }
            if (deliveryTime > 0) {
                outputHeaders.put(HEADER_KEY_DELIVERY_TIME, this.deliveryTime());
            }
            if (mode != -1)
                outputHeaders.put(HEADER_KEY_MODE, this.mode());
            if (expirationTime > 0)
                outputHeaders.put(HEADER_KEY_EXPIRATION_TIME, this.expirationTime());
            if (timestamp > 0)
                outputHeaders.put(HEADER_KEY_TIMESTAMP, this.timestamp());
            if (type != null)
                outputHeaders.put(HEADER_KEY_TYPE, this.type());
            if (priority != -1)
                outputHeaders.put(HEADER_KEY_PRIORITY, this.priority());
            if (redelivered)
                outputHeaders.put(HEADER_KEY_REDELIVERED, this.redelivered());

            byte[] headerBytes = mapper.writeValueAsBytes(outputHeaders);
            streamOut.writeInt(headerBytes.length);
            streamOut.writeInt(Protocol.createHashCode(headerBytes));
            streamOut.write(headerBytes);

            //ystem.out.println("header bytes length " + headerBytes.length);
            //ystem.out.println("header bytes hash " + Protocol.createHashCode(headerBytes));


            if (bodyBytes != null) {


                streamOut.writeInt(bodyBytes.length);
                streamOut.writeInt(Protocol.createHashCode(bodyBytes));
                //ystem.out.println("body bytes length " + bodyBytes.length);
                //ystem.out.println("body bytes hash " + Protocol.createHashCode(bodyBytes));
                streamOut.write(bodyBytes);
            } else {
                streamOut.write(0);
                streamOut.write(0);
            }


        } catch (Exception e) {
            throw new MessageException("Can't write out message", e);
        } finally {
            try {
                streamOut.close();
                baos.close();
            } catch (Exception e) {
                throw new MessageException("Can't write out message", e);
            }
        }
        return baos.toByteArray();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseMessageWithHeaders)) return false;
        BaseMessageWithHeaders that = (BaseMessageWithHeaders) o;
        return timestamp == that.timestamp &&
                expirationTime == that.expirationTime &&
                deliveryTime == that.deliveryTime &&
                mode == that.mode &&
                redelivered == that.redelivered &&
                priority == that.priority &&
                Objects.equals(type, that.type) && compareHeaders(that.headers);
        //&& Arrays.equals(bodyBytes, that.bodyBytes);
    }

    private boolean compareHeaders(Map<String, Object> thatHeaders) {
        if (headers == null && thatHeaders == null) return true;
        if (headers == null) return false;
        if (thatHeaders == null) return false;
        if (headers.size() != thatHeaders.size()) return false;

        for (String key : headers.keySet()) {
            Object value1 = thatHeaders.get(key);
            Object value2 = headers.get(key);
            if (value1 instanceof Number && value2 instanceof Number) {
                Number num1 = (Number) value1;
                Number num2 = (Number) value2;
                if (num1.doubleValue() != num2.doubleValue()) {
                    return false;
                }

            } else if (!value1.equals(value2)) {
                //ystem.out.println(key);
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(timestamp, expirationTime, deliveryTime, mode, type, redelivered, priority, headers);
        result = 31 * result + Arrays.hashCode(bodyBytes);
        return result;
    }

    public byte [] getMessageBytes() {
        return getMessageAsBytes();
    }

    @Override
    public String toString() {
        return "BaseMessageWithHeaders{" +
                "timestamp=" + timestamp +
                ", expirationTime=" + expirationTime +
                ", deliveryTime=" + deliveryTime +
                ", mode=" + mode +
                ", type='" + type + '\'' +
                ", redelivered=" + redelivered +
                ", priority=" + priority +
                ", headers=" + headers +
                ", bodyBytes=" + Arrays.toString(bodyBytes) +
                '}';
    }
}
