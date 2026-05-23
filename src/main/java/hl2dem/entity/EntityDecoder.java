package hl2dem.entity;

import valve.pb.NetmessagesProto;
import valve.pb.DemoProto;
import com.google.protobuf.CodedInputStream;
import hl2dem.frame.DemoCommand;

public final class EntityDecoder {

    private static final HuffmanTree HUFFMAN = new HuffmanTree(FieldPathOp.ALL);

    private SerializerSchema[] serializers;
    private String[] symbols;

    // Per-entity class assignment: entityId -> class ID from PacketEntities.
    private final int[] entityClassId = new int[EntityContext.MAX_ENTITIES];
    // ClassInfo mapping: class ID -> index into serializers[]. -1 = unknown.
    private final int[] classIdToSerializer = new int[8192];

    private final EntityContext context;
    private boolean tickComplete;

    // Mutable path state reused per-entity to avoid allocation on hot path.
    private final int[] path = new int[7];
    private int depth;

    public EntityDecoder(EntityContext context) {
        this.context = context;
        java.util.Arrays.fill(entityClassId, -1);
        java.util.Arrays.fill(classIdToSerializer, -1);
    }

    public void handle(int cmd, int tick, byte[] data, int len) {
        tickComplete = false;
        try {
            if (cmd == DemoCommand.SEND_TABLES) {
                handleSendTables(data, len);
            } else if (cmd == DemoCommand.CLASS_INFO) {
                handleClassInfo(data, len);
            } else if (cmd == DemoCommand.PACKET || cmd == DemoCommand.SIGNON_PACKET) {
                handlePacket(data, len, tick);
                tickComplete = true;
            }
        } catch (Exception e) {
            System.err.println("warn: entity decode error at tick " + tick + ": " + e.getMessage());
        }
    }

    public boolean tickComplete() {
        return tickComplete;
    }

    // ---- Schema building from DEM_SEND_TABLES ----

    private void handleSendTables(byte[] data, int len) throws java.io.IOException {
        DemoProto.CDemoSendTables msg = DemoProto.CDemoSendTables.parseFrom(
            CodedInputStream.newInstance(data, 0, len));
        if (!msg.hasData()) return;

        NetmessagesProto.CSVCMsg_FlattenedSerializer fs =
            NetmessagesProto.CSVCMsg_FlattenedSerializer.parseFrom(msg.getData());

        symbols = new String[fs.getSymbolsCount()];
        for (int i = 0; i < fs.getSymbolsCount(); i++) {
            symbols[i] = fs.getSymbols(i);
        }

        // Build all field descriptors first (they reference each other by index).
        FieldSchema[] allFields = new FieldSchema[fs.getFieldsCount()];
        for (int i = 0; i < fs.getFieldsCount(); i++) {
            allFields[i] = buildField(fs.getFields(i), null); // sub-serializers resolved below
        }

        // Build serializer list so sub-serializer references can be resolved.
        serializers = new SerializerSchema[fs.getSerializersCount()];
        for (int i = 0; i < fs.getSerializersCount(); i++) {
            NetmessagesProto.ProtoFlattenedSerializer_t s = fs.getSerializers(i);
            String name = sym(s.getSerializerNameSym());
            int version = s.hasSerializerVersion() ? s.getSerializerVersion() : 0;
            FieldSchema[] fields = new FieldSchema[s.getFieldsIndexCount()];
            for (int j = 0; j < s.getFieldsIndexCount(); j++) {
                fields[j] = allFields[s.getFieldsIndex(j)];
            }
            serializers[i] = new SerializerSchema(name, version, fields);
        }

        // Second pass: resolve sub-serializer references now that serializers[] is populated.
        for (int i = 0; i < fs.getFieldsCount(); i++) {
            NetmessagesProto.ProtoFlattenedSerializerField_t f = fs.getFields(i);
            if (f.hasFieldSerializerNameSym()) {
                String subName = sym(f.getFieldSerializerNameSym());
                int subVer = f.hasFieldSerializerVersion() ? f.getFieldSerializerVersion() : 0;
                SerializerSchema sub = findSerializer(subName, subVer);
                if (sub != null) {
                    allFields[i] = rebuildWithSub(allFields[i], sub);
                }
            }
        }
        // Rebuild serializer field arrays to pick up the resolved sub-serializers.
        for (int i = 0; i < fs.getSerializersCount(); i++) {
            NetmessagesProto.ProtoFlattenedSerializer_t s = fs.getSerializers(i);
            FieldSchema[] fields = serializers[i].fields;
            for (int j = 0; j < s.getFieldsIndexCount(); j++) {
                fields[j] = allFields[s.getFieldsIndex(j)];
            }
        }
    }

