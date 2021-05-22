package utils;

import exceptions.ArgsException;
import exceptions.ArgsException.Type;

public class IntParser {
    public static int parse(String value) throws ArgsException {
        try {
            return Integer.parseInt(value);
        } catch(NumberFormatException e) {
            throw new ArgsException(Type.FILE_KEY, value);
        }
    }    
}
