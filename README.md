# HL2DEM

CS2 demo file parser for Java 21+. Reads `.dem` files produced by CS2's Source 2
engine, decodes the entity stream tick-by-tick, and exports per-player state as
NDJSON or CSV. Streams output while parsing — no full-file load into memory.

## How it works

- **DemoFile**: memory-maps the file via `MemorySegment`/`Arena`. Zero-copy reads
  throughout the pipeline.
- **FrameSplicer**: walks varint-encoded frame headers to split the byte stream into
  individual frames.
- **FrameUnpacker**: decompresses Snappy-encoded payloads. Passes raw bytes for
  uncompressed frames.
- **EntityDecoder**: processes protobuf send tables and entity updates using Valve's
  CS2 proto definitions. Tracks up to 64 players.
- **HuffmanTree**: decodes Huffman-coded field paths used in CS2's entity delta
  encoding. Weights and tie-breaking match the Source 2 engine exactly.
- **Virtual threads**: parser and consumer run on separate virtual threads with a
  bounded `ArrayBlockingQueue`. Analytics and export happen off the parse hot path.

## Build

```bash
./gradlew clean build
```

First build fetches Valve CS2 proto definitions from SteamDatabase over the network
and generates Java sources. Subsequent builds use the cached protos.

## Usage

Run directly:

```bash
./gradlew run --args="match.dem"
```

Or build a fat JAR:

```bash
./gradlew shadowJar
java --enable-preview -jar build/libs/hl2dem-1.0.jar match.dem
```

Export to file:

```bash
java --enable-preview -jar build/libs/hl2dem-1.0.jar match.dem --out out.ndjson --format json
java --enable-preview -jar build/libs/hl2dem-1.0.jar match.dem --out out.csv   --format csv
```

## Flags

| Flag | Default | Description |
|------|---------|-------------|
| `--out` | stdout | Output file path |
| `--format` | `json` | Output format: `json` or `csv` |

## Output

One JSON object per player per tick (NDJSON):

```json
{"tick":512,"entityId":3,"steamId":76561198012345678,"name":"playerOne","x":412.5,"y":-203.1,"z":64.0,"yaw":87.3,"pitch":-12.0,"flash":0.0,"alive":true}
```

CSV output has the same fields in the same column order with a header row.

## Requirements

- Java 21+ with `--enable-preview` (`MemorySegment`/`Arena` are preview APIs in
  Java 21; finalized in Java 22 via JEP 454)
- Network access on first build to fetch CS2 proto files

## Tests

```bash
./gradlew test
```

24 tests. `DemFuzzerTest` covers corrupt input: invalid magic, truncated frames,
oversized payloads, random and bit-flip mutations. `VarintReaderTest` covers
multi-byte varints and the negative-offset regression.

## Benchmarks

```bash
./gradlew jmh
```

Benchmarks the `BitReader` and `HuffmanTree` hot paths (`BitReaderBenchmark`).
See [PERFORMANCE.md](PERFORMANCE.md) for baseline numbers.
