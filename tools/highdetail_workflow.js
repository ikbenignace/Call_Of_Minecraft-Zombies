export const meta = {
  name: 'highdetail-guns',
  description: 'Re-model the crude guns at high detail (80-180 boxes), one expert subagent per gun',
  phases: [{ title: 'Model', detail: 'one agent per gun: detailed build + self-verify render loop' }],
}

const ROOT = '/Users/ignace.mella/repos/worktrees/Call_Of_Minecraft-Zombies/proof-ibis/Call_Of_Minecraft-Zombies'
const SCRATCH = '/private/tmp/claude-502/-Users-ignace-mella-repos-worktrees-Call-Of-Minecraft-Zombies-proof-ibis-Call-Of-Minecraft-Zombies/ecf640a9-959d-4532-a00d-91d6c9207e14/scratchpad'

// Crude guns to re-model at high detail, each with an expert part breakdown.
const WEAPONS = [
  { key: 'm16', name: 'Colt M16A1', parts: 'thin barrel + 3-prong flash hider; triangular ribbed handguard (stack several ribs); A-frame front sight tower; upper+lower receiver; CARRY HANDLE as a raised bridge with a daylight gap and rear aperture sight inside; straight 20-rd mag; A1 pistol grip; fixed A1 stock + buttplate. Matte black/dark gray.' },
  { key: 'mp5', name: 'H&K MP5', parts: 'slim receiver; top cocking tube; charging-handle knob front-left; front sight ring/hood; slim ribbed handguard; curved 30-rd magazine (3 forward-canted segments); navy-style trigger group + grip; retractable wire stock (rails + buttplate). Black.' },
  { key: 'chicom', name: 'Chicom CQB (QCW-05 bullpup)', parts: 'compact BULLPUP: magazine BEHIND the pistol grip; top carry handle/rail; short barrel; vertical foregrip; chunky polymer body. Dark gray + tan accents.' },
  { key: 'msmc', name: 'MSMC (Mini-Uzi)', parts: 'boxy receiver; magazine through the pistol grip; short barrel; top charging slot; folding wire stock; ribbed grip; front sight. Black.' },
  { key: 'skorpion', name: 'CZ Scorpion EVO', parts: 'angular polymer body; straight box mag; slotted handguard; folding stock; top picatinny rail; flash hider; pistol grip + trigger guard. Black polymer.' },
  { key: 'olympia', name: 'Olympia double-barrel shotgun', parts: 'TWO side-by-side round barrels (build each as a chamfered tube); wooden forend under barrels; break-action hinge; wooden stock; twin exposed hammers; twin triggers; brass bead front sight. Wood + blued steel.' },
  { key: 'r870', name: 'Remington 870', parts: 'long barrel with vent rib; TUBE magazine under the barrel + barrel clamp; ribbed wooden PUMP forend; steel receiver + ejection port; wooden stock + recoil pad; pistol-grip wrist; bead sight. Wood + steel.' },
  { key: 'm1216', name: 'M1216', parts: 'long heavy frame; thick QUAD-TUBE magazine block under the barrel (four round tube ends on the front face); barrel on top; rear pistol grip; top rail + front/rear sights; brass charging handle. Dark gray.' },
  { key: 's12', name: 'Saiga-12 (S12)', parts: 'AK receiver + dust cover + charging handle; AK wooden handguard + gas tube + front sight post; AK wooden stock; AK pistol grip; forward-canted curved box magazine; wide shotgun barrel + choke. Wood + black.' },
  { key: 'm27', name: 'M27 IAR (HK416)', parts: 'flat-top picatinny rail (teeth); round free-float handguard with rail slots; 16in barrel + flash hider; straight STANAG mag; collapsible stock (buffer tube); AR pistol grip + trigger guard; folding sights. Black.' },
  { key: 'type25', name: 'Type 25 (QBZ-95 bullpup)', parts: 'BULLPUP: carry handle on top; magazine behind the grip; integrated front grip; short barrel + front sight; angled buttplate. Black/green polymer.' },
  { key: 'hamr', name: 'HAMR LMG', parts: 'boxy squared receiver; heavy barrel + folding bipod; top scope on rail; box magazine; carry handle; stock; pistol grip. Dark gray.' },
  { key: 'lsat', name: 'LSAT LMG', parts: 'VERY boxy rectangular futuristic receiver; square magazine on the side; short heavy barrel; top rail; vertical foregrip; stock; pistol grip. Gray/black.' },
  { key: 'mk48', name: 'Mk 48 LMG', parts: 'long fluted barrel + folding bipod at front; belt ammo box under the receiver (belt of rounds visible); top carry handle; rail; stock; pistol grip; feed tray cover. Black.' },
  { key: 'barret', name: 'Barrett M82 .50cal', parts: 'VERY long; big multi-port muzzle brake; long heavy barrel with cuts; large scope on a high rail; big rectangular receiver; pistol grip; folding bipod; straight 10-rd mag; recoil-pad stock. Black.' },
  { key: 'dsr50', name: 'DSR-50 bullpup sniper', parts: 'BULLPUP bolt-action: magazine in front of the trigger/grip; very long heavy barrel + muzzle brake; big scope; adjustable bipod; thumbhole stock; bolt handle. Green/black.' },
  { key: 'svu', name: 'SVU bullpup Dragunov', parts: 'BULLPUP: scope on top; long barrel + cylindrical muzzle brake; skeletonized stock; pistol grip; magazine in front of grip; cheek riser. Wood/black.' },
  { key: 'thundergun', name: 'Thundergun (wonder weapon)', parts: 'TWO big fat round barrels side by side at the front (chamfered tubes); bulky boxy body/receiver; side vents; pistol grip; sci-fi greebles. Chunky, dark metal.' },
  { key: 'wunderwaffe', name: 'Wunderwaffe DG-2 (wonder weapon)', parts: 'wooden rifle stock at rear; a cluster of metal CYLINDERS/coils mid-body; a tesla emitter at the front; glowing ELECTRIC-BLUE accent boxes (use 90,200,255); thin antennae. Wood + steel + blue glow.' },
  { key: 'executioner', name: 'Executioner (LeMat revolver shotgun)', parts: 'handgun-sized: a big round REVOLVER CYLINDER mid-body (chamfered); short stubby upper barrel + a fatter under-barrel; revolver grip; exposed hammer; trigger + guard. Blued steel + wood grip.' },
]

