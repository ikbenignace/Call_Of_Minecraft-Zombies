export const meta = {
  name: 'gun-models',
  description: 'Model placeholder BO2 guns to 3D, one subagent per weapon, self-verified by render',
  phases: [
    { title: 'Model', detail: 'one agent per weapon: build + texture + self-verify render loop' },
  ],
}

// Absolute worktree root — agents MUST write only under here (never the main repo). See memory.
const ROOT = '/Users/ignace.mella/repos/worktrees/Call_Of_Minecraft-Zombies/proof-ibis/Call_Of_Minecraft-Zombies'
const SCRATCH = '/private/tmp/claude-502/-Users-ignace-mella-repos-worktrees-Call-Of-Minecraft-Zombies-proof-ibis-Call-Of-Minecraft-Zombies/ecf640a9-959d-4532-a00d-91d6c9207e14/scratchpad'

// Each placeholder gun + a description of the REAL weapon to model from knowledge.
const WEAPONS = [
  { key: 'b23r',     name: 'B23R',         cat: 'pistol',  desc: 'Beretta 93R-style burst machine pistol: boxy slide, extended straight magazine, small front foregrip under the barrel.' },
  { key: 'five_seven', name: 'Five-seveN', cat: 'pistol',  desc: 'FN Five-seveN: sleek black polymer pistol, gently curved grip, slim slide, no foregrip.' },
  { key: 'kap40',    name: 'KAP-40',       cat: 'pistol',  desc: 'Compact full-auto machine pistol: short boxy body, stubby barrel, vertical grip, small.' },
  { key: 'm1216',    name: 'M1216',        cat: 'shotgun', desc: 'M1216 shotgun: long heavy bullpup-ish shotgun with a thick quad-tube magazine block under the barrel, pistol grip.' },
  { key: 's12',      name: 'S12',          cat: 'shotgun', desc: 'Saiga-12 style: AK-pattern semi-auto shotgun with a curved box magazine, wide barrel, AK furniture.' },
  { key: 'an94',     name: 'AN-94',        cat: 'rifle',   desc: 'AN-94 assault rifle: AK-like silhouette, distinctive thin front sight/muzzle assembly, curved magazine, skeleton stock.' },
  { key: 'm14',      name: 'M14',          cat: 'rifle',   desc: 'M14 battle rifle: classic wooden full stock and handguard, long barrel, straight box magazine.' },
  { key: 'm27',      name: 'M27 IAR',      cat: 'rifle',   desc: 'HK416/M27 family: modern AR-15 carbine, flat-top rail, round handguard, straight STANAG mag, collapsible stock.' },
  { key: 'smr',      name: 'SMR',          cat: 'rifle',   desc: 'SR-25/M110-style semi-auto marksman rifle: AR pattern, long heavy barrel, large scope rail, straight mag.' },
  { key: 'type25',   name: 'Type 25',      cat: 'rifle',   desc: 'QBZ-95-style bullpup assault rifle: magazine BEHIND the trigger/grip, carry handle on top, stubby front.' },
  { key: 'hamr',     name: 'HAMR',         cat: 'lmg',     desc: 'HAMR LMG: boxy IAR/LMG hybrid, thick squared receiver, heavy barrel, top scope, box magazine.' },
  { key: 'lsat',     name: 'LSAT',         cat: 'lmg',     desc: 'LSAT light machine gun: very boxy futuristic rectangular receiver, square magazine on the side, short heavy barrel.' },
  { key: 'mk48',     name: 'Mk 48',        cat: 'lmg',     desc: 'Mk 48 belt-fed LMG (M249/Mk46 family): long barrel with bipod, belt ammo box under receiver, top carry handle.' },
  { key: 'pdw57',    name: 'PDW-57',       cat: 'smg',     desc: 'P90-style bullpup SMG: horizontal top-mounted magazine running along the barrel, rounded compact body, integrated grip.' },
  { key: 'msmc',     name: 'MSMC',         cat: 'smg',     desc: 'Mini-Uzi style SMG: compact boxy body, magazine through the pistol grip, short barrel.' },
  { key: 'skorpion', name: 'Skorpion EVO', cat: 'smg',     desc: 'CZ Scorpion EVO: angular polymer SMG, straight box magazine, handguard with rail, collapsible stock.' },
  { key: 'vector',   name: 'Vector K10',   cat: 'smg',     desc: 'KRISS Vector: very distinctive angular body that steps DOWN sharply behind the magazine, vertical mag in front of trigger, boxy.' },
  { key: 'barret',   name: 'Barret 50cal', cat: 'sniper',  desc: 'Barrett M82 .50cal anti-materiel rifle: very long, big muzzle brake, large scope, thick receiver, bipod, straight mag.' },
  { key: 'dsr50',    name: 'DSR-50',       cat: 'sniper',  desc: 'DSR-50 bullpup bolt-action sniper: magazine behind the grip, very long heavy barrel with muzzle brake, big scope, bipod.' },
]

