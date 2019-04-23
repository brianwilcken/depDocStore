package common;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class RectangularRegion {
    private Rectangle rectangle;
    private Set<RectangularRegion> left;
    private Set<RectangularRegion> right;
    private Set<RectangularRegion> above;
    private Set<RectangularRegion> below;
    private String rightText;
    private String leftText;
    private String aboveText;
    private String belowText;

    public RectangularRegion(Rectangle rectangle) {
        this.rectangle = rectangle;
        left = new HashSet<>();
        right = new HashSet<>();
        above = new HashSet<>();
        below = new HashSet<>();
    }

    public Rectangle getRectangle() {
        return rectangle;
    }

    public Set<RectangularRegion> getLeft() {
        return left;
    }

    public Set<RectangularRegion> getRight() {
        return right;
    }

    public Set<RectangularRegion> getAbove() {
        return above;
    }

    public Set<RectangularRegion> getBelow() {
        return below;
    }

    public String getRightText() {
        return rightText;
    }

    public void setRightText(String rightText) {
        this.rightText = rightText;
    }

    public String getLeftText() {
        return leftText;
    }

    public void setLeftText(String leftText) {
        this.leftText = leftText;
    }

    public String getAboveText() {
        return aboveText;
    }

    public void setAboveText(String aboveText) {
        this.aboveText = aboveText;
    }

    public String getBelowText() {
        return belowText;
    }

    public void setBelowText(String belowText) {
        this.belowText = belowText;
    }
}
