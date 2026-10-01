package com.github.guoyyit.moleculardynamicsjava.model;

public class Particle {
    public int speciesIndex;
    public double[] position = new double[3];
    public double[] velocity = new double[3];

    public Particle() {
    }

    public Particle(int speciesIndex, double[] position, double[] velocity) {
        this.speciesIndex = speciesIndex;
        this.position = position;
        this.velocity = velocity;
    }
}