const SCHEMA = {
  type: 'object',
  required: ['key', 'ok', 'boxes', 'iterations', 'self_score', 'notes'],
  properties: {
    key: { type: 'string' }, ok: { type: 'boolean' }, boxes: { type: 'integer' },
    iterations: { type: 'integer' }, self_score: { type: 'integer' }, notes: { type: 'string' },
  },
}

function prompt(w) {
  return `Re-model ONE Black Ops 2 gun as a HIGH-DETAIL low-poly Minecraft item model, fully autonomously, verifying by rendering and LOOKING. The current model is too crude ("you can see big blocks") — make it detailed with real-weapon expertise.

WEAPON: ${w.name} (key: ${w.key})
PARTS (build ALL of these as separate boxes): ${w.parts}

WORKING DIR (write ONLY here, absolute paths): ${ROOT}

TOOLCHAIN (read first): ${ROOT}/tools/modelkit.py , ${ROOT}/tools/model_preview.py , ${ROOT}/tools/README.md
modelkit: Model(name, texture_size=128); m.box([x0,y0,z0],[x1,y1,z1], color=(r,g,b), rotation={"angle":-22.5,"axis":"x","origin":[..]}); m.save_model(path, texture_ref); m.save_texture(path). It auto-builds a detailed RGB texture atlas (one cell per box).

DETAIL REQUIREMENTS:
- Use **80-160 boxes**. This is the whole point — many small boxes = smooth detail, not chunky blocks.
- Approximate ROUND parts (barrels, cylinders, suppressors) with stepped/chamfered boxes: a stack of boxes with the corners shaved using 22.5/45 rotated boxes so it reads round, not square.
- Keep proportions slim and real: rifle body ~3-4 wide/tall, barrel ~1.5-2.5 thick, total length z ~ 0..22 (LMG/sniper up to ~28). Barrel along +Z. Centre around x=8.
- Separate EVERY part listed above; add sub-detail (panel lines, bolts, sight posts, rail teeth, vents, mag floorplate, sling points).
- Distinct colors per material: receiver (60,62,68), barrel/steel (28,28,30), wood (120,80,45), polymer (40,40,44), mag (45,45,50), scope/optic black + a (90,200,255) lens, brass (170,130,50), electric glow (90,200,255).

STEPS (in ${ROOT}):
1. Read tools/modelkit.py + README.
2. Write ${SCRATCH}/hd_${w.key}.py building the gun with 80-160 box() calls, saving:
   model -> ${ROOT}/resourcepack/comz-pack/assets/minecraft/models/custom/item/3d_guns/${w.key}.json (texture_ref "custom/custom/${w.key}")
   texture -> ${ROOT}/resourcepack/comz-pack/assets/minecraft/textures/custom/custom/${w.key}.png
3. Render: cd ${ROOT} && python3 tools/model_preview.py resourcepack/comz-pack/assets/minecraft/models/custom/item/3d_guns/${w.key}.json -o ${SCRATCH}/${w.key}_hd.png --size 512
4. READ ${SCRATCH}/${w.key}_hd.png. Compare to the real ${w.name}. Is every part present, proportioned, and does it read as detailed (not chunky)?
5. Iterate: edit, re-save, re-render, re-read. At LEAST 3 improvement rounds; ADD boxes/detail each round until it clearly reads as a detailed ${w.name}.
6. Keep ALL element coords within [-16,32] and face UVs within texture_size (modelkit handles UV; just don't exceed ~150 boxes for a 128 atlas). Validate JSON: python3 -c "import json;json.load(open('.../3d_guns/${w.key}.json'))".

RULES: only ${ROOT} (absolute). Don't touch the item-def, other guns, or originals. Final answer = the structured object {key, ok, boxes, iterations, self_score, notes}.`
}

phase('Model')
const results = await parallel(WEAPONS.map(w => () =>
  agent(prompt(w), { label: `hd:${w.key}`, phase: 'Model', schema: SCHEMA })
))
const done = results.filter(Boolean)
log(`High-detail modeled ${done.filter(r => r.ok).length}/${WEAPONS.length}. avg boxes ${Math.round(done.reduce((a, r) => a + (r.boxes || 0), 0) / (done.length || 1))}, avg score ${(done.reduce((a, r) => a + (r.self_score || 0), 0) / (done.length || 1)).toFixed(1)}`)
return { total: WEAPONS.length, ok: done.filter(r => r.ok).length, results: done }
