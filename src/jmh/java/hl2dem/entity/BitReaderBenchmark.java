package hl2dem.entity;

import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

// Package-private placement gives access to BitReader, HuffmanTree, and FieldPathOp.
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class BitReaderBenchmark {

    private byte[] buf;
    private HuffmanTree tree;

    @Setup
    public void setup() {
        buf = new byte[256];
        new Random(42).nextBytes(buf);
        tree = new HuffmanTree(FieldPathOp.ALL);
    }

    // Measures the entire UBitVarFP decode: 1-5 boolean reads + 2-31 bit reads.
    // BitReader construction is intentionally included — it is allocation-free at steady state
    // (escape analysis eliminates the object) and resets the read position each call.
    @Benchmark
    public int readUBitVarFP() {
        return new BitReader(buf, buf.length).readUBitVarFP();
    }

    @Benchmark
    public int huffmanDecode() {
        return tree.decode(new BitReader(buf, buf.length));
    }

    // Baseline: plain bit read with no branching overhead
    @Benchmark
    public int readBits_32() {
        return new BitReader(buf, buf.length).readBits(32);
    }
}
