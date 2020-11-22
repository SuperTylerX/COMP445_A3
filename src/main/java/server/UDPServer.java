package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

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
                    logger.info("Get SYN, return SYN-ACK");
                    Packet resp = packet.toBuilder()
                            .setType(Type.SYNACK.ordinal())
                            .setSequenceNumber(packet.getSequenceNumber())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    logger.info("Send packet {}", resp);
                    loop:
                    while (true) {
                        buf.clear();
                        router = channel.receive(buf);
                        // Parse a packet from the received raw data.
                        buf.flip();
                        packet = Packet.fromBuffer(buf);
                        buf.flip();
                        type = packet.getType();
                        logger.info("Received Packet {} ", packet);

                        if (Type.values()[type].equals(Type.ACK)) {
                            logger.info("GET ACK, establish connection");

                            while (true) {
                                buf.clear();
                                router = channel.receive(buf);
                                // Parse a packet from the received raw data.
                                buf.flip();
                                packet = Packet.fromBuffer(buf);
                                buf.flip();
                                type = packet.getType();
                                logger.info("Received Packet {} ", packet);
                                if (Type.values()[type].equals(Type.DATA)) {
                                    logger.info("GET DATA");
                                    resp = packet.toBuilder()
                                            .setType(Type.ACK.ordinal())
                                            .setSequenceNumber(packet.getSequenceNumber())
                                            .create();
                                    channel.send(resp.toBuffer(), router);
                                    logger.info("Send packet {}", resp);


                                } else {
                                    //FIN
                                    break loop;
                                }
                            }
                        }
                    }


                }

//                String payload = new String(packet.getPayload(), UTF_8);
//                logger.info("Packet: {}", packet);
//                logger.info("Payload: {}", payload);
//                logger.info("Router: {}", router);
//
//                // Send the response to the router not the client.
//                // The peer address of the packet is the address of the client already.
//                // We can use toBuilder to copy properties of the current packet.
//                // This demonstrate how to create a new packet from an existing packet.
//                Packet resp = packet.toBuilder()
//                        .setPayload(payload.getBytes())
//                        .create();
//                channel.send(resp.toBuffer(), router);

            }
        }
    }

    public static void main(String[] args) throws IOException {

        int port = 8000;
        UDPServer server = new UDPServer();
        server.listenAndServe(port);
    }
}