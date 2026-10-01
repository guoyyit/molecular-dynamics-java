package com.github.guoyyit.moleculardynamicsjava.model;

public class Species {
    public String name;
    public double mass;
    public double sigma;
    public double epsilon;

    public Species() {
    }

    public Species(String name, double mass, double sigma, double epsilon) {
        this.name = name;
        this.mass = mass;
        this.sigma = sigma;
        this.epsilon = epsilon;
    }
}
