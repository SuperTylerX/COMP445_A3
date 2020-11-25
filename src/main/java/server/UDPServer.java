package server;

import common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;

import common.Packet;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

public class UDPServer {

    enum Type {
        ACK, NAK, SYN, SYNACK, DATA, FIN, FINACK
    }

    static long seq = 0;
    static long basedSeq = 0;
    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);
    private static final int TIMEOUT = 500;
    private static final int WINDOW_SIZE = 4;

    private void listenAndServe(int port) throws IOException {

        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            logger.info("EchoServer is listening at {}", channel.getLocalAddress());
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            while (true) {
                buf.clear();
                SocketAddress router = channel.receive(buf);
                // Parse a packet from the received raw data.
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);
                buf.flip();
                int type = packet.getType();

                logger.info("Received Packet {} ", packet);
                if (Type.values()[type].equals(Type.SYN)) {
                    loop1:
                    while (true) {
                        logger.info("Get SYN, return SYN-ACK");
                        Packet resp = packet.toBuilder()
                                .setType(Type.SYNACK.ordinal())
                                .setSequenceNumber(packet.getSequenceNumber())
                                .create();
                        channel.send(resp.toBuffer(), router);
                        logger.info("Send packet {}", resp);


                        HashMap<Integer, Packet> reqPacketList = new HashMap<>();
                        int totalNumberOfPacket = 0;
                        while (true) {
                            buf.clear();
                            router = channel.receive(buf);
                            // Parse a packet from the received raw data.
                            buf.flip();
                            packet = Packet.fromBuffer(buf);
                            buf.flip();
                            type = packet.getType();
                            logger.info("Received Packet {} ", packet);

                            if (Type.values()[type].equals(Type.SYN)) {
                                logger.info("GET SYN, restart the process");
                                continue loop1;
                            }

                            if (Type.values()[type].equals(Type.ACK)) {
                                logger.info("GET ACK, establish connection");
                            }

                            if (Type.values()[type].equals(Type.DATA)) {

                                if (new String(packet.getPayload(), StandardCharsets.UTF_8).equals("--HTTP END--")) {
                                    totalNumberOfPacket = (int) packet.getSequenceNumber() - 1;
                                    seq = packet.getSequenceNumber() + 1;
                                    basedSeq = packet.getSequenceNumber() + 1;
                                }

                                reqPacketList.put((int) packet.getSequenceNumber(), packet);

                                logger.info("GET DATA");
                                resp = packet.toBuilder()
                                        .setType(Type.ACK.ordinal())
                                        .setSequenceNumber(packet.getSequenceNumber())
                                        .create();
                                channel.send(resp.toBuffer(), router);
                                logger.info("Send packet {}", resp);

                                if (totalNumberOfPacket == reqPacketList.size()) {
                                    break;
                                }
                            }

                        }

                        StringBuilder httpReqStrBuilder = new StringBuilder();
                        for (int i = 2; i < reqPacketList.size() + 1; i++) {
                            httpReqStrBuilder.append(new String(reqPacketList.get(i).getPayload(), StandardCharsets.UTF_8));
                        }
                        System.out.println(httpReqStrBuilder.toString());

                        logger.info("Received All the Http Request Packet...");
                        logger.info("Start to processing HTTP Request...");

                        String msg = "HTTP/1.1 404 Not Found\r\n" +
                                "Date: Sun, 18 Oct 2012 10:36:20 GMT\r\n" +
                                "Server: Apache/2.2.14 (Win32)\r\n" +
                                "Content-Length: 230\r\n" +
                                "Connection: Closed\r\n" +
                                "Content-Type: text/html; charset=iso-8859-1\r\n" +
                                "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\r\n\r\n" +
                                "<html>\r\n" +
                                "<head>\r\n" +
                                "   <title>404 Not Found</title>\r\n" +
                                "</head>\r\n" +
                                "<body>\r\n" +
                                "   <h1>Not Found</h1>\r\n" +
                                "   <p>The requested URL /t.html was not found on this server.</p>\r\n" +
                                "</body>\r\n" +
                                "</html>\r\n\r\n";

                        logger.info("Process Done!");
                        logger.info("Start to sending HTTP Response");
                        List<String> splitHttpResponseStr = Utils.getStrList(msg, 20);
                        List<Packet> resPacketList = new ArrayList<>();

                        for (String str : splitHttpResponseStr) {
                            Packet p = packet.toBuilder()
                                    .setType(Type.DATA.ordinal())
                                    .setSequenceNumber(getSeq())
                                    .setPayload(str.getBytes())
                                    .create();
                            resPacketList.add(p);
                        }
                        Packet p = packet.toBuilder()
                                .setType(Type.DATA.ordinal())
                                .setSequenceNumber(getSeq())
                                .setPayload("--HTTP END--".getBytes())
                                .create();
                        resPacketList.add(p);

                        logger.info("Packet length {}", resPacketList.size());
                        logger.info("Last packet seq should be {}", resPacketList.get(resPacketList.size() - 1).getSequenceNumber());

                        int windowStart = 0;

                        loop3:
                        while (true) {
                            loop2:
                            while (true) {
                                logger.info("最顶上");
                                int cnt = 0;
                                for (int i = windowStart; i < Math.min(windowStart + WINDOW_SIZE, resPacketList.size()); i++) {
                                    packet = resPacketList.get(i);
                                    if (!packet.isACK()) {
                                        channel.send(packet.toBuffer(), router);
                                        logger.info("上来了");
                                        logger.info("Send Packet{} , windowStart {}, windowEnd {}", packet, windowStart + basedSeq, windowStart + WINDOW_SIZE + basedSeq - 1);
                                        cnt++;
                                    }
                                }

                                for (int j = 0; j < cnt; j++) {
                                    channel.configureBlocking(false);
                                    Selector selector = Selector.open();
                                    channel.register(selector, OP_READ);
                                    selector.select(TIMEOUT);
                                    Set<SelectionKey> keys = selector.selectedKeys();

                                    if (keys.isEmpty()) {
                                        selector.close();
                                        logger.error("No response after timeout");
                                    } else {
                                        buf = ByteBuffer.allocate(Packet.MAX_LEN);
                                        router = channel.receive(buf);
                                        buf.flip();
                                        resp = Packet.fromBuffer(buf);
                                        logger.info("Received Packet: {}", resp);

                                        if (Type.values()[resp.getType()].equals(Type.DATA)) {
                                            logger.info("Received Redundant Packet, Send ACK");
                                            packet = resp.toBuilder()
                                                    .setType(Type.ACK.ordinal())
                                                    .setSequenceNumber(resp.getSequenceNumber())
                                                    .create();
                                            channel.send(packet.toBuffer(), router);
                                            selector.close();
                                            continue;
                                        }
                                        if (Type.values()[resp.getType()].equals(Type.FIN)) {
                                            logger.info("Received FIN");
                                            selector.close();
                                            break loop3;
                                        }
                                        // Set the packet to ACK
                                        for (Packet item : resPacketList) {
                                            if (item.getSequenceNumber() == resp.getSequenceNumber()) {
                                                logger.info("ACK {}", item.getSequenceNumber());
                                                item.setACK(true);
                                                break;
                                            }
                                        }

                                        // Move window forward
                                        if (resp.getSequenceNumber() - basedSeq == windowStart) {
                                            for (int i = windowStart; i < Math.min(windowStart + WINDOW_SIZE, resPacketList.size()); i++) {
                                                if (resPacketList.get(i).isACK()) {
                                                    logger.info("ACK是{}", resPacketList.get(i));
                                                    windowStart = i + 1;
                                                    // Send next packet
                                                    if (windowStart + WINDOW_SIZE <= resPacketList.size()) {
                                                        packet = resPacketList.get(windowStart + WINDOW_SIZE - 1);
                                                        channel.send(packet.toBuffer(), router);
                                                        logger.info("Send Packet{} , windowStart {}, windowEnd {}", packet, windowStart + basedSeq, windowStart + WINDOW_SIZE + basedSeq - 1);
                                                        j--;
                                                    }
                                                } else {
                                                    break;
                                                }
                                            }
                                        }

                                    }

                                    selector.close();
                                }

                                channel.configureBlocking(true);
                                // Check all packets are ACK
                                boolean flag = true;
                                for (Packet item : resPacketList) {
                                    if (!item.isACK()) {
                                        flag = false;
                                        break;
                                    }
                                }
                                if (flag) {
                                    break;
                                }
                            }
                            // Waiting for FIN
                            buf.clear();
                            router = channel.receive(buf);
                            // Parse a packet from the received raw data.
                            buf.flip();
                            resp = Packet.fromBuffer(buf);
                            buf.flip();
                            type = resp.getType();

                            logger.info("Received Packet {} ", resp);
                            if (Type.values()[type].equals(Type.FIN)) {
                                break;
                            }
                        }

//                        channel.configureBlocking(true);
                        // If the packet is FIN send FIN-ACK
                        if (Type.values()[resp.getType()].equals(Type.FIN)) {

                            p = resp.toBuilder()
                                    .setType(Type.FINACK.ordinal())
                                    .setSequenceNumber(resp.getSequenceNumber())
                                    .create();

                            channel.send(p.toBuffer(), router);
                            logger.info("Send FINACK");
                            logger.info("Packet {}", p);

                            Date timerStart = new Date();
                            boolean fin=false;
                            while (true) {
                                Date timerEnd = new Date();
                                if (timerEnd.getTime() - timerStart.getTime() > 5000){
                                    logger.info("Timeout, close the connection");
                                    break ;
                                }
                                channel.configureBlocking(true);
                                buf.clear();
                                router = channel.receive(buf);
                                // Parse a packet from the received raw data.
                                buf.flip();
                                resp = Packet.fromBuffer(buf);
                                buf.flip();
                                type = packet.getType();

                                logger.info("Received Packet: {}", resp);

                                if (Type.values()[resp.getType()].equals(Type.FIN)) {
                                    p = resp.toBuilder()
                                            .setType(Type.FINACK.ordinal())
                                            .setSequenceNumber(resp.getSequenceNumber())
                                            .create();

                                    channel.send(p.toBuffer(), router);
                                    timerStart = new Date();
                                    logger.info("Send Packet {}", p);
                                } else if (Type.values()[resp.getType()].equals(Type.ACK)) {
                                    logger.info("GET ACK, close the connection!");
                                    break loop1;
                                }
                            }


                        }

                    }


                } else {
                    Packet resp = packet.toBuilder()
                            .setType(Type.ACK.ordinal())
                            .setSequenceNumber(packet.getSequenceNumber())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    logger.info("Send packet {}", resp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) throws IOException {

        int port = 8000;
        UDPServer server = new UDPServer();
        server.listenAndServe(port);
    }


    public static long getSeq() {
        long returnValue = seq;
        seq++;
        if (seq == 100) {
            seq = 0;
        }
        return returnValue;
    }

}