package messages;

import java.net.UnknownHostException;
import java.util.Arrays;

import configuration.ProtocolVersion;
import exceptions.ArgsException;
import exceptions.ArgsException.Type;
import messages.Message.MessageType;
import utils.IntParser;
import utils.Logger;
import utils.Logger.DebugType;

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

        Logger.debug(DebugType.MESSAGE, "Parsing message: " + new String(data).trim());

        String header = new String(Arrays.copyOf(data, headerEnd + 1));

        String[] headerPieces = header.split(" +"); // regex for spaces (works with multiple spaces)

        String version = headerPieces[0], messageType = headerPieces[1];

        MessageType type = getMessageType(messageType);

        int senderId = IntParser.parse(headerPieces[2]);
        Message message;

        if (needsFileKey(type)) {
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
        else
        {
            message = new Message(new ProtocolVersion(version), senderId);
            message.setMessageType(type);
            
            switch (type) {
                case GETPREDECESSOR: break;

                case NOTIFY: case PREDECESSOR:
                    if (type == MessageType.PREDECESSOR && headerPieces.length < 6) break;  // the successor doesn't have a predecessor
                    message.setNode(headerPieces[3], IntParser.parse(headerPieces[4]), IntParser.parse(headerPieces[5]));
                    break;
                
                default:
                    Logger.error("[MessageParser] Received a message without file key that I don't know how to parse.");
                    break;
            }
        }

        return message;
    }

    public static boolean needsFileKey(MessageType type) {
        return type == MessageType.CHUNK || type == MessageType.DELETE || type == MessageType.REMOVED 
            || type == MessageType.PUTCHUNK || type == MessageType.STORED || type == MessageType.LOOKUP || type == MessageType.FILECHECK || type == MessageType.GETCHUNK || type == MessageType.LOOKUPRESPONSE;
    }

    public static MessageType getMessageType(String type) throws ArgsException {
        for (MessageType value : Message.messageTypeStrings.keySet())
        {
            if (type.equals(Message.messageTypeStrings.get(value))) return value; 
        }
        throw new ArgsException(Type.MESSAGE_TYPE, type);
    }
}
