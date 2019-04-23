package common;

public class RectangleTokenAtRank {
    private String token;
    private RectangleTokenAtRank prev;
    private RectangleTokenAtRank next;
    private String rank;
    private boolean retain;
    private boolean matched;
    private boolean emitted;

    public RectangleTokenAtRank(String token, String rank) {
        this.token = token;
        this.rank = rank;
    }

    public String getToken() {
        return token;
    }

    public RectangleTokenAtRank getPrev() {
        return prev;
    }

    public void setPrev(RectangleTokenAtRank prev) {
        this.prev = prev;
    }

    public RectangleTokenAtRank getNext() {
        return next;
    }

    public void setNext(RectangleTokenAtRank next) {
        this.next = next;
    }

    public String getRank() {
        return rank;
    }

    public boolean isRetain() {
        return retain;
    }

    public void setRetain(boolean retain) {
        this.retain = retain;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public boolean isEmitted() {
        return emitted;
    }

    public void setEmitted(boolean emitted) {
        this.emitted = emitted;
    }

    @Override
    public String toString() {
        return token + " @ " + rank + (retain ? " R" : "");
    }
}
