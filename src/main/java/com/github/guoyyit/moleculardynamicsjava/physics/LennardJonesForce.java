package com.github.guoyyit.moleculardynamicsjava.physics;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.SimulationState;

public class LennardJonesForce {

    public double computeForcesAndEnergy(SimulationState state, SimulationConfig config) {
        int n = state.n;
        double[] box = config.getBox();
        double[] pos = state.pos;
        double[] force = state.getForce();
        for (int i = 0; i < force.length; i++) {
            force[i] = 0.0;
        }
        double cutoff = config.getCutoff();
        double minEdge = Math.min(box[0], Math.min(box[1], box[2]));
        if (minEdge >= 3.0 * cutoff) {
            return computeWithCellList(n, pos, force, box, config);
        }
        return computeDirect(n, pos, force, box, config);
    }

    private double pairShift(SimulationConfig config) {
        double sigma = config.getSigma();
        double cutoff = config.getCutoff();
        double ratio6 = Math.pow(sigma / cutoff, 6);
        double ratio12 = ratio6 * ratio6;
        return 4.0 * config.getEpsilon() * (ratio12 - ratio6);
    }

    private double interact(int i, int j, double[] pos, double[] force, double[] box,
                            SimulationConfig config, double energyShift) {
        double dx = pos[3 * i] - pos[3 * j];
        double dy = pos[3 * i + 1] - pos[3 * j + 1];
        double dz = pos[3 * i + 2] - pos[3 * j + 2];
        dx -= box[0] * Math.rint(dx / box[0]);
        dy -= box[1] * Math.rint(dy / box[1]);
        dz -= box[2] * Math.rint(dz / box[2]);
        double rSquared = dx * dx + dy * dy + dz * dz;
        double cutoffSquared = config.getCutoff() * config.getCutoff();
        if (rSquared >= cutoffSquared || rSquared == 0.0) {
            return 0.0;
        }
        double sigma = config.getSigma();
        double inverseR2 = (sigma * sigma) / rSquared;
        double inverseR6 = inverseR2 * inverseR2 * inverseR2;
        double inverseR12 = inverseR6 * inverseR6;
        double epsilon = config.getEpsilon();
        double factor = 24.0 * epsilon * (2.0 * inverseR12 - inverseR6) / rSquared;
        force[3 * i] += factor * dx;
        force[3 * i + 1] += factor * dy;
        force[3 * i + 2] += factor * dz;
        force[3 * j] -= factor * dx;
        force[3 * j + 1] -= factor * dy;
        force[3 * j + 2] -= factor * dz;
        return 4.0 * epsilon * (inverseR12 - inverseR6) - energyShift;
    }

    private double computeDirect(int n, double[] pos, double[] force, double[] box,
                                 SimulationConfig config) {
        double energyShift = pairShift(config);
        double energy = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                energy += interact(i, j, pos, force, box, config, energyShift);
            }
        }
        return energy;
    }

    private double computeWithCellList(int n, double[] pos, double[] force, double[] box,
                                       SimulationConfig config) {
        double cutoff = config.getCutoff();
        int nx = (int) Math.floor(box[0] / cutoff);
        int ny = (int) Math.floor(box[1] / cutoff);
        int nz = (int) Math.floor(box[2] / cutoff);
        int cellCount = nx * ny * nz;
        int[] cellOfParticle = new int[n];
        int[] occupancy = new int[cellCount];
        for (int i = 0; i < n; i++) {
            int cx = Math.min((int) (pos[3 * i] / cutoff), nx - 1);
            int cy = Math.min((int) (pos[3 * i + 1] / cutoff), ny - 1);
            int cz = Math.min((int) (pos[3 * i + 2] / cutoff), nz - 1);
            int cell = cz * nx * ny + cy * nx + cx;
            cellOfParticle[i] = cell;
            occupancy[cell]++;
        }
        int[] cellStart = new int[cellCount + 1];
        for (int c = 0; c < cellCount; c++) {
            cellStart[c + 1] = cellStart[c] + occupancy[c];
        }
        int[] members = new int[n];
        int[] cursor = new int[cellCount];
        System.arraycopy(cellStart, 0, cursor, 0, cellCount);
        for (int i = 0; i < n; i++) {
            int cell = cellOfParticle[i];
            members[cursor[cell]++] = i;
        }
        double energyShift = pairShift(config);
        double energy = 0.0;
        for (int cz = 0; cz < nz; cz++) {
            for (int cy = 0; cy < ny; cy++) {
                for (int cx = 0; cx < nx; cx++) {
                    int cellIndex = cz * nx * ny + cy * nx + cx;
                    for (int dz = 0; dz <= 1; dz++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int startDx = (dz == 0 && dy == 0) ? 0 : -1;
                            for (int ddx = startDx; ddx <= 1; ddx++) {
                                if (dz == 0 && dy == 0 && ddx == 0) {
                                    energy += withinCell(cellStart, members, cellIndex, pos,
                                            force, box, config, energyShift);
                                    continue;
                                }
                                int ox = Math.floorMod(cx + ddx, nx);
                                int oy = Math.floorMod(cy + dy, ny);
                                int oz = Math.floorMod(cz + dz, nz);
                                int otherIndex = oz * nx * ny + oy * nx + ox;
                                energy += betweenCells(cellStart, members, cellIndex, otherIndex,
                                        pos, force, box, config, energyShift);
                            }
                        }
                    }
                }
            }
        }
        return energy;
    }

    private double withinCell(int[] cellStart, int[] members, int cellIndex, double[] pos,
                              double[] force, double[] box, SimulationConfig config,
                              double energyShift) {
        double energy = 0.0;
        int start = cellStart[cellIndex];
        int end = cellStart[cellIndex + 1];
        for (int a = start; a < end; a++) {
            for (int b = a + 1; b < end; b++) {
                energy += interact(members[a], members[b], pos, force, box, config, energyShift);
            }
        }
        return energy;
    }

    private double betweenCells(int[] cellStart, int[] members, int firstCell, int secondCell,
                                double[] pos, double[] force, double[] box,
                                SimulationConfig config, double energyShift) {
        double energy = 0.0;
        int firstStart = cellStart[firstCell];
        int firstEnd = cellStart[firstCell + 1];
        int secondStart = cellStart[secondCell];
        int secondEnd = cellStart[secondCell + 1];
        for (int a = firstStart; a < firstEnd; a++) {
            for (int b = secondStart; b < secondEnd; b++) {
                int i = members[a];
                int j = members[b];
                energy += interact(i, j, pos, force, box, config, energyShift);
            }
        }
        return energy;
    }
}
