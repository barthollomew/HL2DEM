# HL2DEM

CS2 demo file parser for Java 21+. Reads dot dem files from the CS2 Source 2 engine. It decodes player state tick by tick and exports data as NDJSON or CSV. It streams data while parsing without loading the whole file into memory.

## How it works

- DemoFile: maps the file into memory using MemorySegment and Arena. Reads zero copy data.
- FrameSplicer: walks varint headers to split the byte stream into frames.
- FrameUnpacker: unpacks Snappy compressed payloads or passes raw uncompressed bytes.
- EntityDecoder: parses proto tables and entity updates using CS2 proto files. Tracks up to 64 players.
- HuffmanTree: decodes Huffman paths used in entity delta compression. Weights match the Source 2 engine.
- Virtual threads: parser and writer run on separate virtual threads with a shared queue. Export work happens off the main parse path.

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
