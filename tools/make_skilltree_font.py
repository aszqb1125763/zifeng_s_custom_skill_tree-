from pathlib import Path

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(r"C:\Users\Administrator\AppData\Local\Microsoft\Windows\Fonts\NotoSansSC-VariableFont_wght.ttf")
WORK = ROOT / "build" / "font-work"
WORK.mkdir(parents=True, exist_ok=True)
STATIC = WORK / "noto-sans-sc-regular.ttf"
OUTPUT = WORK / "skilltree.ttf"

font = TTFont(str(SOURCE))
font = instantiateVariableFont(font, {"wght": 400}, inplace=False)
font.save(str(STATIC))

chars = set(chr(i) for i in range(0x20, 0x7F))
chars.update(chr(i) for i in range(0xA0, 0x0250))
chars.update(chr(i) for i in range(0x2000, 0x2070))
chars.update(chr(i) for i in range(0x20A0, 0x20C0))
chars.update(chr(i) for i in range(0x2100, 0x2140))
chars.update(chr(i) for i in range(0x2190, 0x21B0))
chars.update(chr(i) for i in range(0x2200, 0x2300))
chars.update(chr(i) for i in range(0x2500, 0x2580))
chars.update(chr(i) for i in range(0x3000, 0x3040))

for path in (ROOT / "src").rglob("*"):
    if path.is_file() and path.suffix.lower() in {".java", ".json", ".mcmeta"}:
        try:
            chars.update(path.read_text(encoding="utf-8").replace("\\n", "\n"))
        except UnicodeDecodeError:
            pass

font = TTFont(str(STATIC))
options = Options()
options.layout_features = []
options.hinting = False
options.desubroutinize = True
options.recalc_average_width = True
subsetter = Subsetter(options=options)
subsetter.populate(text="".join(sorted(chars)))
subsetter.subset(font)
font.save(str(OUTPUT))

print(f"chars={len(chars)} bytes={OUTPUT.stat().st_size}")
