package textextraction;

import java.awt.*;

public class TextExtractionCandidate {
    private Rectangle rect;
    private int rot;
    private TextExtractionTask candidate;

    public TextExtractionCandidate(Rectangle rect, int rot, TextExtractionTask candidate) {
        this.rect = rect;
        this.rot = rot;
        this.candidate = candidate;
    }

    public Rectangle getRect() {
        return rect;
    }

    public void setRect(Rectangle rect) {
        this.rect = rect;
    }

    public int getRot() {
        return rot;
    }

    public void setRot(int rot) {
        this.rot = rot;
    }

    public TextExtractionTask getCandidate() {
        return candidate;
    }

    public void setCandidate(TextExtractionTask candidate) {
        this.candidate = candidate;
    }
}
