package messages;

import java.net.UnknownHostException;
import java.util.Arrays;

import configuration.ProtocolVersion;
import exceptions.ArgsException;
import exceptions.ArgsException.Type;
import messages.Message.MessageType;
import utils.IntParser;
import utils.Logger;

public class MessageParser {

    public static Message parse(byte[] data, int length) throws ArgsException, UnknownHostException {
        int bodyStart = -1, headerEnd = -1;
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b == 0xD) {
                if (data[i+1] == 0xA && data[i+2] == 0xD && data[i+3] == 0xA) {
                    if (data.length > i + 4 && data[i + 3] != ' ') bodyStart = i + 4;
                    headerEnd = i - 1;
                    break;
                }
            }
        }

        String header = new String(Arrays.copyOf(data, headerEnd + 1));

        String[] headerPieces = header.split(" +"); // regex for spaces (works with multiple spaces)

        String version = headerPieces[0], messageType = headerPieces[1];

        MessageType type = getMessageType(messageType);

        int senderId = IntParser.parse(headerPieces[2]);
        Message message;

        try 
        {  // try to find a fileKey
            int fileKey = IntParser.parse(headerPieces[3]);
            message = new Message(new ProtocolVersion(version), senderId, fileKey);
            message.setMessageType(type);

            switch(type)
            {
                case PUTCHUNK:
                    message.setChunkNo(Integer.parseInt(headerPieces[4]));
                    message.setReplicationDeg((short) Integer.parseInt(headerPieces[5]));

                    byte[] body = Arrays.copyOfRange(data, bodyStart, length);
                    message.setBody(body);
                    break;

                case STORED:
                    message.setChunkNo(Integer.parseInt(headerPieces[4]));
                    break;

                case GETCHUNK:   
                    message.setChunkNo(Integer.parseInt(headerPieces[4]));
                    break;
                
                case CHUNK:
                    message.setChunkNo(Integer.parseInt(headerPieces[4]));

                    byte[] chunkBody = Arrays.copyOfRange(data, bodyStart, length);
                    message.setBody(chunkBody);
                    break;

                case REMOVED:   
                    message.setChunkNo(Integer.parseInt(headerPieces[4]));   
                    break;

                case LOOKUPRESPONSE:
                    message.setNode(headerPieces[4], IntParser.parse(headerPieces[5]), IntParser.parse(headerPieces[6]));
                    break;

                case DELETE: case LOOKUP: case FILECHECK:
                    break;

                default:
                    Logger.error("[MessageParser] Received a message with file key that I don't know how to parse.");
                    break;
            }
        } 
        catch (Exception e)  // if didn't find a file key
        {
            message = new Message(new ProtocolVersion(version), senderId);
            message.setMessageType(type);
            
            switch (type) {
                case GETPREDECESSOR: case CHECK: case NOTIFYPREDECESSOR:
                    break;
                
                default:
                    Logger.error("[MessageParser] Received a message without file key that I don't know how to parse.");
                    break;
            }
        }

        return message;
    }

    public static MessageType getMessageType(String type) throws ArgsException {
        for (MessageType value : Message.messageTypeStrings.keySet())
        {
            if (type.equals(Message.messageTypeStrings.get(value))) return value; 
        }
        throw new ArgsException(Type.MESSAGE_TYPE, type);
    }
}
