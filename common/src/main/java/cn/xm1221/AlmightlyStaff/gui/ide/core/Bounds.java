package cn.xm1221.AlmightlyStaff.gui.ide.core;

/**
 * 不可变整数 GUI 矩形区域。
 */
public record Bounds(int x, int y, int w, int h) {
    public static final Bounds EMPTY = new Bounds(0, 0, 0, 0);

    public Bounds {
        w = Math.max(0, w);
        h = Math.max(0, h);
    }

    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public boolean isEmpty() {
        return w <= 0 || h <= 0;
    }

    public boolean contains(double mx, double my) {
        return !isEmpty() && mx >= x && mx < right() && my >= y && my < bottom();
    }

    public Bounds inset(int left, int top, int right, int bottom) {
        int nx = x + left;
        int ny = y + top;
        int nw = Math.max(0, w - left - right);
        int nh = Math.max(0, h - top - bottom);
        return new Bounds(nx, ny, nw, nh);
    }

    public Bounds inset(int pad) {
        return inset(pad, pad, pad, pad);
    }

    public Bounds intersect(Bounds other) {
        if (other == null || isEmpty() || other.isEmpty()) {
            return EMPTY;
        }
        int nx = Math.max(x, other.x);
        int ny = Math.max(y, other.y);
        int nr = Math.min(right(), other.right());
        int nb = Math.min(bottom(), other.bottom());
        if (nr <= nx || nb <= ny) {
            return EMPTY;
        }
        return new Bounds(nx, ny, nr - nx, nb - ny);
    }
}
