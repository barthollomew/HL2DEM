package hl2dem.entity;

// Describes one entity class (serializer) and its ordered field list.
final class SerializerSchema {

    final String name;
    final int version;
    final FieldSchema[] fields;

    SerializerSchema(String name, int version, FieldSchema[] fields) {
        this.name = name;
        this.version = version;
        this.fields = fields;
    }
}
