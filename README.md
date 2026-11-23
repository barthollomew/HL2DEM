# HL2DEM

Counter-Strike 2 match replays (`.dem` files) pack hundreds of megabytes of tick-by-tick player positions, view angles, and flashbang states into a compressed binary stream.

## What it does

HL2DEM parses CS2 demo files at high speed using modern Java. It memory-maps the `.dem` file with Java 21 `MemorySegment` so it never loads the whole file into heap memory. It unpacks Snappy frames and Source 2 Huffman field paths with zero copy overhead. It runs live crosshair accuracy and flashbang blindness analytics (`CrosshairEngine` and `BlindnessTracker`) on a background virtual thread while rendering a live terminal dashboard (`Dashboard`). It streams every tick to NDJSON or CSV.

## How it works

- **DemoFile**: memory-maps the replay via Java 21 foreign memory API. Zero copy reads throughout the parse pipeline.
- **FrameSplicer**: walks varint frame headers to split the byte stream into individual frames.
- **FrameUnpacker**: decompresses Snappy payloads or passes raw bytes for uncompressed frames.
- **EntityDecoder**: processes protobuf tables and entity updates using Valve CS2 definitions. Tracks up to 64 players.
- **HuffmanTree**: decodes Huffman field paths used in entity delta compression. Weights match the Source 2 engine.
- **Virtual Threads**: parser and consumer run on separate virtual threads with a bounded queue. Analytics and UI rendering run off the parse hot path.

## Build

```bash
gradlew clean build
```

The first build downloads CS2 proto files from SteamDatabase over the network and generates Java code. Later builds use cached protos.

## Usage

Run directly:

```bash
gradlew run --args="match.dem"
```

Or build a fat jar:

```bash
gradlew shadowJar
java --enable-preview -jar build/libs/hl2dem-1.0.jar match.dem
```

Export to file:

```bash
java --enable-preview -jar build/libs/hl2dem-1.0.jar match.dem --out out.ndjson --format json
java --enable-preview -jar build/libs/hl2dem-1.0.jar match.dem --out out.csv --format csv
```

## Flags

| Flag | Default | Description |
|------|---------|-------------|
| --out | stdout | Output file path |
| --format | json | Output format json or csv |

## Output

One JSON object per player per tick:

```json
{"tick":512,"entityId":3,"steamId":76561198012345678,"name":"playerOne","x":412.5,"y":-203.1,"z":64.0,"yaw":87.3,"pitch":-12.0,"flash":0.0,"alive":true}
```

CSV output uses the same fields in order with a header row.

## Requirements

- Java 21 or newer with preview flags enabled. MemorySegment and Arena are preview in Java 21 and final in Java 22.
- Network access on first build for proto files.

## Benchmarks

```bash
gradlew jmh
```

Benchmarks test BitReader and HuffmanTree hot paths. See PERFORMANCE.md for full results.

### Benchmark Summary

| Benchmark | Mean (ns) | p99 (ns) |
|---|---|---|
| HuffmanTree.decode | 5.37 | 31 |
| BitReader.readBits(32) | 6.04 | 31 |
| BitReader.readUBitVarFP | 0.64 | 31 |

## Parser Bugs Found

Testing found two real parser bugs:

1. Varint negative offset bug: Large varints decoded to negative numbers which corrupted buffer offsets and caused errors. Fixed by adding bounds guards. Found by DemFuzzerTest.
2. HuffmanTree comparator bug: Leaf and merged nodes had mismatched array lengths which threw index errors during tie breaks. Fixed by matching array sizes. Found by BitReaderBenchmark.

## Tests

```bash
gradlew test
```

There are 24 tests. DemFuzzerTest tests corrupt input like bad magic headers and truncated frames. VarintReaderTest tests varint edge cases.
