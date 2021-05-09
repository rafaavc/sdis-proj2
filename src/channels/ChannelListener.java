package channels;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import channels.handlers.Handler;
import messages.Message;
import messages.MessageParser;
import utils.Logger;

public class ChannelListener extends Thread {
    private final MulticastChannel channel;
    private final Handler action;
    private final ScheduledThreadPoolExecutor threadScheduler;

    public ChannelListener(MulticastChannel channel, Handler action, ScheduledThreadPoolExecutor threadScheduler) {
        this.channel = channel;
        this.action = action;
        this.threadScheduler = threadScheduler;
    }

    @Override
    public void run() {
        MulticastSocket socket = channel.getSocket();
        
        try {
            socket.joinGroup(channel.getHost());

            while (true) {
                byte[] rbuf = new byte[65000];
                DatagramPacket packet = new DatagramPacket(rbuf, rbuf.length);

                socket.receive(packet);
                threadScheduler.execute(new Runnable() {
                    @Override
                    public void run() {
                        byte[] data = packet.getData();

                        try 
                        {
                            Message msg = MessageParser.parse(data, packet.getLength());
                            
                            Logger.log(channel.getType(), msg);

                            if (msg.getSenderId() == action.getConfiguration().getPeerId()) return;
                            action.execute(msg, packet.getAddress());
                        } 
                        catch(Exception e) 
                        {
                            Logger.error("Received invalid message: " + e);
                        }
                    }
                });
            }
        } catch (IOException e) {
            if (!(e instanceof SocketException)) {
                Logger.error("IOException in ChannelListener of channel " + this.channel + ": " + e.getMessage());
            }
        }
    }
}
