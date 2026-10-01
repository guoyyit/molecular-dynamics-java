package com.github.guoyyit.moleculardynamicsjava.physics;

import com.github.guoyyit.moleculardynamicsjava.model.SimulationConfig;
import com.github.guoyyit.moleculardynamicsjava.model.Species;

/**
 * Lennard-Jones pair interaction with a finite cutoff.
 *
 * <p>For pair type ab the parameters are combined with the Lorentz-Berthelot
 * rules: sigma_ab = (sigma_a + sigma_b) / 2 and epsilon_ab = sqrt(eps_a eps_b).
 * The force-shifted form is used: the unshifted force magnitude at the
 * cutoff rc is subtracted from the force, and the potential is shifted by
 * the matching constant. Both force and energy are continuous at rc, which
 * keeps the numerical energy drift in NVE runs small.
 */
public class LennardJones {
    private final double cutoff;
    private final double cutoffSquared;
    private final double[][] sigma;
    private final double[][] epsilon;

    private final int count;
    private final double[][] positions;
    private final double[][] forces;
    private final PeriodicBox box;
    private final CellList cellList;
    private int[] speciesAt;

    private double potentialEnergy;
    private final double[] delta = new double[3];

    public LennardJones(SimulationConfig config) {
        cutoff = config.cutoff;
        cutoffSquared = cutoff * cutoff;
        int speciesCount = config.species.size();
        sigma = new double[speciesCount][speciesCount];
        epsilon = new double[speciesCount][speciesCount];
        for (int a = 0; a < speciesCount; a++) {
            Species sa = config.species.get(a);
            for (int b = a; b < speciesCount; b++) {
                Species sb = config.species.get(b);
                double sigmaAb = 0.5 * (sa.sigma + sb.sigma);
                double epsilonAb = StrictMath.sqrt(sa.epsilon * sb.epsilon);
                sigma[a][b] = sigmaAb;
                sigma[b][a] = sigmaAb;
                epsilon[a][b] = epsilonAb;
                epsilon[b][a] = epsilonAb;
            }
        }
        count = config.particles.size();
        positions = new double[count][3];
        forces = new double[count][3];
        box = new PeriodicBox(config.boxLength);
        cellList = new CellList(config);
    }

    public PeriodicBox getBox() {
        return box;
    }

    public double[][] getForces() {
        return forces;
    }

    public int getCount() {
        return count;
    }

    public void setSpeciesAt(int[] speciesIndices) {
        this.speciesAt = speciesIndices;
    }

    public void setPositions(double[][] source) {
        for (int i = 0; i < count; i++) {
            System.arraycopy(source[i], 0, positions[i], 0, 3);
        }
    }

    /**
     * Zero the forces, evaluate all interacting pairs and return the total
     * potential energy of the system.
     */
    public double computeForces() {
        for (int i = 0; i < count; i++) {
            forces[i][0] = 0.0;
            forces[i][1] = 0.0;
            forces[i][2] = 0.0;
        }
        potentialEnergy = 0.0;
        cellList.rebuild(positions, box);
        if (cellList.isUsable()) {
            cellList.forEachPair(this::interact);
        } else {
            for (int i = 0; i < count; i++) {
                for (int j = i + 1; j < count; j++) {
                    interact(i, j);
                }
            }
        }
        return potentialEnergy;
    }

    private void interact(int i, int j) {
        double[] pi = positions[i];
        double[] pj = positions[j];
        delta[0] = pi[0] - pj[0];
        delta[1] = pi[1] - pj[1];
        delta[2] = pi[2] - pj[2];
        box.minimumImage(delta);
        double rSquared = delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2];
        if (rSquared >= cutoffSquared || rSquared == 0.0) {
            return;
        }
        double sigmaAb = sigma[speciesAt[i]][speciesAt[j]];
        double epsilonAb = epsilon[speciesAt[i]][speciesAt[j]];
        double sigmaOverR6 = StrictMath.pow(sigmaAb * sigmaAb / rSquared, 3.0);

        double sigma6 = sigmaAb * sigmaAb * sigmaAb * sigmaAb * sigmaAb * sigmaAb;
        double cutoff6 = cutoff * cutoff * cutoff * cutoff * cutoff * cutoff;
        double cutoff12 = cutoff6 * cutoff6;
        double forceAtCutoff = 24.0 * epsilonAb * sigma6 * (2.0 / (cutoff12 * cutoff) - 1.0 / (cutoff6 * cutoff));
        double energyAtCutoff = 4.0 * epsilonAb * sigma6 * (1.0 / cutoff12 - 1.0 / cutoff6);

        double r = StrictMath.sqrt(rSquared);
        double forceMagnitude =
                24.0 * epsilonAb * sigmaOverR6 * (2.0 * sigmaOverR6 - 1.0) / rSquared - forceAtCutoff / r;
        double fx = forceMagnitude * delta[0];
        double fy = forceMagnitude * delta[1];
        double fz = forceMagnitude * delta[2];
        double[] fi = forces[i];
        double[] fj = forces[j];
        fi[0] += fx;
        fi[1] += fy;
        fi[2] += fz;
        fj[0] -= fx;
        fj[1] -= fy;
        fj[2] -= fz;
        potentialEnergy += 4.0 * epsilonAb * sigmaOverR6 * (sigmaOverR6 - 1.0) - energyAtCutoff;
    }
}
