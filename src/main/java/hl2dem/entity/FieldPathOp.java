package hl2dem.entity;

// The 40 field path operations used in the CS2 entity bit-stream decoder.
// Weights are the Huffman node frequencies from the Source 2 engine.
// The Huffman tree is built at startup by FieldPathDecoder.
final class FieldPathOp {

    final String name;
    final int weight;

    // Encoded as constants matching the original Source 2 implementation.
    static final int PLUS_ONE                               = 0;
    static final int PLUS_TWO                              = 1;
    static final int PLUS_THREE                            = 2;
    static final int PLUS_FOUR                             = 3;
    static final int PLUS_N                                = 4;
    static final int PUSH_ONE_LEFT_DELTA_ZERO_RIGHT_ZERO   = 5;
    static final int PUSH_ONE_LEFT_DELTA_ZERO_RIGHT_NON_ZERO = 6;
    static final int PUSH_ONE_LEFT_DELTA_ONE_RIGHT_ZERO    = 7;
    static final int PUSH_ONE_LEFT_DELTA_ONE_RIGHT_NON_ZERO = 8;
    static final int PUSH_ONE_LEFT_DELTA_N_RIGHT_ZERO      = 9;
    static final int PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO  = 10;
    static final int PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO_PACK_6 = 11;
    static final int PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO_PACK_8 = 12;
    static final int PUSH_TWO_LEFT_DELTA_ZERO              = 13;
    static final int PUSH_TWO_PACK_5_LEFT_DELTA_ZERO       = 14;
    static final int PUSH_THREE_LEFT_DELTA_ZERO            = 15;
    static final int PUSH_THREE_PACK_5_LEFT_DELTA_ZERO     = 16;
    static final int PUSH_TWO_LEFT_DELTA_N                 = 17;
    static final int PUSH_TWO_PACK_5_LEFT_DELTA_N          = 18;
    static final int PUSH_THREE_LEFT_DELTA_N               = 19;
    static final int PUSH_THREE_PACK_5_LEFT_DELTA_N        = 20;
    static final int PUSH_N                                = 21;
    static final int PUSH_N_AND_NON_TOPOGRAPHICAL          = 22;
    static final int POP_ONE_PLUS_ONE                      = 23;
    static final int POP_ONE_PLUS_N                        = 24;
    static final int POP_ALL_BUT_ONE_PLUS_ONE              = 25;
    static final int POP_ALL_BUT_ONE_PLUS_N                = 26;
    static final int POP_ALL_BUT_ONE_PLUS_N_PACK_3         = 27;
    static final int POP_ALL_BUT_ONE_PLUS_N_PACK_6         = 28;
    static final int POP_N_PLUS_ONE                        = 29;
    static final int POP_N_PLUS_N                          = 30;
    static final int POP_N_AND_NON_TOPOGRAPHICAL           = 31;
    static final int NON_TOPOGRAPHICAL_COMPLEX             = 32;
    static final int NON_TOPOGRAPHICAL_COMPLEX_PACK_4      = 33;
    static final int NON_TOPOGRAPHICAL_COMPLEX_PACK_8      = 34;
    static final int NON_TOPOGRAPHICAL_COMPLEX_FULL        = 35;
    static final int FIELD_PATH_ENCODE_FINISH              = 39;

    final int id;

    FieldPathOp(String name, int weight, int id) {
        this.name = name;
        this.weight = weight;
        this.id = id;
    }

