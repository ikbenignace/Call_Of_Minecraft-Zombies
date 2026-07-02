export const meta = {
  name: 'flat-guns-3d',
  description: 'Bring the remaining 2D-sprite guns to 3D, one subagent per weapon, self-verified by render',
  phases: [
    { title: 'Model', detail: 'one agent per flat gun: build 3D + texture + self-verify render loop' },
  ],
}

const ROOT = '/Users/ignace.mella/repos/worktrees/Call_Of_Minecraft-Zombies/proof-ibis/Call_Of_Minecraft-Zombies'
const SCRATCH = '/private/tmp/claude-502/-Users-ignace-mella-repos-worktrees-Call-Of-Minecraft-Zombies-proof-ibis-Call-Of-Minecraft-Zombies/ecf640a9-959d-4532-a00d-91d6c9207e14/scratchpad'

// Guns that are currently flat 2D sprites (parent custom/item/handheld*) — model them to 3D.
const WEAPONS = [
  { key: 'm16',         name: 'Colt M16A1',  cat: 'rifle',  desc: 'Classic M16A1: long thin barrel, triangular ribbed handguard, a CARRY HANDLE on top with the rear sight inside it, straight 20-round magazine, fixed A1 stock.' },
  { key: 'mp5',         name: 'MP5',         cat: 'smg',    desc: 'H&K MP5: slim SMG, curved 30-round magazine, slim ribbed handguard, front sight ring, retractable wire stock, navy trigger group.' },
  { key: 'chicom',      name: 'Chicom CQB',  cat: 'smg',    desc: 'Chicom QCB: very compact boxy PDW-style SMG, short barrel, straight box magazine, top rail, vertical foregrip, stubby.' },
  { key: 'peacekeeper', name: 'Peacekeeper', cat: 'smg',    desc: 'Peacekeeper SMG/carbine hybrid: modern AR-ish carbine, rounded rail handguard, straight magazine, collapsible stock, flat-top rail.' },
  { key: 'r870',        name: 'R870 MCS',    cat: 'shotgun',desc: 'Remington 870: pump-action shotgun, long barrel with a tube magazine UNDER the barrel, ribbed pump grip mid-body, pistol grip + short stock.' },
  { key: 'olympia',     name: 'Olympia',     cat: 'shotgun',desc: 'Olympia: short double-barrel break-action shotgun, TWO side-by-side barrels, wooden forend and stock, exposed hammers, short.' },
  { key: 'rpd',         name: 'RPD',         cat: 'lmg',    desc: 'RPD light machine gun: belt-fed LMG, round ammo drum/box under the receiver, long barrel with bipod at the front, wooden stock and grip.' },
  { key: 'svu',         name: 'SVU',         cat: 'sniper', desc: 'SVU (bullpup Dragunov): bullpup sniper — magazine BEHIND the pistol grip, big scope on top, long barrel with a wide muzzle brake, skeleton frame.' },
  { key: 'executioner', name: 'Executioner', cat: 'pistol', desc: 'Executioner: a REVOLVER that fires shotgun shells — a fat round revolver cylinder in the middle, short stubby barrel, revolver grip, hammer at the back. Handgun-sized.' },
  { key: 'thundergun',  name: 'Thundergun',  cat: 'special',desc: 'Thundergun wonder weapon: a large air-cannon — TWO big fat round barrels side by side at the front, a bulky boxy body/receiver, pistol grip, sci-fi vents. Chunky and wide.' },
  { key: 'wunderwaffe', name: 'Wunderwaffe DG-2', cat: 'special', desc: 'Wunderwaffe DG-2 tesla rifle: a wooden rifle stock at the rear, a cluster of metal CYLINDERS/coils and a glowing tesla emitter at the front, glowing electric-blue accent parts, antennae. Use a few light-blue accent boxes for the glow.' },
]

const SCHEMA = {
  type: 'object',
  required: ['key', 'ok', 'boxes', 'iterations', 'self_score', 'notes'],
  properties: {
    key: { type: 'string' },
    ok: { type: 'boolean' },
    boxes: { type: 'integer' },
    iterations: { type: 'integer' },
    self_score: { type: 'integer' },
    notes: { type: 'string' },
  },
}

