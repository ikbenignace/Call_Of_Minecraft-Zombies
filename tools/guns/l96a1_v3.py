#!/usr/bin/env python3
"""L96A1 (BO1): long fluted barrel with muzzle brake, olive-green chassis stock with thumbhole grip,
cheek piece and butt pad, black receiver and bolt handle, large scope with thick objective and eyepiece,
detachable box magazine. ~46 units; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('l96a1_v3', offset=(0, -1, 0))
C = 8
BY = 10.0

# Muzzle brake, thick fluted barrel
g.cyl('z', (C, BY), 0.62, -15, -13.4, 'steel', seg=8, name='muzzle_brake')
g.cyl('z', (C, BY), 0.45, -13.4, 1.0, 'gunmetal', seg=8, name='barrel')
for z in (-11.0, -8.0, -5.0):
    g.box((C - 0.2, BY + 0.38, z), (C + 0.2, BY + 0.47, z + 2.2), 'darkmetal', name='barrel_flute')

# Folded bipod under the barrel
g.box((C - 0.5, 8.0, -4.2), (C + 0.5, BY - 0.3, -2.4), 'gunmetal', name='bipod_mount')
g.box((C - 0.7, 7.6, -6.2), (C + 0.7, 8.2, -1.2), 'gunmetal', name='bipod_legs')

# Olive chassis: forend, action bed, thumbhole stock around an open hole, cheek piece, butt pad
g.box((C - 1.0, 7.7, -1.0), (C + 1.0, 10.2, 9.0), 'polymer_olive', name='forend')
g.box((C - 1.0, 7.4, 9.0), (C + 1.0, 10.4, 17.0), 'polymer_olive', name='action_bed')
g.box((C - 0.8, 8.4, 17.0), (C + 0.8, 10.1, 26.0), 'polymer_olive', name='stock_top')
g.box((C - 0.8, 4.2, 17.0), (C + 0.8, 5.4, 26.0), 'polymer_olive', name='stock_bottom')
g.box((C - 0.8, 4.2, 22.4), (C + 0.8, 10.1, 26.0), 'polymer_olive', name='stock_rear')
g.box((C - 0.75, 4.0, 16.4), (C + 0.75, 8.6, 17.4), 'polymer_olive', rot=('x', -22.5, (C, 8.2, 17.2)), name='thumbhole_grip')
g.box((C - 0.84, 10.1, 19.0), (C + 0.84, 10.35, 25.0), 'polymer_olive', name='cheek_piece')
g.box((C - 0.88, 4.1, 26.0), (C + 0.88, 10.1, 28.2), 'rubber', name='butt_pad')

# Receiver, bolt and handle
g.box((C - 0.7, 9.0, 6.0), (C + 0.7, 10.9, 16.6), 'darkmetal', name='receiver')
g.cyl('z', (C, 10.2), 0.5, 13.0, 17.4, 'steel', seg=8, name='bolt')
g.box((C + 0.5, 9.9, 16.4), (C + 1.5, 10.3, 17.0), 'steel', name='bolt_handle')
g.cyl('x', (10.1, 16.9), 0.3, C + 1.4, C + 1.9, 'darkmetal', seg=8, name='bolt_knob')

# Scope on rings: mounts, tube, turrets, objective, eyepiece
for z in (9.0, 12.0):
    g.box((C - 0.5, 10.6, z), (C + 0.5, 11.8, z + 0.9), 'steel', name='scope_ring')
g.cyl('z', (C, 12.4), 0.75, 8.0, 15.2, 'gunmetal', seg=8, name='scope_tube')
g.cyl('z', (C, 12.4), 1.1, 5.2, 8.2, 'darkmetal', seg=8, name='scope_objective')
g.cyl('z', (C, 12.4), 1.0, 4.9, 5.3, 'glass', seg=8, name='objective_lens')
g.cyl('z', (C, 12.4), 1.0, 14.8, 16.6, 'darkmetal', seg=8, name='scope_eyepiece')
g.cyl('y', (C, 10.5), 0.45, 12.9, 13.7, 'darkmetal', seg=8, name='scope_turret')
g.cyl('x', (12.4, 10.5), 0.4, C + 0.6, C + 1.3, 'darkmetal', seg=8, name='scope_turret_side')

# Magazine and trigger guard
g.box((C - 0.55, 5.9, 11.7), (C + 0.55, 7.6, 13.4), 'darkmetal', name='magazine')
g.box((C - 0.22, 6.1, 14.0), (C + 0.22, 6.4, 17.0), 'darkmetal', name='guard_bottom')
g.box((C - 0.1, 6.4, 14.6), (C + 0.1, 7.4, 14.9), 'steel', name='trigger')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
sb(0.68, 0.74, 9.4, 10.4, 8.0, 10.5, 'rubber', name='ejection_port')
for z in (-14.6, -14.0):
    sb(-0.64, 0.64, BY - 0.2, BY + 0.2, z, z + 0.35, 'rubber', name='brake_port')
g.cyl('x', (7.9, -3.3), 0.15, C - 0.75, C + 0.75, 'steel', seg=8, name='bipod_pin')
g.ring('x', (4.0, 24.2), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
g.ring('x', (7.5, 7.0), 0.2, 0.4, C - 0.12, C + 0.12, 'steel', name='front_swivel')
for z in (9.45, 12.45):
    g.cyl('x', (11.2, z), 0.12, C - 0.55, C + 0.55, 'steel', seg=8, name='ring_screw')
sb(-0.7, -0.95, 9.6, 10.0, 15.0, 16.0, 'steel', name='safety')
sb(-0.6, 0.6, 5.75, 5.95, 11.6, 13.5, 'steel', name='mag_baseplate')
g.cyl('y', (C, 19.8), 0.3, 10.3, 10.7, 'darkmetal', seg=8, name='cheek_knob')
g.cyl('z', (C, 12.4), 0.9, 16.6, 17.0, 'rubber', seg=8, name='eyecup')

# Detail pass 2
for s in (-1, 1):
    for z in (7.2, 11.0):
        sb(s * 0.7, s * 0.74, 9.2, 10.8, z, z + 0.12, 'rubber', name='receiver_seam')
    for z in (1.5, 3.5, 5.5):
        sb(s * 1.0, s * 1.04, 8.2, 9.8, z, z + 0.3, 'rubber', name='forend_groove')
for z in (14.6, 16.0):
    g.ring('z', (C, 10.2), 0.5, 0.6, z, z + 0.2, 'steel', name='bolt_ring')
g.ring('z', (C, 12.4), 0.75, 0.85, 13.6, 14.4, 'steel', name='power_ring')
g.ring('z', (C, 12.4), 1.1, 1.18, 7.0, 7.3, 'steel', name='objective_ring')
sb(-0.7, 0.7, 7.4, 7.6, -6.2, -5.4, 'rubber', name='bipod_foot')
g.cyl('y', (C, 24.0), 0.25, 10.3, 10.6, 'darkmetal', seg=8, name='cheek_knob2')
sb(0.55, 0.7, 6.2, 6.8, 11.8, 12.4, 'steel', name='mag_release')
for y in (6.4, 7.0):
    sb(-0.58, 0.58, y, y + 0.2, 11.8, 13.3, 'rubber', name='mag_rib')
sb(-0.22, 0.22, 6.1, 7.4, 13.8, 14.0, 'darkmetal', name='guard_front')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"translation": [1, 1, -2], "scale": [0.48, 0.48, 0.48]},
    "firstperson_lefthand": {"translation": [1, 1, -2], "scale": [0.48, 0.48, 0.48]},
    "ground": {"translation": [0, 2, 0], "scale": [0.32, 0.32, 0.32]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.38, 0.38, 0.38]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
