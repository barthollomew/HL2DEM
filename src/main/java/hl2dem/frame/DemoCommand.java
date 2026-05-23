package hl2dem.frame;

import valve.pb.DemoProto;

public final class DemoCommand {

    // Bitmask: if set on a raw command byte, the payload is Snappy-compressed.
    public static final int IS_COMPRESSED = DemoProto.EDemoCommands.DEM_IsCompressed_VALUE; // 64

    // Command IDs from the generated EDemoCommands protobuf enum.
    public static final int STOP            = DemoProto.EDemoCommands.DEM_Stop_VALUE;
    public static final int FILE_HEADER     = DemoProto.EDemoCommands.DEM_FileHeader_VALUE;
    public static final int FILE_INFO       = DemoProto.EDemoCommands.DEM_FileInfo_VALUE;
    public static final int SYNC_TICK       = DemoProto.EDemoCommands.DEM_SyncTick_VALUE;
    public static final int SEND_TABLES     = DemoProto.EDemoCommands.DEM_SendTables_VALUE;
    public static final int CLASS_INFO      = DemoProto.EDemoCommands.DEM_ClassInfo_VALUE;
    public static final int STRING_TABLES   = DemoProto.EDemoCommands.DEM_StringTables_VALUE;
    public static final int PACKET          = DemoProto.EDemoCommands.DEM_Packet_VALUE;
    public static final int SIGNON_PACKET   = DemoProto.EDemoCommands.DEM_SignonPacket_VALUE;
    public static final int CONSOLE_CMD     = DemoProto.EDemoCommands.DEM_ConsoleCmd_VALUE;
    public static final int USER_CMD        = DemoProto.EDemoCommands.DEM_UserCmd_VALUE;
    public static final int FULL_PACKET     = DemoProto.EDemoCommands.DEM_FullPacket_VALUE;

    public static boolean isCompressed(int rawCmd) {
        return (rawCmd & IS_COMPRESSED) != 0;
    }

    public static int stripFlags(int rawCmd) {
        return rawCmd & ~IS_COMPRESSED;
    }

    private DemoCommand() {}
}