    // The 40 operations and their Huffman weights (from Source 2 Source).
    static final FieldPathOp[] ALL = {
        new FieldPathOp("PlusOne", 36, PLUS_ONE),
        new FieldPathOp("PlusTwo", 10, PLUS_TWO),
        new FieldPathOp("PlusThree", 2, PLUS_THREE),
        new FieldPathOp("PlusFour", 1, PLUS_FOUR),
        new FieldPathOp("PlusN", 4, PLUS_N),
        new FieldPathOp("PushOneLeftDeltaZeroRightZero", 1, PUSH_ONE_LEFT_DELTA_ZERO_RIGHT_ZERO),
        new FieldPathOp("PushOneLeftDeltaZeroRightNonZero", 1, PUSH_ONE_LEFT_DELTA_ZERO_RIGHT_NON_ZERO),
        new FieldPathOp("PushOneLeftDeltaOneRightZero", 12, PUSH_ONE_LEFT_DELTA_ONE_RIGHT_ZERO),
        new FieldPathOp("PushOneLeftDeltaOneRightNonZero", 50, PUSH_ONE_LEFT_DELTA_ONE_RIGHT_NON_ZERO),
        new FieldPathOp("PushOneLeftDeltaNRightZero", 2, PUSH_ONE_LEFT_DELTA_N_RIGHT_ZERO),
        new FieldPathOp("PushOneLeftDeltaNRightNonZero", 3, PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO),
        new FieldPathOp("PushOneLeftDeltaNRightNonZeroPack6Bits", 10, PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO_PACK_6),
        new FieldPathOp("PushOneLeftDeltaNRightNonZeroPack8Bits", 1, PUSH_ONE_LEFT_DELTA_N_RIGHT_NON_ZERO_PACK_8),
        new FieldPathOp("PushTwoLeftDeltaZero", 1, PUSH_TWO_LEFT_DELTA_ZERO),
        new FieldPathOp("PushTwoPack5LeftDeltaZero", 1, PUSH_TWO_PACK_5_LEFT_DELTA_ZERO),
        new FieldPathOp("PushThreeLeftDeltaZero", 1, PUSH_THREE_LEFT_DELTA_ZERO),
        new FieldPathOp("PushThreePack5LeftDeltaZero", 1, PUSH_THREE_PACK_5_LEFT_DELTA_ZERO),
        new FieldPathOp("PushTwoLeftDeltaN", 1, PUSH_TWO_LEFT_DELTA_N),
        new FieldPathOp("PushTwoPack5LeftDeltaN", 1, PUSH_TWO_PACK_5_LEFT_DELTA_N),
        new FieldPathOp("PushThreeLeftDeltaN", 1, PUSH_THREE_LEFT_DELTA_N),
        new FieldPathOp("PushThreePack5LeftDeltaN", 1, PUSH_THREE_PACK_5_LEFT_DELTA_N),
        new FieldPathOp("PushN", 1, PUSH_N),
        new FieldPathOp("PushNAndNonTopographical", 1, PUSH_N_AND_NON_TOPOGRAPHICAL),
        new FieldPathOp("PopOnePlusOne", 1, POP_ONE_PLUS_ONE),
        new FieldPathOp("PopOnePlusN", 1, POP_ONE_PLUS_N),
        new FieldPathOp("PopAllButOnePlusOne", 1, POP_ALL_BUT_ONE_PLUS_ONE),
        new FieldPathOp("PopAllButOnePlusN", 1, POP_ALL_BUT_ONE_PLUS_N),
        new FieldPathOp("PopAllButOnePlusNPack3Bits", 1, POP_ALL_BUT_ONE_PLUS_N_PACK_3),
        new FieldPathOp("PopAllButOnePlusNPack6Bits", 1, POP_ALL_BUT_ONE_PLUS_N_PACK_6),
        new FieldPathOp("PopNPlusOne", 1, POP_N_PLUS_ONE),
        new FieldPathOp("PopNPlusN", 1, POP_N_PLUS_N),
        new FieldPathOp("PopNAndNonTopographical", 1, POP_N_AND_NON_TOPOGRAPHICAL),
        new FieldPathOp("NonTopoComplex", 1, NON_TOPOGRAPHICAL_COMPLEX),
        new FieldPathOp("NonTopoPenultimatePluseOne", 1, NON_TOPOGRAPHICAL_COMPLEX_PACK_4),
        new FieldPathOp("NonTopoComplexPack4Bits", 1, NON_TOPOGRAPHICAL_COMPLEX_PACK_8),
        new FieldPathOp("NonTopoComplexPack8Bits", 1, NON_TOPOGRAPHICAL_COMPLEX_FULL),
        new FieldPathOp("FieldPathEncodeFinish", 25, FIELD_PATH_ENCODE_FINISH),
    };
}
