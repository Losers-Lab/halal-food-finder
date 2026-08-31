"""Regenerate the favicon/app-icon set from the tight head crop (SC-172)."""
from PIL import Image

SRC = "brand/tahir/tahirs-head-cropped.jpg"
OUT = "brand/tahir/icons"

im = Image.open(SRC).convert("RGB")
print("source:", im.size)


def save(img, path, size):
    img.resize((size, size), Image.LANCZOS).save(path, "PNG")


# Flat standard sizes (edge-to-edge, no background)
for s in (16, 32, 48, 180, 192, 512):
    save(im, f"{OUT}/icon-{s}.png", s)

# apple-touch-icon-180 (opaque, edge-to-edge is fine since face fills frame)
save(im, f"{OUT}/apple-touch-icon-180.png", 180)

# Maskable: face kept within 80% safe zone, brand green background
def maskable(size):
    bg = Image.new("RGB", (size, size), (31, 92, 61))  # stamp green #1F5C3D
    inner = int(size * 0.78)
    face = im.resize((inner, inner), Image.LANCZOS)
    bg.paste(face, ((size - inner) // 2, (size - inner) // 2))
    return bg

maskable(192).save(f"{OUT}/maskable-192.png", "PNG")
maskable(512).save(f"{OUT}/maskable-512.png", "PNG")

# Multi-frame favicon.ico (16/32/48)
im.resize((48, 48), Image.LANCZOS).save(
    f"{OUT}/favicon.ico",
    format="ICO",
    sizes=[(16, 16), (32, 32), (48, 48)],
    append_images=[
        im.resize((16, 16), Image.LANCZOS),
        im.resize((32, 32), Image.LANCZOS),
    ],
)

# Frontend app icons
save(im, "frontend/src/app/icon.png", 512)
save(im, "frontend/src/app/apple-icon.png", 180)

print("done")
