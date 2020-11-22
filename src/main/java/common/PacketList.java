package common;

import java.util.ArrayList;

public class PacketList {
    public ArrayList<Packet> packetList;
    public int windowSize;
    public int startPtr;

    public PacketList(int windowSize) {
        this.windowSize = windowSize;
        this.startPtr = 0;
        packetList = new ArrayList<>();
    }

    public void add(Packet p) {
        packetList.add(p);
    }
}
