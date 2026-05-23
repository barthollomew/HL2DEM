package hl2dem.entity;

// Describes a single field within a serializer (entity class).
final class FieldSchema {

    final String name;
    final String typeName;
    final int bitCount;
    final float lowValue;
    final float highValue;
    final int encodeFlags;

    // If non-null, this field is an embedded sub-serializer.
    final SerializerSchema subSerializer;

    // If true, this is a fixed-size array field with a known element count.
    final int arrayLength;

    FieldSchema(String name, String typeName, int bitCount,
                float lowValue, float highValue, int encodeFlags,
                SerializerSchema subSerializer, int arrayLength) {
        this.name = name;
        this.typeName = typeName;
        this.bitCount = bitCount;
        this.lowValue = lowValue;
        this.highValue = highValue;
        this.encodeFlags = encodeFlags;
        this.subSerializer = subSerializer;
        this.arrayLength = arrayLength;
    }

    boolean isFloat() {
        return typeName.equals("float32")
            || typeName.equals("CNetworkedQuantizedFloat")
            || typeName.equals("QAngle");
    }

    boolean isVector() {
        return typeName.equals("Vector")
            || typeName.equals("Vector2D")
            || typeName.equals("Vector4D");
    }

    boolean isInt() {
        return typeName.startsWith("int") || typeName.startsWith("uint")
            || typeName.equals("bool") || typeName.equals("CHandle")
            || typeName.equals("CStrongHandle") || typeName.equals("CEntityIndex")
            || typeName.equals("Color") || typeName.equals("CGameSceneNodeHandle");
    }

    boolean isString() {
        return typeName.equals("CUtlString") || typeName.equals("CUtlSymbolLarge")
            || typeName.equals("char");
    }

    boolean isArray() {
        return arrayLength > 0;
    }
}
