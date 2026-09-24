"""Frame-to-frame difference across a fine climb: does the backdrop POP?

jump = the worst single step between neighbouring frames, step = the average one. Only
meaningful next to the same numbers from another build.
"""
import sys, os
sys.path.insert(0, 'tools/backdrop')
import png

def measure(path, n):
    w, h, nch, px = png.read(path)
    fw = w // n
    fr = []
    for f in range(n):
        x0 = f * fw
        col = []
        for y in range(0, h, 6):
            base = y * w * nch
            for x in range(x0 + 3, x0 + fw - 3, 5):
                i = base + x * nch
                col.append((px[i], px[i+1], px[i+2]))
        fr.append(col)
    d = []
    for i in range(1, n):
        a, b = fr[i-1], fr[i]
        m = len(a)
        d.append(sum(abs(a[k][0]-b[k][0]) + abs(a[k][1]-b[k][1]) + abs(a[k][2]-b[k][2])
                     for k in range(m)) / (3.0 * m))
    return max(d), sum(d) / len(d)

if __name__ == '__main__':
    root, n = sys.argv[1], int(sys.argv[2])
    for fn in sorted(os.listdir(root)):
        if fn.endswith('.png'):
            j, s = measure(os.path.join(root, fn), n)
            print(f'{fn[:-4]:9s} {j:7.1f} {s:7.1f}')
