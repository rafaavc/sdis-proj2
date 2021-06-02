package messages;

import java.net.UnknownHostException;
import java.util.Arrays;

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

        if (headerPieces.length < 3) throw new ArgsException(Type.INVALID_MESSAGE);

        String messageType = headerPieces[0];

        MessageType type = MessageType.valueOf(messageType);

        int senderId = IntParser.parse(headerPieces[1]);
        Message message;

        if (needsFileKey(type)) {
            int fileKey = IntParser.parse(headerPieces[2]);
            message = new Message(senderId, fileKey);
            message.setMessageType(type);

            switch(type)
            {
                case PUTFILE:
                    message.setOrder(Integer.parseInt(headerPieces[3]))
                        .setReplicationDeg((short) Integer.parseInt(headerPieces[4]))
                        .setAlreadyPerceivedDegree((short) Integer.parseInt(headerPieces[5]))
                        .setByteAmount(Integer.parseInt(headerPieces[6]));

                    byte[] body = Arrays.copyOfRange(data, bodyStart, length);
                    message.setBody(body);
                    break;

                case DATA:
                    byte[] chunkBodyData = Arrays.copyOfRange(data, bodyStart, length);
                    message.setOrder(Integer.parseInt(headerPieces[3]))
                        .setBody(chunkBodyData);
                    break;

                case STORED: case REMOVED: case DELETE: case LOOKUP: case FILECHECK: case REMOVEPOINTER: case ADDPOINTER:
                    break;

                case CHUNK:
                    byte[] chunkBody = Arrays.copyOfRange(data, bodyStart, length);
                    message.setBody(chunkBody);
                    break;

                case LOOKUPRESPONSE: case GETFILE:
                    message.setNode(headerPieces[3], IntParser.parse(headerPieces[4]), IntParser.parse(headerPieces[5]));
                    break;

                default:
                    Logger.error("[MessageParser] Received a message with file key that I don't know how to parse.");
                    break;
            }
        } 
        else
        {
            message = new Message(senderId);
            message.setMessageType(type);
            
            switch (type) {
                case GETPREDECESSOR: case PROCESSEDNO: case PROCESSEDYES: case GETSUCCESSOR: break;

                case NOTIFY: case NODE: case REDIRECT:
                    if (type == MessageType.NODE && headerPieces.length < 5) break;  // is null
                    message.setNode(headerPieces[2], IntParser.parse(headerPieces[3]), IntParser.parse(headerPieces[4]));
                    break;
                
                default:
                    Logger.error("[MessageParser] Received a message without file key that I don't know how to parse.");
                    break;
            }
        }

        return message;
    }

    public static boolean needsFileKey(MessageType type) {
        return type == MessageType.CHUNK || type == MessageType.DELETE || type == MessageType.REMOVED || type == MessageType.DATA || type == MessageType.ADDPOINTER || type == MessageType.REMOVEPOINTER
            || type == MessageType.PUTFILE || type == MessageType.STORED || type == MessageType.LOOKUP || type == MessageType.FILECHECK || type == MessageType.GETFILE || type == MessageType.LOOKUPRESPONSE;
    }
}
