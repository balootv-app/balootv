"""mosaico.py dir salida.png ancho_celda col n1 n2 ... [--recorte x0,y0,x1,y1] -> mosaico de fotogramas numerados."""
import sys, os
from PIL import Image, ImageDraw
d, sal, w, cols = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
args = sys.argv[5:]
rec = None
if '--recorte' in args:
    i = args.index('--recorte'); rec = tuple(map(int, args[i + 1].split(','))); args = args[:i] + args[i + 2:]
ns = [int(a) for a in args]
ims = []
for n in ns:
    im = Image.open(os.path.join(d, 'f_%04d.png' % n))
    if rec: im = im.crop(rec)
    h = int(im.height * w / im.width)
    im = im.resize((w, h)); ImageDraw.Draw(im).text((4, 2), str(n), fill=(255, 0, 0)); ims.append(im)
h = ims[0].height; rows = (len(ims) + cols - 1) // cols
c = Image.new('RGB', (cols * w, rows * h))
for k, im in enumerate(ims): c.paste(im, ((k % cols) * w, (k // cols) * h))
c.save(sal)
