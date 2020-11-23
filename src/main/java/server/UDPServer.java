package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

import common.Packet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

public class UDPServer {

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    enum Type {
        ACK, NAK, SYN, SYNACK, DATA
    }

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


                        HashMap<Integer, Packet> resPacketList = new HashMap<>();
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
                                logger.info("GET ACK, establish connection");
                                continue loop1;
                            }

                            if (Type.values()[type].equals(Type.ACK)) {
                                logger.info("GET ACK, establish connection");
                            }

                            if (Type.values()[type].equals(Type.DATA)) {

                                if (new String(packet.getPayload(), StandardCharsets.UTF_8).equals("--HTTP END--")) {
                                    totalNumberOfPacket = (int) packet.getSequenceNumber() - 2;
                                } else {
                                    resPacketList.put((int) packet.getSequenceNumber(), packet);
                                }

                                logger.info("GET DATA");
                                resp = packet.toBuilder()
                                        .setType(Type.ACK.ordinal())
                                        .setSequenceNumber(packet.getSequenceNumber())
                                        .create();
                                channel.send(resp.toBuffer(), router);
                                logger.info("Send packet {}", resp);


                                if (totalNumberOfPacket == resPacketList.size()) {
                                    break;
                                }
                            }

                        }

                        StringBuilder httpReqStrBuilder = new StringBuilder();
                        for (int i = 2; i < resPacketList.size() + 2; i++) {
                            httpReqStrBuilder.append(new String(resPacketList.get(i).getPayload(), StandardCharsets.UTF_8));
                        }
                        System.out.println(httpReqStrBuilder.toString());

                        logger.info("收到了这些包{}", resPacketList);
                        logger.info("老子出来了");


                        //TODO：处理HTTP请求，返回hTTP response

//                    while(true){
//
//                    }
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
        }

    }

    public static void main(String[] args) throws IOException {

        int port = 8000;
        UDPServer server = new UDPServer();
        server.listenAndServe(port);
    }
}