function prompt(w) {
  return `You are modeling ONE Black Ops 2 weapon as a 3D Minecraft item model, fully autonomously, verifying it yourself by rendering and LOOKING at the result. This gun is currently a flat 2D sprite; replace it with a real 3D model.

WEAPON: ${w.name} (key: ${w.key}, class: ${w.cat})
REAL-WEAPON SHAPE (model from this well-known gun): ${w.desc}

WORKING DIR (absolute — write ONLY under here, NEVER any other repo path):
${ROOT}

TOOLCHAIN (read first):
- ${ROOT}/tools/modelkit.py  — build models from primitive boxes. Import with PYTHONPATH=${ROOT}/tools.
  Model(name, texture_size=64); m.box([x0,y0,z0],[x1,y1,z1], color=(r,g,b), rotation={"angle":-22.5,"axis":"x","origin":[..]});
  m.save_model(path, texture_ref); m.save_texture(path). It auto-creates a detailed bevelled texture
  atlas (one cell per box) AND the correct in-hand display block — you do NOT set display yourself.
- ${ROOT}/tools/model_preview.py — renders a model JSON + texture to a PNG you can view.
- ${ROOT}/tools/README.md.

CONVENTIONS (match the existing 3d_guns):
- Barrel points along +Z. Length z ≈ 0..18 (pistols ~10, LMG/sniper up to ~24). Centre around x ≈ 8; body y ≈ 4..11.
- texture_size=64. Aim for ~35-70 boxes — enough for real detail (separate barrel, receiver, mag, stock, grip, sight/scope, handguard, the gun's SIGNATURE feature from the description). Use distinct colors per part (receiver dark gray 60,62,68; barrel near-black 28,28,30; wood 120,80,45; mag 40,40,44; scope black; electric glow 90,200,255).
- Use box rotation for angled mags/grips/stocks where it helps the silhouette.

STEPS (all in ${ROOT}):
1. Read tools/modelkit.py.
2. Write a build script ${SCRATCH}/build_${w.key}.py that imports modelkit and builds the gun, saving:
   - model -> ${ROOT}/resourcepack/comz-pack/assets/minecraft/models/custom/item/gen/${w.key}.json (texture_ref "custom/item/gen/${w.key}")
   - texture -> ${ROOT}/resourcepack/comz-pack/assets/minecraft/textures/custom/item/gen/${w.key}.png
3. Render: cd ${ROOT} && python3 tools/model_preview.py resourcepack/comz-pack/assets/minecraft/models/custom/item/gen/${w.key}.json -o ${SCRATCH}/${w.key}_v3.png --size 512
4. READ ${SCRATCH}/${w.key}_v3.png. Compare critically to the real ${w.name}. Right silhouette? signature feature present and proportioned?
5. Iterate: edit build script, re-save, re-render, re-read. At LEAST 3 improvement rounds (4+ renders total). Stop when it clearly reads as a ${w.cat} resembling the ${w.name}.
6. Repoint the item-def (it currently points at a flat sprite): set "model.model" in
   ${ROOT}/resourcepack/comz-pack/assets/comz/items/gun/${w.key}.json
   to "minecraft:custom/item/gen/${w.key}". Use Edit; keep JSON valid.
7. Validate JSON for both the model file and the item-def with python3 -c "import json;json.load(open('...'))".

RULES: only write under ${ROOT} (absolute paths). Do not edit the _pap variant or other guns. Final answer MUST be the structured object.`
}

phase('Model')
const results = await parallel(WEAPONS.map(w => () =>
  agent(prompt(w), { label: `gun:${w.key}`, phase: 'Model', schema: SCHEMA })
))
const done = results.filter(Boolean)
const ok = done.filter(r => r.ok)
log(`3D-ified ${ok.length}/${WEAPONS.length} flat guns. Avg self-score ${(done.reduce((a, r) => a + (r.self_score || 0), 0) / (done.length || 1)).toFixed(1)}`)
return { total: WEAPONS.length, written: ok.length, results: done }
