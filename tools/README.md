# Model authoring toolchain

Self-contained pipeline for authoring COM:Z 3D item models (guns, machines) without a GUI
and without any external service. Pure `python3` + `Pillow` — no npm/native deps.

## Why this exists

Minecraft item models are just BlockBench-format JSON (cuboid `elements` + `textures` +
`display` transforms). They can be written directly. The only thing missing for a tight
author→verify loop was *seeing* the model before in-game testing — `model_preview.py` closes
that gap by rendering a model to a PNG you (or the assistant, via vision) can inspect.

## Files

- **`model_preview.py`** — isometric (2:1 dimetric) previewer. Renders a model JSON + its
  texture to a PNG. Flat per-face shading + painter's algorithm; not pixel-accurate Minecraft
  lighting, but enough to catch shape/proportion/UV errors fast.
  ```
  python3 tools/model_preview.py <model.json> [-o out.png] [--size 512] [--pack resourcepack/comz-pack]
  ```
  Texture refs resolve under `<pack>/assets/minecraft/textures/<ref>.png`; a missing texture
  renders as a magenta checker so it is obvious.

- **`modelkit.py`** — build models from primitives instead of hand-typing cuboids. Import it
  from a per-gun script; it accumulates boxes, auto-packs a tiny texture atlas, and writes the
  model JSON + a base-coloured PNG. See its module docstring for an example.

## Workflow per model

1. Drop a reference image in `refs/guns/<name>.png` (BO render/screenshot). Read it for
   silhouette + colours.
2. Author geometry with `modelkit` (or edit JSON directly), colour the boxes.
3. `model_preview.py` → inspect the PNG → iterate on geometry/UV/texture.
4. Refine the texture PNG (Pillow or by hand) for detail beyond flat swatches.
5. Write the item-def under `assets/comz/items/gun/<name>.json` pointing at the model.
6. Commit; verify in-game on the 26.2 server (the renderer is an approximation).

## Limits

- Preview shading is fixed per face, not real block-light — judge form, not final look.
- `modelkit`'s auto-atlas gives one flat swatch per box; paint the PNG for real detail.
- Always do a final in-game check on 26.2 — display transforms (`gui`, `firstperson`) and
  true lighting only show correctly there.