    private FieldSchema buildField(NetmessagesProto.ProtoFlattenedSerializerField_t f,
                                   SerializerSchema sub) {
        String typeName = sym(f.getVarTypeSym());
        String varName = sym(f.getVarNameSym());
        int bitCount = f.hasBitCount() ? f.getBitCount() : 0;
        float low = f.hasLowValue() ? f.getLowValue() : 0f;
        float high = f.hasHighValue() ? f.getHighValue() : 1f;
        int flags = f.hasEncodeFlags() ? f.getEncodeFlags() : 0;

        int arrayLen = 0;
        if (typeName.endsWith("]")) {
            int bracket = typeName.lastIndexOf('[');
            if (bracket >= 0) {
                try {
                    arrayLen = Integer.parseInt(typeName.substring(bracket + 1, typeName.length() - 1));
                    typeName = typeName.substring(0, bracket).trim();
                } catch (NumberFormatException ignored) {}
            }
        }

        return new FieldSchema(varName, typeName, bitCount, low, high, flags, sub, arrayLen);
    }

    private FieldSchema rebuildWithSub(FieldSchema f, SerializerSchema sub) {
        return new FieldSchema(f.name, f.typeName, f.bitCount, f.lowValue,
                               f.highValue, f.encodeFlags, sub, f.arrayLength);
    }

    private SerializerSchema findSerializer(String name, int version) {
        if (serializers == null) return null;
        for (SerializerSchema s : serializers) {
            if (s.name.equals(name) && s.version == version) return s;
        }
        // Fall back to any version match if exact not found.
        for (SerializerSchema s : serializers) {
            if (s.name.equals(name)) return s;
        }
        return null;
    }

    private String sym(int idx) {
        if (symbols == null || idx < 0 || idx >= symbols.length) return "";
        return symbols[idx];
    }

    // ---- ClassInfo: maps class IDs to serializer indices ----

    private void handleClassInfo(byte[] data, int len) throws java.io.IOException {
        DemoProto.CDemoClassInfo msg = DemoProto.CDemoClassInfo.parseFrom(
            CodedInputStream.newInstance(data, 0, len));
        if (serializers == null) return;
        for (DemoProto.CDemoClassInfo.class_t cls : msg.getClassesList()) {
            int id = cls.getClassId();
            String name = cls.getNetworkName();
            if (id < 0 || id >= classIdToSerializer.length) continue;
            for (int i = 0; i < serializers.length; i++) {
                if (serializers[i].name.equals(name)) {
                    classIdToSerializer[id] = i;
                    break;
                }
            }
        }
    }

    // ---- Packet frame: extract embedded SVC messages ----

    private void handlePacket(byte[] data, int len, int tick) throws java.io.IOException {
        DemoProto.CDemoPacket msg = DemoProto.CDemoPacket.parseFrom(
            CodedInputStream.newInstance(data, 0, len));
        if (!msg.hasData()) return;

        byte[] inner = msg.getData().toByteArray();
        int pos = 0;
        while (pos < inner.length) {
            int cmdType = readInnerVarint(inner, pos);
            pos += varintSize(inner, pos);
            int size = readInnerVarint(inner, pos);
            pos += varintSize(inner, pos);

            if (cmdType == 55) { // svc_PacketEntities
                handlePacketEntities(inner, pos, size, tick);
            }
            pos += size;
            if (pos > inner.length) break;
        }
    }

