"""How much SCENERY is in each frame of a strip.

A pure sky gradient is flat along every row, so the horizontal spread within a row is close to
zero. Anything the player can actually pick out - a ridge, a tower, a tree, a cloud - breaks
that. Averaged down the frame it says whether an area has gone bare, which is the failure mode
of laying scenery out once instead of tiling it.
"""
import sys, os
sys.path.insert(0, 'tools/backdrop')
import png

def frames(path, n):
    w, h, nch, px = png.read(path)
    fw = w // n
    out = []
    for f in range(n):
        x0 = f * fw
        tot = 0.0
        rows = 0
        for y in range(0, h, 4):
            base = y * w * nch
            vals = []
            for x in range(x0 + 4, x0 + fw - 4, 6):
                i = base + x * nch
                vals.append((px[i] + px[i+1] + px[i+2]) / 3.0)
            if len(vals) < 4:
                continue
            m = sum(vals) / len(vals)
            var = sum((v - m) ** 2 for v in vals) / len(vals)
            tot += var ** 0.5
            rows += 1
        out.append(tot / max(1, rows))
    return out

if __name__ == '__main__':
    root = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    worst = []
    for b in range(5):
        d = os.path.join(root, f'band{b}')
        if not os.path.isdir(d): continue
        for fn in sorted(os.listdir(d)):
            if not fn.endswith('.png'): continue
            v = frames(os.path.join(d, fn), n)
            # the last frame is the cross-fade into the next band, so judge 0..n-2
            body = v[:n-1]
            lo = min(body)
            print(f'band{b} {fn[:-4]:8s} min={lo:5.1f}  ' + ' '.join(f'{x:5.1f}' for x in v))
            worst.append((lo, b, fn[:-4]))
    worst.sort()
    print()
    print('BAREST:')
    for lo, b, s in worst[:12]:
        print(f'  band{b} {s:8s} {lo:5.1f}')
