# HL2DEM Performance Baseline

## Environment

| Item | Value |
|---|---|
| JDK | OpenJDK 21.0.2 (Amazon Corretto) |
| JMH | 1.36 |
| OS | Linux 7.0.9 x86_64 |
| Warmup | 10 iterations × 1 s |
| Measurement | 10 iterations × 1 s |
| Forks | 1 |
| Modes | `AverageTime` + `SampleTime` |

## Results — BitReader / HuffmanTree

Hot paths in CS2 entity decode: every field update in every packet reads one Huffman opcode
plus one `UBitVarFP` value. These two operations dominate per-entity CPU time.

### Average Time (mean ± error)

| Benchmark | Mean (ns) | ± Error |
|---|---|---|
| `HuffmanTree.decode` | **5.37** | ±0.01 |
| `BitReader.readBits(32)` | **6.04** | ±0.01 |
| `BitReader.readUBitVarFP` | **0.64** | ±0.002 |

### Percentiles (SampleTime)

| Benchmark | p50 (ns) | p95 (ns) | p99 (ns) | p99.9 (ns) |
|---|---|---|---|---|
| `HuffmanTree.decode` | 30 | 31 | 31 | 80 |
| `BitReader.readBits(32)` | 30 | 31 | 31 | 90 |
| `BitReader.readUBitVarFP` | 20 | 30 | 31 | 80 |

The p99–p99.9 jump to 80–90 ns reflects OS scheduling jitter on unshielded cores; the hot-path
(p50) is consistently ≤30 ns for all three primitives.

## Bugs Found and Fixed During Hardening

The fuzzer and benchmarks exposed two latent bugs:

### 1. `VarintReader`: negative offset from large-payload varint (`IOOBE` in `FrameSplicer`)
- **Root cause:** A 5-byte varint encoding 0xFFFF_FFFF decodes to `-1` as a signed `int`.
  `FrameSplicer` added this to `position` (a `long`), making position negative. The next
  call to `VarintReader.readVarint(seg, -2_147_483_648L)` passed the bounds check
  (`-2G < 512`) and then called `seg.get(JAVA_BYTE, -2G)` which threw `IOOBE`.
- **Fix:** Added `if (offset < 0) throw ISE` guard in `VarintReader.readVarint`; added
  `if (size < 0) return null` guard in `FrameSplicer.next`.
- **Detected by:** `DemFuzzerTest.randomMutations_parserNeverHangs(seed=12345)`

### 2. `HuffmanTree`: comparator reads `a[2]` on a 2-element merged node (`AIOOBE`)
- **Root cause:** Leaf nodes in the heap are `long[]{weight, nodeIdx, insertionOrder}` (length 3).
  Merged internal nodes were inserted as `long[]{combinedWeight, nodeIdx}` (length 2). When two
  internal nodes had equal combined weights, the tie-breaking comparator read `a[2]` and threw
  `ArrayIndexOutOfBoundsException`.
- **Fix:** Added `insertionOrder++` to the merged-node array in `HuffmanTree`.
- **Detected by:** `BitReaderBenchmark.huffmanDecode` (failed in first JMH warmup iteration)

## VarintReader Benchmarks (Java 22+ required)

`VarintReader.readVarint()` takes a `MemorySegment` parameter. `java.lang.foreign.MemorySegment`
is a preview API in Java 21 (finalized in Java 22 via JEP 454). The JMH benchmark for
`VarintReader` is in `src/jmh/java/hl2dem/io/VarintBenchmark.java` as a stub. To run it,
upgrade the toolchain to Java 22+ and restore the `@Benchmark` methods.

Expected order-of-magnitude: 2–8 ns per varint (single-byte hot path vs. 5-byte slow path),
consistent with the `readUBitVarFP` baseline above.
