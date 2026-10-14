#!/usr/bin/env python3
"""Commando (BO1, XM177E2): long moderator on a short barrel, triangular front sight base, fat round
ribbed handguard with delta ring, M16 upper with carry handle and rear sight, magwell + 20-round
magazine, A1 grip, buffer tube with collapsible stock. 31 units long; muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('commando_v3', offset=(0, -1.5, 4))
C = 8


def Z(px):
    return -15 + (px - 20) / 15.5


def Y(py):
    return 14.8 - (py - 45) / 15.5


def box(x0, x1, y0, y1, hw, mat, name, **kw):
    g.box((C - hw, Y(y1), Z(x0)), (C + hw, Y(y0), Z(x1)), mat, name=name, **kw)


BY = Y(88)

# Flash hider, barrel, front sight base with post
g.cyl('z', (C, BY), 0.4, Z(20), Z(62), 'darkmetal', seg=8, name='moderator')
for px in (28, 40, 52):
    g.cyl('z', (C, BY), 0.45, Z(px), Z(px + 5), 'gunmetal', seg=8, name='moderator_ring')
g.cyl('z', (C, BY), 0.3, Z(62), Z(112), 'steel', seg=8, name='barrel')
box(72, 92, 84, 104, 0.4, 'darkmetal', 'fsb_base')
box(76, 87, 48, 86, 0.25, 'darkmetal', 'fsb_upright')
box(78, 85, 40, 50, 0.1, 'darkmetal', 'front_post')

# Round ribbed handguard, delta ring
box(110, 190, 64, 113, 0.95, 'polymer_dark', 'handguard')
for px in (122, 138, 154, 170):
    box(px, px + 4, 62, 115, 0.99, 'darkmetal', 'handguard_rib')
box(186, 196, 60, 117, 1.0, 'gunmetal', 'delta_ring')
box(106, 112, 70, 106, 0.9, 'gunmetal', 'handguard_cap')

# Upper receiver with carry handle, rear sight, charging handle, ejection port, forward assist
box(190, 335, 80, 122, 0.75, 'gunmetal', 'upper')
box(195, 215, 64, 82, 0.35, 'gunmetal', 'handle_front')
box(300, 322, 64, 82, 0.35, 'gunmetal', 'handle_rear')
box(195, 322, 62, 72, 0.42, 'gunmetal', 'handle_top')
box(262, 285, 44, 63, 0.2, 'darkmetal', 'rear_sight')
box(318, 338, 82, 94, 0.3, 'steel', 'charging_handle')
box(235, 290, 90, 108, 0.8, 'rubber', 'ejection_port')
g.cyl('z', (C + 0.85, Y(100)), 0.25, Z(292), Z(310), 'steel', seg=8, name='forward_assist')

# Lower receiver, magwell, curved magazine, trigger guard, A1 grip
box(200, 340, 118, 148, 0.72, 'darkmetal', 'lower')
box(196, 256, 142, 172, 0.75, 'darkmetal', 'magwell')
box(200, 252, 168, 192, 0.6, 'gunmetal', 'magazine')
g.box((C - 0.6, Y(246), Z(212)), (C + 0.6, Y(190), Z(256)), 'gunmetal',
      rot=('x', 22.5, (C, Y(190), Z(234))), name='magazine_lower')
box(256, 305, 148, 160, 0.25, 'darkmetal', 'guard_bottom')
box(270, 276, 140, 156, 0.1, 'steel', 'trigger')
g.box((C - 0.6, Y(228), Z(255)), (C + 0.6, Y(145), Z(300)), 'polymer_dark',
      rot=('x', -22.5, (C, Y(145), Z(278))), name='grip')

# Buffer tube, stock body, belly and butt pad
box(330, 482, 98, 128, 0.7, 'darkmetal', 'buffer_tube')
box(376, 482, 112, 160, 0.7, 'polymer_dark', 'stock_body')
box(430, 484, 150, 182, 0.5, 'polymer_dark', 'stock_toe')
box(482, 500, 108, 182, 0.7, 'rubber', 'butt_pad')


def sb(xa, xb, ya, yb, za, zb, mat, **kw):
    g.box((C + min(xa, xb), min(ya, yb), min(za, zb)), (C + max(xa, xb), max(ya, yb), max(za, zb)), mat, **kw)

# Detail pass
g.ring('x', (Y(120), Z(191)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='front_swivel')
g.ring('x', (Y(185), Z(460)), 0.22, 0.42, C - 0.12, C + 0.12, 'steel', name='rear_swivel')
sb(0.72, 0.9, Y(150), Y(158), Z(238), Z(250), 'steel', name='mag_release')
sb(-0.7, -0.85, Y(124), Y(133), Z(262), Z(282), 'steel', name='bolt_catch')
g.cyl('x', (Y(140), Z(326)), 0.22, C - 0.9, C - 0.65, 'steel', seg=8, name='selector')
sb(-0.9, -0.97, Y(137), Y(143), Z(318), Z(334), 'steel', name='selector_lever')
for px in (210, 332):
    g.cyl('x', (Y(128), Z(px)), 0.14, C - 0.78, C + 0.78, 'steel', seg=8, name='takedown_pin')
sb(-0.07, 0.07, Y(50), Y(58), Z(285), Z(289), 'rubber', name='aperture')

# Detail pass 2
box(36, 42, 74, 102, 0.42, 'steel', 'muzzle_brake_cap')
box(75, 95, 78, 82, 0.45, 'steel', 'fsb_collar')
box(262, 285, 63, 66, 0.22, 'steel', 'sight_base')
box(300, 325, 60, 64, 0.38, 'darkmetal', 'handle_top_rail')
box(196, 340, 144, 148, 0.74, 'steel', 'lower_edge')
box(200, 252, 188, 194, 0.62, 'steel', 'mag_baseplate')
box(256, 300, 126, 134, 0.0 + 0.74, 'rubber', 'dust_cover_seam')
box(430, 482, 100, 104, 0.72, 'steel', 'tube_ring')
g.cyl('x', (Y(115), Z(470)), 0.14, C - 0.72, C + 0.72, 'steel', seg=8, name='stock_pin')
box(482, 500, 112, 118, 0.74, 'darkmetal', 'butt_hook')
sb(-0.3, 0.3, Y(120), Y(116), Z(112), Z(186), 'darkmetal', name='handguard_spine')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, -3], "scale": [0.52, 0.52, 0.52]},
    "thirdperson_lefthand": {"translation": [0, 3, -3], "scale": [0.52, 0.52, 0.52]},
    "firstperson_righthand": {"translation": [1, 1.5, -2], "scale": [0.66, 0.66, 0.66]},
    "firstperson_lefthand": {"translation": [1, 1.5, -2], "scale": [0.66, 0.66, 0.66]},
    "ground": {"translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.56, 0.56, 0.56]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
