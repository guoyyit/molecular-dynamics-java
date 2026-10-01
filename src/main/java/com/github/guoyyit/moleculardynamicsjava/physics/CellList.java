package com.github.guoyyit.moleculardynamicsjava.physics;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;

import java.util.function.BiConsumer;

/**
 * Linked-cell neighbor list for orthorhombic periodic boxes.
 *
 * <p>Space is divided into cells whose edge length is at least the pair
 * cutoff. Each particle pair whose distance can be below the cutoff is
 * visited exactly once. Usable only when every box dimension holds at
 * least 4 cells; with fewer cells the half-neighbor stencil would meet its
 * own periodic image, so callers fall back to the direct O(N^2) loop.
 */
public class CellList {
    private final double cutoff;

    private int ncx;
    private int ncy;
    private int ncz;
    private int cellCount;
    private int[] head;
    private int[] next;
    private boolean usable;

    public CellList(SimulationConfig config) {
        this.cutoff = config.cutoff;
    }

    public boolean isUsable() {
        return usable;
    }

    public void rebuild(double[][] positions, PeriodicBox box) {
        ncx = (int) StrictMath.floor(box.length(0) / cutoff);
        ncy = (int) StrictMath.floor(box.length(1) / cutoff);
        ncz = (int) StrictMath.floor(box.length(2) / cutoff);
        usable = ncx >= 4 && ncy >= 4 && ncz >= 4;
        if (!usable) {
            return;
        }
        cellCount = ncx * ncy * ncz;
        if (head == null || head.length < cellCount) {
            head = new int[cellCount];
        }
        if (next == null || next.length < positions.length) {
            next = new int[positions.length];
        }
        java.util.Arrays.fill(head, 0, cellCount, -1);
        for (int i = 0; i < positions.length; i++) {
            int cx = cellIndex(positions[i][0], box.length(0), ncx);
            int cy = cellIndex(positions[i][1], box.length(1), ncy);
            int cz = cellIndex(positions[i][2], box.length(2), ncz);
            int cell = cx + ncx * (cy + ncy * cz);
            next[i] = head[cell];
            head[cell] = i;
        }
    }

    private static int cellIndex(double coordinate, double length, int count) {
        int index = (int) StrictMath.floor(coordinate / length * count);
        if (index < 0) {
            return 0;
        }
        if (index >= count) {
            return count - 1;
        }
        return index;
    }

    private int headOf(int cx, int cy, int cz) {
        int wx = (cx %= ncx) < 0 ? cx + ncx : cx;
        int wy = (cy %= ncy) < 0 ? cy + ncy : cy;
        int wz = (cz %= ncz) < 0 ? cz + ncz : cz;
        return head[wx + ncx * (wy + ncy * wz)];
    }

    /** Visit every candidate particle pair (i, j) with i &lt; j exactly once. */
    public void forEachPair(BiConsumer<Integer, Integer> consumer) {
        int[][] offsets = {
                {1, 0, 0}, {0, 1, 0}, {1, 1, 0}, {-1, 1, 0},
                {-1, -1, 1}, {0, -1, 1}, {1, -1, 1},
                {-1, 0, 1}, {0, 0, 1}, {1, 0, 1},
                {-1, 1, 1}, {0, 1, 1}, {1, 1, 1}
        };
        for (int cz = 0; cz < ncz; cz++) {
            for (int cy = 0; cy < ncy; cy++) {
                for (int cx = 0; cx < ncx; cx++) {
                    for (int i = headOf(cx, cy, cz); i != -1; i = next[i]) {
                        for (int j = next[i]; j != -1; j = next[j]) {
                            consumer.accept(i, j);
                        }
                        for (int[] offset : offsets) {
                            int neighbor = headOf(cx + offset[0], cy + offset[1], cz + offset[2]);
                            for (int j = neighbor; j != -1; j = next[j]) {
                                consumer.accept(i, j);
                            }
                        }
                    }
                }
            }
        }
    }
}
