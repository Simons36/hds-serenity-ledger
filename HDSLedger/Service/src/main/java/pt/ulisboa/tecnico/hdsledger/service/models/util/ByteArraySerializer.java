package pt.ulisboa.tecnico.hdsledger.service.models.util;

import com.google.gson.*;

import pt.ulisboa.tecnico.hdsledger.utilities.Util;

import java.lang.reflect.Type;

public class ByteArraySerializer implements JsonSerializer<byte[]> {

    @Override
    public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(Util.bytesToHex(src));
    }
}
