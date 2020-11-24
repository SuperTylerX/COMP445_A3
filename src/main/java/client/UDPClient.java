package client;

import common.Packet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.UDPServer;

import static java.nio.channels.SelectionKey.OP_READ;


public class UDPClient {

    enum Type {
        ACK, NAK, SYN, SYNACK, DATA
    }

    static long seq = 0;
    static long basedSeq = 0;


    public static void main(String[] args) {
        // Router address
        String routerHost = "localhost";
        int routerPort = 3000;

        // Server address
        String serverHost = "localhost";
        int serverPort = 8000;

        String msg = "POST /cgi-bin/process.cgi HTTP/1.1r\r\n" +
                "User-Agent: Mozilla/4.0 (compatible; MSIE5.01; Windows NT)\r\n" +
                "Host: www.tutorialspoint.com\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "Content-Length: 49\r\n" +
                "Accept-Language: en-us\r\n" +
                "Accept-Encoding: gzip, deflate\r\n" +
                "Connection: Keep-Alive\r\n" +
                "\r\n" +
                "licenseID=string&content=string&/paramsXML=string";

        SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);

        runClient(routerAddress, serverAddress, msg);
    }

    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);
    private static final int TIMEOUT = 500;
    private static final int WINDOW_SIZE = 4;

    private static void runClient(SocketAddress routerAddr, InetSocketAddress serverAddr, String msg) {

        try (DatagramChannel channel = DatagramChannel.open()) {


            // 3 way handshaking

            Packet p = new Packet.Builder()
                    .setType(Type.SYN.ordinal())
                    .setSequenceNumber(getSeq())
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload("This is SYN".getBytes())
                    .create();

            logger.info("Sending SYN packet to router at {}", routerAddr);
            channel.send(p.toBuffer(), routerAddr);
            logger.info("Packet {}", p);

            // Try to receive a packet within timeout.
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            logger.info("Waiting for the response");
            selector.select(TIMEOUT);

            Set<SelectionKey> keys = selector.selectedKeys();

            while (true) {
                while (keys.isEmpty()) {
                    logger.error("No response after timeout");
                    logger.info("Resending SYN packet to server");
                    channel.send(p.toBuffer(), routerAddr);
                    logger.info("Packet {}", p);

                    // Try to receive a packet within timeout.
                    channel.configureBlocking(false);
                    selector = Selector.open();
                    channel.register(selector, OP_READ);
                    logger.info("Waiting for the response");
                    selector.select(TIMEOUT);
                    keys = selector.selectedKeys();
                }

                ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                SocketAddress router = channel.receive(buf);
                buf.flip();
                Packet resp = Packet.fromBuffer(buf);
                logger.info("Received Packet: {}", resp);

                // If the packet is SYN-ACK
                if (Type.values()[resp.getType()].equals(Type.SYNACK)) {
                    logger.info("Sending ACK for SYN-ACK packet to router at {}", routerAddr);

                    p = new Packet.Builder()
                            .setType(Type.ACK.ordinal())
                            .setSequenceNumber(getSeq())
                            .setPortNumber(serverAddr.getPort())
                            .setPeerAddress(serverAddr.getAddress())
                            .setPayload("ACK for SYNACK".getBytes())
                            .create();

                    channel.send(p.toBuffer(), routerAddr);
                    logger.info("Packet {}", p);


                    // 3 way handshaking done, start to send HTTP request
                    logger.info("Send HTTP request...");


                    // Split the Http request into packets
//                    List<String> splitHttpRequestStr = getStrList(msg, (Packet.MAX_LEN - Packet.MIN_LEN) / 2);
                    List<String> splitHttpRequestStr = Utils.getStrList(msg, 20);
                    List<Packet> reqPacketList = new ArrayList<>();
                    for (String str : splitHttpRequestStr) {
                        p = new Packet.Builder()
                                .setType(Type.DATA.ordinal())
                                .setSequenceNumber(getSeq())
                                .setPortNumber(serverAddr.getPort())
                                .setPeerAddress(serverAddr.getAddress())
                                .setPayload(str.getBytes())
                                .create();
                        reqPacketList.add(p);
                    }
                    p = new Packet.Builder()
                            .setType(Type.DATA.ordinal())
                            .setSequenceNumber(getSeq())
                            .setPortNumber(serverAddr.getPort())
                            .setPeerAddress(serverAddr.getAddress())
                            .setPayload("--HTTP END--".getBytes())
                            .create();
                    reqPacketList.add(p);
                    basedSeq = p.getSequenceNumber() + 1;

                    logger.info("Packet length {}", reqPacketList.size());
                    logger.info("Last packet seq should be {}", reqPacketList.get(reqPacketList.size() - 1).getSequenceNumber());

                    int windowStart = 0;

                    while (true) {

                        int cnt = 0;
                        for (int i = windowStart; i < Math.min(windowStart + WINDOW_SIZE, reqPacketList.size()); i++) {
                            Packet packet = reqPacketList.get(i);
                            if (!packet.isACK()) {
                                channel.send(packet.toBuffer(), routerAddr);
                                logger.info("上来了");
                                logger.info("Send Packet{} , windowStart {}, windowEnd {}", packet, windowStart + 2, windowStart + WINDOW_SIZE + 1);
                                cnt++;
                            }
                        }

                        for (int j = 0; j < cnt; j++) {
                            channel.configureBlocking(false);
                            selector = Selector.open();
                            channel.register(selector, OP_READ);
                            selector.select(TIMEOUT);
                            keys = selector.selectedKeys();

                            if (keys.isEmpty()) {
                                logger.error("No response after timeout");
                            } else {
                                buf = ByteBuffer.allocate(Packet.MAX_LEN);
                                router = channel.receive(buf);
                                buf.flip();
                                resp = Packet.fromBuffer(buf);
                                logger.info("Received Packet: {}", resp);

                                // Set the packet to ACK
                                for (Packet item : reqPacketList) {
                                    if (item.getSequenceNumber() == resp.getSequenceNumber()) {
                                        logger.info("ACK {}", item.getSequenceNumber());
                                        item.setACK(true);
                                        break;
                                    }
                                }

                                // Move window forward
                                if (resp.getSequenceNumber() - 2 == windowStart) {
                                    for (int i = windowStart; i < Math.min(windowStart + WINDOW_SIZE, reqPacketList.size()); i++) {
                                        if (reqPacketList.get(i).isACK()) {
                                            logger.info("ACK是{}", reqPacketList.get(i));
                                            windowStart = i + 1;
                                            // Send next packet
                                            if (windowStart + WINDOW_SIZE <= reqPacketList.size()) {
                                                Packet packet = reqPacketList.get(windowStart + WINDOW_SIZE - 1);
                                                channel.send(packet.toBuffer(), routerAddr);
                                                logger.info("Send Packet{} , windowStart {}, windowEnd {}", packet, windowStart + 2, windowStart + WINDOW_SIZE + 1);
                                                j--;
                                            }
                                        } else {
                                            break;
                                        }
                                    }
                                }

                            }
                        }


                        // Check all packets are ACK
                        boolean flag = true;
                        for (Packet item : reqPacketList) {
                            if (!item.isACK()) {
                                flag = false;
                                break;
                            }
                        }
                        if (flag) {
                            break;
                        }

                    }
                    logger.info("Start to Receive Http Response");
                    // Start to receive HTTP response

                    HashMap<Integer, Packet> resPacketList = new HashMap<>();
                    int totalNumberOfPacket = 0;

                    while (true) {

                        channel.configureBlocking(false);
                        selector = Selector.open();
                        channel.register(selector, OP_READ);
                        selector.select(TIMEOUT);
                        keys = selector.selectedKeys();

                        if (keys.isEmpty()) {
                            logger.error("No response after timeout");
                        } else {
                            buf.clear();
                            router = channel.receive(buf);
                            // Parse a packet from the received raw data.
                            buf.flip();
                            Packet packet = Packet.fromBuffer(buf);
                            buf.flip();
                            int type = packet.getType();

                            logger.info("Received Packet {} ", packet);

                            if (Type.values()[type].equals(Type.DATA)) {

                                if (new String(packet.getPayload(), StandardCharsets.UTF_8).equals("--HTTP END--")) {
                                    totalNumberOfPacket = (int) packet.getSequenceNumber() - (int) basedSeq + 1;
                                    logger.info("totalNumberOfPacket {}, SequenceNumber{}, basedSeq {}", totalNumberOfPacket, packet.getSequenceNumber(), basedSeq);
                                }
                                resPacketList.put((int) packet.getSequenceNumber(), packet);

                                resp = packet.toBuilder()
                                        .setType(Type.ACK.ordinal())
                                        .setSequenceNumber(packet.getSequenceNumber())
                                        .create();
                                channel.send(resp.toBuffer(), router);
                                logger.info("Send packet {}", resp);

                                logger.info("resPacketLis {}", resPacketList);
                                if (totalNumberOfPacket == resPacketList.size()) {
                                    break;
                                }
                            }
                        }

                    }

                    logger.info("收到所有包了");
                    StringBuilder httpResStrBuilder = new StringBuilder();
                    for (long i = basedSeq; i < resPacketList.size() + basedSeq - 1; i++) {
                        httpResStrBuilder.append(new String(resPacketList.get((int) i).getPayload(), StandardCharsets.UTF_8));
                    }
                    System.out.println(httpResStrBuilder.toString());



                    break;
                } else {
                    logger.info("Received None SYN-ACK packet...");
                }
            }


            keys.clear();


        } catch (IOException e) {
            e.printStackTrace();
        }
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
