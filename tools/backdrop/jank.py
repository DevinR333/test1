"""Names the frame where a backdrop jumps, and what jumped.

Two faults you can see but cannot find by reading code: a big area of the frame changing colour
between one step of the climb and the next, and the whole scene sliding sideways. Both are
measured here off the rendered pixels.

  colour  - per frame, how many pixels are close to each quantised colour. The biggest
            frame-to-frame change in a single colour, with that colour, says WHAT popped.
  shift   - per frame, the mean brightness of each column. Cross-correlated with the next
            frame, the offset of the best match is how far the picture slid sideways. A
            backdrop drifts by a pixel or two; a pop-in jumps.

Usage: python3 tools/backdrop/jank.py <strip.png> <frames>
"""
import sys
sys.path.insert(0, 'tools/backdrop')
import png

def load(path, n):
    w, h, nch, px = png.read(path)
    fw = w // n
    frames = []
    for f in range(n):
        x0 = f * fw
        hist = {}
        cols = []
        for x in range(x0 + 2, x0 + fw - 2, 3):
            tot = 0; cnt = 0
            for y in range(0, h, 5):
                i = (y * w + x) * nch
                r, g, b = px[i], px[i+1], px[i+2]
                k = (r >> 4, g >> 4, b >> 4)
                hist[k] = hist.get(k, 0) + 1
                tot += r + g + b; cnt += 1
            cols.append(tot / max(1, cnt))
        frames.append((hist, cols))
    return frames

def main(path, n):
    fr = load(path, n)
    worst_c = (0, 0, None)
    worst_s = (0, 0, 0)
    for i in range(1, n):
        ha, ca = fr[i-1]
        hb, cb = fr[i]
        for k in set(ha) | set(hb):
            d = abs(hb.get(k, 0) - ha.get(k, 0))
            if d > worst_c[0]:
                worst_c = (d, i, k)
        # best horizontal alignment between the two column profiles
        best = (1e18, 0)
        m = len(ca)
        for off in range(-14, 15):
            s = 0; cnt = 0
            for x in range(m):
                y = x + off
                if 0 <= y < m:
                    s += (ca[x] - cb[y]) ** 2; cnt += 1
            if cnt > m * 0.6:
                v = s / cnt
                if v < best[0]:
                    best = (v, off)
        if abs(best[1]) > abs(worst_s[0]):
            worst_s = (best[1], i, best[0])
    d, i, k = worst_c
    tot = sum(fr[0][0].values())
    print(f'  colour pop : frame {i}, {100.0*d/max(1,tot):.1f}% of the frame changed to/from '
          f'#{(k[0]<<4):02X}{(k[1]<<4):02X}{(k[2]<<4):02X}')
    print(f'  side shift : {worst_s[0]} columns at frame {worst_s[1]}')

if __name__ == '__main__':
    main(sys.argv[1], int(sys.argv[2]))
