package hl2dem.entity;

import java.util.PriorityQueue;

// Builds and navigates the Huffman tree used for CS2 field path decoding.
final class HuffmanTree {

    // A node in the tree. Leaf nodes hold a FieldPathOp id; internal nodes hold -1.
    private final int[] left;   // left child index (0 bit)
    private final int[] right;  // right child index (1 bit)
    private final int[] opId;   // FieldPathOp.id if leaf, else -1
    private final int root;
    private int size;

    // Node count: at most 2*N-1 for N leaves.
    HuffmanTree(FieldPathOp[] ops) {
        int capacity = ops.length * 2;
        left = new int[capacity];
        right = new int[capacity];
        opId = new int[capacity];
        java.util.Arrays.fill(opId, -1);

        // Min-heap sorted by weight then by insertion order for stable tie-breaking.
        // Stable tie-breaking is required to produce the same tree as the Source 2 engine.
        // Ops with weight 0 are excluded - they are never emitted by the encoder.
        long insertionOrder = 0;
        PriorityQueue<long[]> heap = new PriorityQueue<>((a, b) -> {
            int cmp = Long.compare(a[0], b[0]);
            return cmp != 0 ? cmp : Long.compare(a[2], b[2]);
        });

        for (FieldPathOp op : ops) {
            if (op.weight == 0) continue;
            int idx = alloc();
            opId[idx] = op.id;
            heap.add(new long[]{op.weight, idx, insertionOrder++});
        }

        while (heap.size() > 1) {
            long[] a = heap.poll();
            long[] b = heap.poll();
            int parent = alloc();
            left[parent] = (int) a[1];
            right[parent] = (int) b[1];
            heap.add(new long[]{a[0] + b[0], parent, insertionOrder++});
        }

        root = (int) heap.poll()[1];
    }

    private int alloc() {
        return size++;
    }

    // Decode one opcode from the BitReader by traversing the tree.
    int decode(BitReader br) {
        int node = root;
        while (opId[node] == -1) {
            if (br.readBool()) {
                node = right[node];
            } else {
                node = left[node];
            }
        }
        return opId[node];
    }
}