    private static int readInnerVarint(byte[] buf, int pos) {
        int val = 0, shift = 0;
        while (pos < buf.length) {
            int b = buf[pos++] & 0xFF;
            val |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return val;
            shift += 7;
        }
        return val;
    }

    private static int varintSize(byte[] buf, int pos) {
        int count = 0;
        while (pos + count < buf.length) {
            if ((buf[pos + count++] & 0x80) == 0) return count;
        }
        return count;
    }

    // ---- Entity delta bit-stream decode ----

    private void handlePacketEntities(byte[] data, int offset, int len, int tick)
            throws java.io.IOException {
        if (serializers == null) return;

        NetmessagesProto.CSVCMsg_PacketEntities msg =
            NetmessagesProto.CSVCMsg_PacketEntities.parseFrom(
                CodedInputStream.newInstance(data, offset, len));
        if (!msg.hasEntityData()) return;

        byte[] entityData = msg.getEntityData().toByteArray();
        int updatedEntries = msg.hasUpdatedEntries() ? msg.getUpdatedEntries() : 0;

        BitReader br = new BitReader(entityData, entityData.length);
        int entityId = -1;

        for (int i = 0; i < updatedEntries; i++) {
            entityId += 1 + br.readUBitVarFP();

            boolean b0 = br.readBool();
            boolean b1 = br.readBool();

            if (!b0 && !b1) {
                // Update existing entity.
                decodeEntityUpdate(br, entityId, tick);
            } else if (!b0) {
                // Enter PVS - new entity. Read class ID and serial.
                int classId = br.readBits(classBits());
                br.readBits(17); // serial
                entityClassId[entityId] = classId;
                decodeEntityUpdate(br, entityId, tick);
            } else {
                // Leave or delete.
                context.remove(entityId);
                if (b1) entityClassId[entityId] = -1;
            }
        }
    }

    private int classBits() {
        if (serializers == null || serializers.length == 0) return 8;
        int n = serializers.length;
        int bits = 0;
        while ((1 << bits) < n) bits++;
        return bits;
    }

    private void decodeEntityUpdate(BitReader br, int entityId, int tick) {
        int rawClassId = entityClassId[entityId];
        int cls = (rawClassId >= 0 && rawClassId < classIdToSerializer.length)
            ? classIdToSerializer[rawClassId] : -1;
        if (cls < 0 || serializers == null || cls >= serializers.length) {
            drainUnknownEntity(br);
            return;
        }
        SerializerSchema schema = serializers[cls];
        boolean isPlayer = schema.name.contains("PlayerPawn");
        boolean isController = schema.name.contains("PlayerController");

        PlayerState state = (isPlayer || isController)
            ? context.getOrCreate(entityId) : null;
        if (state != null) state.lastUpdateTick = tick;

        // Reset path for this entity.
        java.util.Arrays.fill(path, 0);
        path[0] = -1;
        depth = 0;

        while (true) {
            int op = HUFFMAN.decode(br);
            if (op == FieldPathOp.FIELD_PATH_ENCODE_FINISH) break;
            advancePath(op, br);
            readAndApplyField(schema, br, state);
        }
    }

    private void drainUnknownEntity(BitReader br) {
        // We can't safely skip fields without knowing the schema, so just stop.
    }

