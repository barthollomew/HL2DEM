package hl2dem.io;

// VarintReader.readVarint() benchmarks require Java 22+ where MemorySegment/Arena
// are stable (JEP 454). On Java 21 they are preview APIs and the JMH bytecode
// generator cannot load preview-compiled class files. Run manually with:
//   java --enable-preview -jar build/libs/hl2dem-jmh.jar VarintBenchmark
// after upgrading the toolchain to Java 22+.
final class VarintBenchmark {
    private VarintBenchmark() {}
}
