"""fotogramas.py video.mp4 salida_dir [fps] [recorte x0,y0,x1,y1] -> extrae fotogramas y una tira con todos."""
import os, subprocess, sys, glob
import imageio_ffmpeg
from PIL import Image
v, out = sys.argv[1], sys.argv[2]
fps = sys.argv[3] if len(sys.argv) > 3 else "10"
os.makedirs(out, exist_ok=True)
for f in glob.glob(os.path.join(out, "f_*.png")): os.remove(f)
subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-loglevel", "error", "-i", v, "-vf", f"fps={fps}", os.path.join(out, "f_%04d.png")], check=True)
fs = sorted(glob.glob(os.path.join(out, "f_*.png")))
print(len(fs), "fotogramas")