    // Advance the mutable path[] and depth using the decoded Huffman opcode.
    private void advancePath(int op, BitReader br) {
        switch (op) {
            case FieldPathOp.PLUS_ONE -> path[depth]++;
            case FieldPathOp.PLUS_TWO -> path[depth] += 2;
            case FieldPathOp.PLUS_THREE -> path[depth] += 3;
            case FieldPathOp.PLUS_FOUR -> path[depth] += 4;
            case FieldPathOp.PLUS_N -> path[depth] += br.readUBitVarFP() + 5;
            case FieldPathOp.PUSH_ONE_LEFT_DELTA_ZERO_RIGHT_ZERO -> {
                depth++;
                path[depth] = 0;
            }
            case FieldPathOp.PUSH_ONE_LEFT_DELTA_ZERO_RIGHT_NON_ZERO -> {
                depth++;
                path[depth] = br.readUBitVarFP();
            }
            case FieldPathOp.PUSH_ONE_LEFT_DELTA_ONE_RIGHT_ZERO -> {
                path[depth]++;
                depth++;
                path[depth] = 0;
            }
            case FieldPathOp.PUSH_ONE_LEFT_DELTA_ONE_RIGHT_NON_ZERO -> {
                path[depth]++;
                depth++;
                path[depth] = br.readUBitVarFP();
            }
            case FieldPathOp.PUSH_ONE_LEFT_DELTA_N_RIGHT_ZERO -> {
                path[depth] += br.readUBitVarFP();
                depth++;
                path[depth] = 0;
            }
            case FieldPathOp.PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO -> {
                path[depth] += br.readUBitVarFP();
                depth++;
                path[depth] = br.readUBitVarFP();
            }
            case FieldPathOp.PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO_PACK_6 -> {
                int packed = br.readBits(6);
                path[depth] += (packed >> 3) & 7;
                depth++;
                path[depth] = packed & 7;
            }
            case FieldPathOp.PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO_PACK_8 -> {
                int packed = br.readBits(8);
                path[depth] += (packed >> 4) & 0xF;
                depth++;
                path[depth] = packed & 0xF;
            }
            case FieldPathOp.POP_ONE_PLUS_ONE -> {
                if (depth > 0) depth--;
                path[depth]++;
            }
            case FieldPathOp.POP_ONE_PLUS_N -> {
                if (depth > 0) depth--;
                path[depth] += br.readUBitVarFP() + 1;
            }
            case FieldPathOp.POP_ALL_BUT_ONE_PLUS_ONE -> {
                depth = 0;
                path[depth]++;
            }
            case FieldPathOp.POP_ALL_BUT_ONE_PLUS_N -> {
                depth = 0;
                path[depth] += br.readUBitVarFP() + 1;
            }
            case FieldPathOp.POP_ALL_BUT_ONE_PLUS_N_PACK_3 -> {
                depth = 0;
                path[depth] += br.readBits(3) + 1;
            }
            case FieldPathOp.POP_ALL_BUT_ONE_PLUS_N_PACK_6 -> {
                depth = 0;
                path[depth] += br.readBits(6) + 1;
            }
            case FieldPathOp.PUSH_TWO_LEFT_DELTA_ZERO -> {
                depth++;
                path[depth] = 0;
                depth++;
                path[depth] = 0;
            }
            case FieldPathOp.PUSH_TWO_PACK_5_LEFT_DELTA_ZERO -> {
                int p = br.readBits(10);
                depth++;
                path[depth] = p >> 5;
                depth++;
                path[depth] = p & 0x1F;
            }
            case FieldPathOp.PUSH_THREE_LEFT_DELTA_ZERO -> {
                depth++;
                path[depth] = 0;
                depth++;
                path[depth] = 0;
                depth++;
                path[depth] = 0;
            }
            case FieldPathOp.PUSH_THREE_PACK_5_LEFT_DELTA_ZERO -> {
                int bits = br.readBits(15);
                depth++;
                path[depth] = bits >> 10;
                depth++;
                path[depth] = (bits >> 5) & 0x1F;
                depth++;
                path[depth] = bits & 0x1F;
            }
            case FieldPathOp.PUSH_TWO_LEFT_DELTA_N -> {
                path[depth] += br.readUBitVarFP() + 2;
                depth++;
                path[depth] = br.readUBitVarFP();
                depth++;
                path[depth] = br.readUBitVarFP();
            }
            case FieldPathOp.PUSH_TWO_PACK_5_LEFT_DELTA_N -> {
                path[depth] += br.readUBitVarFP() + 2;
                int bits = br.readBits(10);
                depth++;
                path[depth] = bits >> 5;
                depth++;
                path[depth] = bits & 0x1F;
            }
            case FieldPathOp.PUSH_THREE_LEFT_DELTA_N -> {
                path[depth] += br.readUBitVarFP() + 2;
                depth++;
                path[depth] = br.readUBitVarFP();
                depth++;
                path[depth] = br.readUBitVarFP();
                depth++;
                path[depth] = br.readUBitVarFP();
            }
            case FieldPathOp.PUSH_THREE_PACK_5_LEFT_DELTA_N -> {
                path[depth] += br.readUBitVarFP() + 2;
                int bits = br.readBits(15);
                depth++;
                path[depth] = bits >> 10;
                depth++;
                path[depth] = (bits >> 5) & 0x1F;
                depth++;
                path[depth] = bits & 0x1F;
            }
            case FieldPathOp.PUSH_N -> {
                int n = br.readUBitVarFP();
                path[depth] += br.readUBitVarFP();
                for (int j = 0; j < n && depth < path.length - 1; j++) {
                    depth++;
                    path[depth] = br.readUBitVarFP();
                }
            }
            case FieldPathOp.PUSH_N_AND_NON_TOPOGRAPHICAL -> {
                int n = br.readUBitVarFP();
                path[depth] += br.readUBitVarFP();
                for (int j = 0; j < n && depth < path.length - 1; j++) {
                    depth++;
                    path[depth] = br.readUBitVarFP();
                }
            }
            case FieldPathOp.POP_N_PLUS_ONE -> {
                int n = br.readUBitVarFP() + 1;
                if (depth >= n) depth -= n;
                else depth = 0;
                path[depth]++;
            }
            case FieldPathOp.POP_N_PLUS_N -> {
                int n = br.readUBitVarFP() + 1;
                if (depth >= n) depth -= n;
                else depth = 0;
                path[depth] += br.readUBitVarFP() + 1;
            }
            case FieldPathOp.POP_N_AND_NON_TOPOGRAPHICAL -> {
                int n = br.readUBitVarFP();
                if (depth >= n) depth -= n;
                else depth = 0;
                path[depth] += br.readUBitVarFP();
            }
            case FieldPathOp.NON_TOPOGRAPHICAL_COMPLEX -> {
                for (int j = 0; j <= depth; j++) {
                    if (br.readBool()) path[j] += br.readUBitVarFP() + 1;
                }
            }
            case FieldPathOp.NON_TOPOGRAPHICAL_COMPLEX_PACK_4 -> {
                // NonTopoPenultimatePluseOne: increment second-to-last path element.
                if (depth > 0) path[depth - 1]++;
            }
            case FieldPathOp.NON_TOPOGRAPHICAL_COMPLEX_PACK_8 -> {
                // NonTopoComplexPack4Bits: per-level delta in 4 bits, biased by -7.
                for (int j = 0; j <= depth; j++) {
                    if (br.readBool()) path[j] += br.readBits(4) - 7;
                }
            }
            case FieldPathOp.NON_TOPOGRAPHICAL_COMPLEX_FULL -> {
                // NonTopoComplexPack8Bits: per-level delta in 8 bits, biased by -127.
                for (int j = 0; j <= depth; j++) {
                    if (br.readBool()) path[j] += br.readBits(8) - 127;
                }
            }
            default -> {}
        }
    }

