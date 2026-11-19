"""seguir2.py dir ini x0 y0 x1 y1 [mv] -> dy de la plantilla (bordes verticales, del fotograma ini) en cada fotograma desde ini."""
import sys, glob, os
import numpy as np
from PIL import Image
d = sys.argv[1]; ini = int(sys.argv[2]); x0, y0, x1, y1 = map(int, sys.argv[3:7])
mv = int(sys.argv[7]) if len(sys.argv) > 7 else 20
fs = sorted(glob.glob(os.path.join(d, 'f_*.png')))[ini - 1:]
def bordes(f):
    a = np.asarray(Image.open(f).convert('L'), dtype=np.float32)
    g = np.abs(np.diff(a, axis=0)); return (g > 40).astype(np.float32)
t = bordes(fs[0])[y0:y1, x0:x1]
out = []
for f in fs:
    b = bordes(f)
    errs = [(np.abs(b[y0 + dy:y1 + dy, x0:x1] - t).mean(), dy) for dy in range(-mv, mv + 1)]
    out.append(min(errs)[1])
print(' '.join(map(str, out)))