const SCHEMA = {
  type: 'object',
  required: ['key', 'ok', 'boxes', 'iterations', 'self_score', 'notes'],
  properties: {
    key: { type: 'string' },
    ok: { type: 'boolean', description: 'true if model + texture + item-def were written and render looks like the weapon' },
    boxes: { type: 'integer' },
    iterations: { type: 'integer', description: 'how many render+inspect+improve rounds were done' },
    self_score: { type: 'integer', description: '1-5 how recognizable the final render is vs the real weapon' },
    notes: { type: 'string' },
  },
}

function prompt(w) {
  return `You are modeling ONE Black Ops 2 weapon as a low-poly Minecraft item model, fully autonomously, and verifying it yourself by rendering and LOOKING at the result.

WEAPON: ${w.name} (key: ${w.key}, class: ${w.cat})
REAL-WEAPON SHAPE (model from this knowledge — it is a widely-known gun): ${w.desc}

WORKING DIR (absolute — write ONLY under here, NEVER any other repo path):
${ROOT}

TOOLCHAIN (already exists, read them first):
- ${ROOT}/tools/modelkit.py  — build models from primitive boxes with auto-UV. Import: PYTHONPATH=${ROOT}/tools
- ${ROOT}/tools/model_preview.py — renders a model JSON + texture to a PNG you can view.
- ${ROOT}/tools/README.md — workflow.

CONVENTIONS (match the existing pack):
- Barrel points along +Z. Length roughly z = 0..18 (longer for LMG/sniper, up to ~22; shorter for pistols ~10).
- Centre the weapon around x ≈ 8; body height around y = 4..11. Keep most geometry within BlockBench bounds (-16..32 allowed, but stay near 0..16 where you can).
- Use ${w.cat} proportions. Build the SILHOUETTE that makes this gun recognizable (mag position, stock, barrel length, scope, foregrip, the gun's signature feature called out above).
- Aim for ~20-45 boxes. More boxes = more detail but keep it readable.

STEPS (do all, in ${ROOT}):
1. Read tools/modelkit.py and tools/README.md.
2. Write a build script at ${SCRATCH}/build_${w.key}.py that imports modelkit and constructs the gun with box() calls (use distinct colors per part: receiver dark gray, barrel near-black, wood = brown, mag dark, scope black, accents). Save:
   - model -> ${ROOT}/resourcepack/comz-pack/assets/minecraft/models/custom/item/gen/${w.key}.json  (texture_ref "custom/item/gen/${w.key}")
   - texture -> ${ROOT}/resourcepack/comz-pack/assets/minecraft/textures/custom/item/gen/${w.key}.png
3. Render: cd ${ROOT} && python3 tools/model_preview.py resourcepack/comz-pack/assets/minecraft/models/custom/item/gen/${w.key}.json -o ${SCRATCH}/${w.key}_preview.png --size 512
4. READ ${SCRATCH}/${w.key}_preview.png (vision). Critically compare to the real ${w.name}. Is the silhouette right? mag/stock/scope/barrel in the right place and proportion?
5. Iterate: edit the build script, re-save, re-render, re-read. Do at LEAST 2 improvement rounds (3+ total renders). Stop when it clearly reads as a ${w.cat} resembling the ${w.name}.
6. Update the item-def so the game uses your model: set the "model.model" in
   ${ROOT}/resourcepack/comz-pack/assets/comz/items/gun/${w.key}.json
   to "minecraft:custom/item/gen/${w.key}". (Use Edit; keep JSON valid.)
7. Validate JSON: python3 -c "import json;json.load(open('.../gen/${w.key}.json'))" for both the model and the item-def.

IMPORTANT:
- Everything under ${ROOT} only. Use absolute paths. Do not touch any file outside ${ROOT}.
- Do not edit the _pap variant item-def. Do not touch other guns.
- Your final answer MUST be the structured object (key, ok, boxes, iterations, self_score, notes).`
}

phase('Model')
const results = await parallel(WEAPONS.map(w => () =>
  agent(prompt(w), { label: `gun:${w.key}`, phase: 'Model', schema: SCHEMA })
))

const done = results.filter(Boolean)
const ok = done.filter(r => r.ok)
log(`Modeled ${ok.length}/${WEAPONS.length} guns. Avg self-score ${(done.reduce((a, r) => a + (r.self_score || 0), 0) / (done.length || 1)).toFixed(1)}`)
return { total: WEAPONS.length, written: ok.length, results: done }