    // Navigate the schema along path[0..depth] and read + apply the field value.
    private void readAndApplyField(SerializerSchema rootSchema, BitReader br, PlayerState state) {
        SerializerSchema schema = rootSchema;
        FieldSchema field = null;

        for (int d = 0; d <= depth; d++) {
            int idx = path[d];
            if (schema == null || idx < 0 || idx >= schema.fields.length) {
                field = null;
                break;
            }
            field = schema.fields[idx];
            if (d < depth) {
                schema = field.subSerializer;
                if (schema == null) { field = null; break; }
            }
        }

        if (field == null) {
            // Unknown path - we can't safely skip; stop field decode for this entity.
            return;
        }

        readFieldValue(field, br, state);
    }

    private void readFieldValue(FieldSchema field, BitReader br, PlayerState state) {
        String type = field.typeName;
        String name = field.name;

        switch (type) {
            case "Vector" -> {
                float x = br.readFloat32();
                float y = br.readFloat32();
                float z = br.readFloat32();
                if (state != null && isPositionField(name)) {
                    state.x = x; state.y = y; state.z = z;
                }
            }
            case "Vector2D" -> {
                br.readFloat32();
                br.readFloat32();
            }
            case "QAngle" -> {
                float pitch, yaw, roll;
                if (field.bitCount > 0) {
                    pitch = br.readAngle(field.bitCount);
                    yaw   = br.readAngle(field.bitCount);
                    roll  = br.readAngle(field.bitCount);
                } else {
                    boolean hp = br.readBool(), hy = br.readBool(), hr = br.readBool();
                    pitch = hp ? br.readFloat32() : 0f;
                    yaw   = hy ? br.readFloat32() : 0f;
                    roll  = hr ? br.readFloat32() : 0f;
                }
                if (state != null && isAngleField(name)) {
                    state.pitch = pitch; state.yaw = yaw;
                }
            }
            case "float32", "CNetworkedQuantizedFloat" -> {
                float val;
                int flags = field.encodeFlags;
                if (field.bitCount > 0 && field.bitCount < 32) {
                    val = br.readQuantizedFloat(field.bitCount, field.lowValue, field.highValue);
                } else {
                    val = br.readFloat32();
                }
                if (state != null) {
                    if (isFlashField(name)) state.flashDuration = val;
                }
            }
            case "bool" -> {
                boolean val = br.readBool();
                if (state != null && name.equals("m_bPawnIsAlive")) {
                    state.isAlive = val;
                }
            }
            case "CUtlString", "CUtlSymbolLarge" -> {
                // Skip strings: read length as varint then that many bytes via bits.
                int slen = br.readVarUInt32();
                br.readBits(Math.min(slen * 8, br.bitsRemaining()));
            }
            default -> {
                // Integer / handle types.
                int bits = field.bitCount;
                if (bits > 0 && bits <= 32) {
                    int val = br.readBits(bits);
                    if (state != null && isHealthField(name)) {
                        state.isAlive = val > 0;
                    }
                } else if (bits > 32) {
                    // 64-bit: read as two 32s.
                    long lo = br.readBits(32) & 0xFFFFFFFFL;
                    long hi = br.readBits(bits - 32) & 0xFFFFFFFFL;
                    long val = lo | (hi << 32);
                    if (state != null && name.equals("m_steamID")) {
                        state.steamId = val;
                    }
                } else {
                    int val = br.readVarUInt32();
                    if (state != null) {
                        if (isHealthField(name)) state.isAlive = val > 0;
                        if (name.equals("m_steamID")) state.steamId = val;
                    }
                }
            }
        }
    }

    private static boolean isPositionField(String name) {
        return name.equals("m_vecAbsOrigin") || name.equals("m_vecOrigin")
            || name.equals("m_Origin");
    }

    private static boolean isAngleField(String name) {
        return name.equals("m_angEyeAngles") || name.equals("m_angRotation")
            || name.equals("m_vecViewAngles");
    }

    private static boolean isFlashField(String name) {
        return name.equals("m_flFlashDuration") || name.equals("m_flFlashMaxAlpha");
    }

    private static boolean isHealthField(String name) {
        return name.equals("m_iHealth") || name.equals("m_lifeState");
    }
}
