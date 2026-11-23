# HL2DEM Performance Baseline

## Environment

| Item | Value |
|---|---|
| JDK | OpenJDK 21.0.2 Amazon Corretto |
| JMH | 1.36 |
| OS | Linux 7.0.9 x86_64 |
| Warmup | 10 runs by 1 s |
| Measurement | 10 runs by 1 s |
| Forks | 1 |
| Modes | AverageTime and SampleTime |

## Results for BitReader and HuffmanTree

Hot paths in CS2 entity decode read one Huffman opcode and one UBitVarFP value for every field update in every packet. These two tasks take most of the CPU time during parsing.

### Average Time (mean plus or minus error)

| Benchmark | Mean (ns) | Error |
|---|---|---|
| HuffmanTree.decode | 5.37 | 0.01 |
| BitReader.readBits(32) | 6.04 | 0.01 |
| BitReader.readUBitVarFP | 0.64 | 0.002 |

### Percentiles (SampleTime)

| Benchmark | p50 (ns) | p95 (ns) | p99 (ns) | p99.9 (ns) |
|---|---|---|---|---|
| HuffmanTree.decode | 30 | 31 | 31 | 80 |
| BitReader.readBits(32) | 30 | 31 | 31 | 90 |
| BitReader.readUBitVarFP | 20 | 30 | 31 | 80 |

The jump from p99 to p99.9 reaches 80 to 90 ns due to OS core scheduling jitter. The fast path p50 stays at or below 30 ns for all three methods.

## Bugs Found and Fixed During Hardening

The fuzzer and benchmarks found two hidden bugs:

### 1. VarintReader negative offset error

- Root cause: A 5-byte varint for 0xFFFF_FFFF decoded to negative one as a signed int. FrameSplicer added this to position as a long. That made position negative. The next read passed the bounds check because it was small and threw an index out of bounds error.
- Fix: Added a check for negative offset in VarintReader and a check for negative size in FrameSplicer.
- Found by: DemFuzzerTest random mutations.

### 2. HuffmanTree comparator array size error

- Root cause: Leaf nodes used an array of length three while merged nodes used an array of length two. When two merged nodes had equal weights the tie breaker comparator read index two and threw an index out of bounds error.
- Fix: Added insertion order tracking to merged nodes in HuffmanTree.
- Found by: BitReaderBenchmark huffman decode during the first warmup run.

## VarintReader Benchmarks (Java 22 required)

VarintReader takes a MemorySegment. MemorySegment is a preview feature in Java 21 and final in Java 22. The JMH benchmark stub sits in src/jmh/java/hl2dem/io/VarintBenchmark.java. Upgrade to Java 22 and restore benchmark annotations to run it.
