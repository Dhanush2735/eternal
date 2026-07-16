package com.eternalengine.population;

/**
 * Social class distribution, fractions sum approximately to 1.
 */
public final class ClassDistribution {
    private double nobility;
    private double clergy;
    private double merchant;
    private double artisan;
    private double peasant;
    private double slave;

    public ClassDistribution(double nobility, double clergy, double merchant, double artisan, double peasant, double slave) {
        this.nobility = clamp(nobility);
        this.clergy = clamp(clergy);
        this.merchant = clamp(merchant);
        this.artisan = clamp(artisan);
        this.peasant = clamp(peasant);
        this.slave = clamp(slave);
        normalize();
    }

    public static ClassDistribution ancientDefault() {
        return new ClassDistribution(0.03d, 0.04d, 0.05d, 0.08d, 0.65d, 0.15d);
    }

    private void normalize() {
        double sum = nobility + clergy + merchant + artisan + peasant + slave;
        if (sum <= 0.0d) {
            peasant = 1.0d;
            return;
        }
        nobility /= sum;
        clergy /= sum;
        merchant /= sum;
        artisan /= sum;
        peasant /= sum;
        slave /= sum;
    }

    private double clamp(double value) {
        if (Double.isNaN(value)) return 0.0d;
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public double nobility() { return nobility; }
    public double clergy() { return clergy; }
    public double merchant() { return merchant; }
    public double artisan() { return artisan; }
    public double peasant() { return peasant; }
    public double slave() { return slave; }

    public void setIndustrialized() {
        merchant = 0.16d;
        artisan = 0.24d;
        peasant = 0.50d;
        slave = 0.0d;
        nobility = 0.04d;
        clergy = 0.06d;
        normalize();
    }

    public double inequalityScore() {
        return Math.min(1.0d, nobility * 4.0d + slave * 2.0d + Math.max(0.0d, peasant - 0.60d));
    }
}
