package hl2dem.frame;

public record FrameHeader(int rawCmd, int tick, long payloadOffset, int payloadSize) {}
