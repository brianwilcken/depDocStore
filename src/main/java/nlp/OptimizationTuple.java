package nlp;

import java.io.Serializable;

public class OptimizationTuple implements Serializable {
    public int i;
    public int c;
    public double P;

    public OptimizationTuple(int i, int c) {
        this.i = i;
        this.c = c;
        this.P = 0;
    }

    public int getI() {
        return i;
    }

    public void setI(int i) {
        this.i = i;
    }

    public int getC() {
        return c;
    }

    public void setC(int c) {
        this.c = c;
    }

    public double getP() {
        return P;
    }

    public void setP(double p) {
        P = p;
    }
}
