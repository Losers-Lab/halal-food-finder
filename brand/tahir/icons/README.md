# Tahir's List — favicon / app-icon set

Generated from **`brand/tahir/tahirs-head-cropped.jpg`** (630x630 JPEG — tight
face-only crop of Tahir, no shoulders, no background; updated SC-172,
previously `tahir-head.jpg` head+shoulders on light-blue).

## Regenerating

All sizes are derived from the source with Lanczos downsampling (see the
generation script in the task history / `gen_icons.py` used for SC-172).
Hosts have no Python toolchains — run Pillow inside a container, e.g.:

```
docker run --rm -v "$PWD":/w -w /w python:3.12-slim \
  sh -c 'pip install -q pillow && python gen_icons.py'
```

## Files

| File | Size | Notes |
| --- | --- | --- |
| `icon-16.png` | 16 | favicon small |
| `icon-32.png` | 32 | favicon standard |
| `icon-48.png` | 48 | favicon large |
| `favicon.ico` | 16/32/48 | multi-frame ICO |
| `apple-touch-icon-180.png` | 180 | iOS home screen |
| `icon-192.png` / `icon-512.png` | 192/512 | PWA manifest |
| `maskable-192.png` / `maskable-512.png` | 192/512 | face inset to ~78% safe zone on stamp-green `#1F5C3D` background |
| `qa-16px-contact-sheet.png` | — | QA artifact: 16/32/48/64/128 px legibility check |

Standard sizes are edge-to-edge (the face fills the frame, so no extra
background is added). `frontend/src/app/icon.png` (512) and
`frontend/src/app/apple-icon.png` (180) are copies of the same generation and
are served by Next.js metadata conventions.

Legibility: round glasses and smile survive down to 16 px (see QA contact
sheet).
