#!/usr/bin/env python3
"""Thundergun (BO1/BO3): big black ribbed turbine drum with an open intake and inner cone, silver motor
housing with a hose loop, tan chevron-tread turbine wheel, wiring and junction box, two stacked silver
capacitors with labels, hazard-striped end plate, dark under-rail with raked front and rear grips.
Measured from the BO1 third-person render (605 px -> 34 units); muzzle towards -Z."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from gunkit import Gun

g = Gun('thundergun_v3', offset=(0, -2, 0))
C = 8


def Z(px):
    return -15 + (px - 50) / 17.8


def Y(py):
    return 14 - (py - 455) / 17.8


DY = Y(522)
# Turbine drum: ribbed black shell, rib rings, longitudinal ribs, open intake with inner cone
g.cyl('z', (C, DY), 3.6, Z(50), Z(186), 'darkmetal', seg=16, name='drum')
for px in (60, 92, 124, 156):
    g.cyl('z', (C, DY), 3.75, Z(px), Z(px + 6), 'gunmetal', seg=16, name='drum_ring')
for ang in (0, 45):
    g.box((C - 3.8, DY - 0.18, Z(56)), (C + 3.8, DY + 0.18, Z(184)), 'gunmetal', rot=('z', ang, (C, DY, (Z(56) + Z(184)) / 2)), name='drum_rib')
    g.box((C - 0.18, DY - 3.8, Z(56)), (C + 0.18, DY + 3.8, Z(184)), 'gunmetal', rot=('z', ang, (C, DY, (Z(56) + Z(184)) / 2)), name='drum_rib')
g.cyl('z', (C, DY), 3.0, Z(46), Z(52), 'rubber', seg=16, name='intake')
g.cyl('z', (C, DY), 1.4, Z(48), Z(70), 'steel', seg=8, name='inner_cone')
g.cyl('z', (C, DY), 0.6, Z(40), Z(48), 'steel', seg=8, name='cone_tip')

# Silver motor housing with hose loop, turbine wheel
g.cyl('z', (C, DY), 3.45, Z(186), Z(262), 'steel', seg=16, name='motor_housing')
g.cyl('z', (C, DY), 3.55, Z(196), Z(204), 'gunmetal', seg=16, name='housing_band')
g.wire([(C + 3.4, Y(520), Z(200)), (C + 3.4, Y(455), Z(205)), (C + 3.4, Y(440), Z(225)), (C + 3.4, Y(470), Z(245)),
        (C + 3.4, Y(540), Z(240))], 0.45, 'rubber', name='hose')
g.cyl('z', (C, DY), 3.3, Z(258), Z(336), 'tread', seg=16, name='turbine_wheel')
g.cyl('z', (C, DY), 2.0, Z(336), Z(345), 'darkmetal', seg=16, name='wheel_hub')

# Wiring section and junction box
g.cyl('z', (C, Y(530)), 0.6, Z(340), Z(400), 'darkmetal', seg=8, name='spine')
g.box((C - 0.7, Y(545), Z(380)), (C + 0.7, Y(495), Z(420)), 'putty', name='junction_box')
g.wire([(C + 0.75, Y(520), Z(345)), (C + 0.75, Y(500), Z(370)), (C + 0.75, Y(520), Z(395))], 0.2, 'fez_red', name='wire_red')
g.wire([(C - 0.75, Y(540), Z(345)), (C - 0.75, Y(560), Z(370)), (C - 0.75, Y(530), Z(395))], 0.2, 'rubber', name='wire_black')

# Stacked capacitors with labels, hazard end plate
g.cyl('z', (C, Y(487)), 1.5, Z(395), Z(625), 'steel', seg=16, name='capacitor_top')
g.cyl('z', (C, Y(552)), 1.8, Z(395), Z(625), 'steel', seg=16, name='capacitor_bottom')
g.box((C + 1.45, Y(505), Z(470)), (C + 1.55, Y(470), Z(540)), 'hazard', name='capacitor_label')
for px in (520, 534, 548, 562):
    g.box((C + 1.45, Y(510), Z(px)), (C + 1.6, Y(470), Z(px + 8)), 'fez_red', name='cell')
g.box((C - 2.0, Y(592), Z(612)), (C + 2.0, Y(458), Z(655)), 'darkmetal', name='end_plate')
for (x0, x1, y0, y1) in ((-2.05, 2.05, 458, 470), (-2.05, 2.05, 580, 592), (-2.05, -1.6, 458, 592), (1.6, 2.05, 458, 592)):
    g.box((C + x0, Y(y1), Z(608)), (C + x1, Y(y0), Z(658)), 'hazard', name='end_plate_hazard')

# Under-rail, raked front grip, rear grip, trigger
g.box((C - 0.55, Y(612), Z(150)), (C + 0.55, Y(588), Z(520)), 'darkmetal', name='rail')
FRONT = None
g.box((C - 0.9, Y(722), Z(156)), (C + 0.9, Y(606), Z(206)), 'polymer_dark', name='front_grip')
REAR = None
g.box((C - 0.95, Y(716), Z(434)), (C + 0.95, Y(606), Z(490)), 'polymer_dark', name='rear_grip')
g.box((C - 0.1, Y(640), Z(398)), (C + 0.1, Y(612), Z(408)), 'steel', name='trigger')
g.box((C - 0.2, Y(645), Z(392)), (C + 0.2, Y(640), Z(430)), 'darkmetal', name='guard')


# --- detail pass ---------------------------------------------------------------------------------
# Drum: front lip and radial intake vanes
g.cyl('z', (C, DY), 3.75, Z(50), Z(56), 'gunmetal', seg=16, name='drum_lip')
for ang in (0, 45, -45, 90):
    if ang == 90:
        g.box((C - 0.12, DY - 2.6, Z(54)), (C + 0.12, DY + 2.6, Z(66)), 'steel', name='intake_vane')
    else:
        g.box((C - 2.6, DY - 0.12, Z(54)), (C + 2.6, DY + 0.12, Z(66)), 'steel', rot=('z', ang, (C, DY, Z(60))), name='intake_vane')
# Motor housing: bolt ring, top fan grille box, side panel with engraving
for ang_y, ang_z in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    g.cyl('z', (C + ang_y * 3.3, DY + ang_z * 3.3), 0.22, Z(186), Z(192), 'darkmetal', seg=8, name='housing_bolt')
g.box((C - 1.4, DY + 3.2, Z(200)), (C + 1.4, DY + 4.0, Z(250)), 'gunmetal', name='fan_box')
g.box((C - 1.2, DY + 3.95, Z(205)), (C + 1.2, DY + 4.05, Z(245)), 'grille', name='fan_grille')
g.box((C + 3.4, DY - 1.0, Z(206)), (C + 3.5, DY + 0.8, Z(250)), 'engraved_steel', name='housing_plate')
# Junction box: circuit board face and extra wiring
g.box((C + 0.7, Y(540), Z(384)), (C + 0.78, Y(500), Z(416)), 'pcb', name='junction_pcb')
g.wire([(C + 0.75, Y(512), Z(418)), (C + 0.75, Y(470), Z(440)), (C + 0.75, Y(472), Z(470))], 0.18, 'gold', name='wire_yellow')
g.wire([(C - 0.75, Y(520), Z(418)), (C - 0.75, Y(575), Z(430)), (C - 0.75, Y(570), Z(470))], 0.18, 'fez_red', name='wire_red_2')
# Capacitors: three black discs at the front of the top one, end caps, warning label, straps
for dx in (-0.85, 0.0, 0.85):
    g.cyl('z', (C + dx, Y(487)), 0.4, Z(386), Z(396), 'rubber', seg=8, name='front_disc')
for py, r in ((487, 1.6), (552, 1.9)):
    g.cyl('z', (C, Y(py)), r, Z(395), Z(402), 'gunmetal', seg=16, name='capacitor_cap')
    g.cyl('z', (C, Y(py)), r, Z(600), Z(606), 'gunmetal', seg=16, name='capacitor_cap')
g.box((C - 1.55, Y(505), Z(440)), (C - 1.45, Y(470), Z(500)), 'label_warn', name='danger_label')
for px in (430, 585):
    g.cyl('z', (C, Y(520)), 2.05, Z(px), Z(px + 6), 'darkmetal', seg=16, name='capacitor_strap')
# Frame brackets from the rail up to the housing and capacitors, ribbed grip wraps
for px in (200, 470):
    g.box((C - 0.25, Y(592), Z(px)), (C + 0.25, Y(560), Z(px + 10)), 'darkmetal', name='bracket')
for grip_px, pivot in ((178, FRONT), (455, REAR)):
    for py in (640, 665, 690):
        g.box((C - 0.98, Y(py + 8), Z(grip_px - 22)), (C + 0.98, Y(py), Z(grip_px + 28)), 'rubber', name='grip_rib')


# --- detail pass 2 ---
import math
for i in range(8):
    a = math.radians(i * 45 + 22.5)
    g.cyl('z', (C + 3.4 * math.cos(a), DY + 3.4 * math.sin(a)), 0.2, Z(47), Z(53), 'steel', seg=8, name='lip_rivet')
g.cyl('x', (DY, Z(235)), 1.0, C - 3.7, C - 3.35, 'steel', seg=16, name='gauge_bezel')
g.cyl('x', (DY, Z(235)), 0.78, C - 3.78, C - 3.6, 'gauge', seg=16, name='gauge_face')
for dx, mat in ((-0.6, 'brass'), (0.6, 'brass')):
    g.cyl('y', (C + dx, Z(500)), 0.25, Y(487) + 1.4, Y(487) + 1.9, mat, seg=8, name='cap_terminal')
    g.cyl('y', (C + dx, Z(500)), 0.35, Y(487) + 1.9, Y(487) + 2.1, 'rubber', seg=8, name='terminal_boot')
for px in (240, 560):
    for side in (-1, 1):
        g.ring('x', (Y(588), Z(px)), 0.28, 0.5, C + side * 0.55 - 0.08, C + side * 0.55 + 0.08, 'steel', name='sling_swivel')
for dx in (-0.45, 0.45):
    g.cyl('x', (Y(545) + 0.0, Z(400)), 0.1, C + dx - 0.05, C + dx + 0.05, 'steel', seg=8, name='box_screw')
g.box((C - 0.72, Y(535), Z(398)), (C + 0.72, Y(530), Z(402)), 'steel', name='box_hinge')

# --- detail pass 3 ---
g.box((C - 1.6, DY + 2.8, Z(268)), (C + 1.6, DY + 3.7, Z(280)), 'gunmetal', name='wheel_strap_a')
g.box((C - 1.6, DY + 2.8, Z(318)), (C + 1.6, DY + 3.7, Z(330)), 'gunmetal', name='wheel_strap_b')
g.box((C - 1.6, DY + 3.4, Z(268)), (C + 1.6, DY + 3.7, Z(330)), 'darkmetal', name='wheel_strap_bar')
for ang in (45, 135, 225, 315):
    a = math.radians(ang)
    g.cyl('z', (C + 1.4 * math.cos(a), DY + 1.4 * math.sin(a)), 0.2, Z(345), Z(350), 'steel', seg=8, name='hub_bolt')
for px in (260, 300, 340, 380):
    for side in (-1, 1):
        g.box((C + side * 0.55 - (0.08 if side < 0 else 0), Y(606), Z(px)), (C + side * 0.55 + (0.08 if side > 0 else 0), Y(594), Z(px + 14)), 'gunmetal', name='rail_slot')
for gz in (181, 462):
    for side in (-1, 1):
        g.cyl('x', (Y(630), Z(gz)), 0.2, C + side * 0.9 - 0.04, C + side * 0.9 + 0.04, 'steel', seg=8, name='grip_pin')
g.box((C - 0.95, Y(726), Z(154)), (C + 0.95, Y(720), Z(208)), 'steel', name='front_mag_plate')
g.box((C - 1.0, Y(720), Z(432)), (C + 1.0, Y(714), Z(492)), 'steel', name='rear_mag_plate')
g.cyl('z', (C, Y(487)), 1.58, Z(450), Z(458), 'brass', seg=16, name='cap_band')
g.cyl('z', (C, Y(487)), 1.58, Z(560), Z(568), 'brass', seg=16, name='cap_band')
g.box((C - 0.5, Y(645), Z(392)), (C - 0.2, Y(640), Z(396)), 'steel', name='guard_stud')

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 2, -2], "scale": [0.42, 0.42, 0.42]},
    "thirdperson_lefthand": {"translation": [0, 2, -2], "scale": [0.42, 0.42, 0.42]},
    "firstperson_righthand": {"translation": [2, 0, -2], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"translation": [2, 0, -2], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 2, 0], "scale": [0.34, 0.34, 0.34]},
    "gui": {"rotation": [90, -45, 90], "translation": [0, 0, 0], "scale": [0.44, 0.44, 0.44]},
    "head": {"rotation": [0, 90, 0], "translation": [0, -27.5, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"rotation": [-90, -90, 0], "translation": [0, 0, -1], "scale": [0.9, 0.9, 0.9]},
}

if __name__ == '__main__':
    g.save(DISPLAY)
