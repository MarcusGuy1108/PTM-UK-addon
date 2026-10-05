# PTM UK Addon

UK road furniture for [Public Transport Mod 2](https://www.curseforge.com/minecraft/mc-mods/public-transport-mod) (PTM2) on **Minecraft Forge 1.20.1**.

## What's in it so far

### UK signal heads

All of them are real PTM2 traffic lights: PTM2's intersection controller switches them and its AI cars (and pedestrians, for the crossing heads) obey them.

| Head | LED | LED, tunnel hoods | Classic bulb |
|---|:-:|:-:|:-:|
| Signal (red / amber / green) | ✓ | ✓ | ✓ (also with large green "pod") |
| Left / right / ahead green-arrow signal | ✓ | ✓ | ✓ |
| Left / right filter signal (extra green filter arrow) | ✓ | ✓ | ✓ |
| Cycle signal (bicycle symbols) | ✓ | | |
| Low-level cycle signal (eye height) | ✓ | | |
| Far-side pedestrian signal (pelican, flashing green man) | ✓ | | ✓ |
| Puffin near-side pedestrian signal | ✓ | | |
| Toucan signal (green man + green cycle) | ✓ | | |

- **LED**: modern LED heads with short angled cowls.
- **LED, tunnel hoods**: LED heads with long tunnel hoods.
- **Classic bulb**: older incandescent heads with deep hoods, fresnel lenses and a visible bulb. Their lamps **fade** on and off like real filament bulbs; LED heads switch instantly.
- **Classic bulb, large green**: older layout with small red and amber aspects over a big 300 mm green "pod", on a light grey board.

LED pedestrian heads have round dot-matrix lenses with cowls, as on current UK crossings.

Vehicle heads come with a grey backing board with a white border. **Sneak + right-click with an empty hand** removes or adds the board.

They show the proper UK sequence: **red → red + amber → green → amber → red**. They never flash green, and they flash amber if PTM2's intersection is in night mode. On crossings:

- Pelican heads flash the green man.
- Puffin heads show the red man during clearance.
- Toucan heads go blank during clearance.

### Poles

UK poles that PTM2 treats as its own streetposts, so UK heads and PTM2's own lights and signs mount on them:

- **Traffic signal pole**, black or galvanised (114 mm), with ground collar and domed cap.
- **Sign pole**, galvanised or black (76 mm), with plastic cap.

Heads mounted on a pole sit a small gap off it on clamp brackets, as on real UK poles. Put a head or sign straight on top of a UK pole and the pole carries on up behind it, the UK way. Mount two heads back to back on one pole for primary and secondary signals.

### Signal furniture

These are decorative and line up with the head above or below:

- **Illuminated sign plates** that go under a head: No Left Turn, No Right Turn, No U-Turn, Ahead Only, Turn Left, Turn Right, and Except Buses, Taxis & Cycles. Sneak + right-click toggles their board.
- **Vehicle detector** that sits on top of a head.
- **Pedestrian push-button unit**: "PEDESTRIANS push button and wait for signal opposite", wait / cross with care diagram and yellow tactile cones. Press it (right-click) and WAIT lights up for 10 seconds.

### Street furniture

In the **UK Street Furniture** tab:

- **Bins:** wheelie bins (black, grey, green, blue, brown), communal 1100 L bin, litter bin, dog waste bin, grit bin.
- **Street icons:** pillar box, red K6 telephone box, bus shelter, bus stop with timetable case.
- **Seating and cycles:** benches (steel, wood), cycle stand.
- **Bollards and crossings:** cast iron and steel bollards, illuminated keep-left bollard, Belisha beacon.
- **Parking and charging:** pay and display machine, parking meter, EV charging point.
- **Roadside utilities:** telecoms cabinet, signal controller cabinet, feeder pillar, fire hydrant marker, manhole cover, drain grate.
- **Street lights:** LED and old sodium lanterns. Put them on top of a UK pole and the arm reaches out over the road.
- **Speed cameras:**
  - Gatso: rear facing; put it on top of a UK sign pole.
  - Truvelo: forward facing, on its own post.
  - SPECS average-speed camera: on an arm; put it on top of a pole.
  - The matching speed camera sign is in the road signs tab.
- **Road works:** traffic cone, red and white barrier, ROAD CLOSED sign.
- **Motorway roadside:** concrete step barrier, Armco crash barrier (connects like a fence), orange SOS emergency phone, marker post.
- **Fences:** palisade, black railings, pedestrian guardrail, close board, Heras temporary fencing. They connect like vanilla fences.

### Building blocks

In the **UK Building Blocks** tab, each as a full block and a slab:

- **Paving:** concrete paving slabs, red and grey block paving, buff and red blister tactile paving, corduroy tactile paving, granite setts, concrete kerb.
- **Tarmac:** standard and red (bus and cycle lanes).
- **Walls:** red brick, London stock brick, blue engineering brick, pebbledash, white render, Portland stone.

### Road signs

In the **UK Road Signs** tab, mounted on poles with clamps like the signal heads:

- **Speed limits:** 20 to 70, and national speed limit.
- **Regulatory:** no entry, give way, stop, one way, keep left.
- **Speed camera** sign.
- **Parking:** parking, pay at machine, disabled parking.
- **Warnings:** traffic signals ahead, pedestrian crossing, children, road works.

### Direction signs (editable)

Also in **UK Road Signs**: direction signs you write yourself. Place one and right-click it to open the editor.

- **Colour scheme:**
  - Local (white)
  - Primary route (green, with yellow route numbers)
  - Motorway (blue)
  - Tourist (brown)
  - Temporary / diversion (yellow)
  - Street name plate

  You can change the scheme later in the editor.
- **Size:** 1–8 blocks wide and 1–6 blocks tall. The block you place is the bottom middle of the panel.
- **Junction diagram:** ahead, left, right, crossroads, T-junction, ahead + left/right, roundabout (3 or 4 arms), or 2–4 lane arrows for overhead signs. Or no diagram, for stacked destinations.
- **Text:** a header bar (e.g. *HANGMAN'S CROSSROADS*), the ahead destinations above the diagram, and left/right destinations beside it. One destination per line. In commands, `|` also starts a new line.
- **Route patches** inside the text:
  - `[A34]` gives a green patch with yellow numbers.
  - `{M1}` gives a blue motorway patch.
  - `<B1043>` gives a black-bordered white patch.
  - On green signs, plain A/B road numbers turn yellow automatically.
- **Legs:** galvanised posts reach down to the ground. Turn them off for wall-mounted signs, or for overhead signs hung from a gantry beam, which get hanger brackets instead.

The text uses a Transport-style typeface (bundled DejaVu Sans), heavy for black-on-white and medium for white-on-colour, as on real UK signs.

### Motorway gantries

In the **UK Motorway** tab:

- **Gantry beam:** a box-truss beam with a maintenance walkway. It runs across the way you're looking when you place it.
- **Gantry leg:** a lattice leg that gets a concrete plinth at the bottom.
- **Lane signal (MS4):** hangs under the beam, one per lane.
  - Right-click to change the whole gantry at once: blank, 70, 60, 50, 40, 30, 20 in a red ring, end of restriction, red X, move left/right arrows, QUEUE, FOG.
  - Sneak + right-click changes just that lane, e.g. to close it with a red X.
  - A redstone signal shows a red X.
  - Amber lanterns flash in pairs for speeds and warnings; red lamps flash with the red X.
  - The dot-matrix pictures are baked into the block textures, so they show in Distant Horizons too.
- **Variable message sign:** right-click to write up to three lines of amber dot-matrix text. You can set its width (2–8 blocks), turn the flashing amber lanterns on or off, and pick from preset messages.
- **Overhead direction signs:** use a blue motorway direction sign with *lane arrows*, legs off, and hang it under the beam.

### Power lines

In the **UK Power** tab, each builder places a whole tower made of real blocks, so towers show up in **Distant Horizons**' LODs. The line runs the way you're facing.

- **400 kV suspension pylon:** about 46 blocks tall, 3 cross-arms per side, glass insulator strings, twin conductors.
- **400 kV tension pylon:** heavier, for angles. Insulator strings run along the line, with jumper loops.
- **400 kV terminal pylon:** the line ends here and comes down to cable sealing ends on a platform.
- **132 kV pylon:** the smaller lattice tower.
- **T-pylon:** a white tubular mast with diamond "earring" insulators.
- **11 kV wooden pole** and **33 kV H pole.**

Breaking any part removes the whole tower. In survival you get the builder back.

**Overhead Line Tool:** right-click a tower, then the next one, to string the conductors between them. Spans can be up to 256 blocks. Each conductor connects to its partner on the other tower, with a realistic sag; 400 kV lines are drawn as twin bundles, plus the earth wire. Sneak + right-click a tower to take down its spans. The wires are drawn by the client, so Distant Horizons doesn't show them beyond your normal render distance.

### Buses and cars

In the **UK Buses & Cars** tab. These are PTM2 vehicles, so they drive, take routes and carry passengers like PTM2's own:

- **Alexander ALX400** and **Alexander Dennis Enviro400** (London spec): dual door, red, with a Stagecoach-style interior.
- **UK Double Decker**: the original stand-alone design.
- **Len Livery** versions of the ALX400 and Enviro400: red at the front fading to white at the back, with yellow and black stripes and music notes. To paint your own artwork on the white part, override `assets/ptmuk/textures/entity/bus/alx400_len.png` or `e400_len.png` in a resource pack. Also override the matching `_left.png` file, which is used with left-hand traffic on.
- **Kia K4 GT-Line S** (red), as a car.

With PTM2's left-hand traffic setting on, the buses have their doors on the left, as in the UK. All three have:

- Working upper decks and stairs.
- Destination blinds (front, side and rear) and inside next-stop screens.
- Lights that glow at night.
- Working mirrors.
- A yellow card reader by the entrance.

### Fares and bus stop kit

- **Cube Card** (pay as you go):
  - Holds credit in PTM2 money. Right-click by a bus's yellow card reader to pay the fare, which is the world's PTM2 single ticket price.
  - Any tap within an hour of a paid one is free (a hopper fare).
  - Right-click anywhere else to see the balance.
- **Bus Pass**: free travel; every tap is accepted.
- PTM2's own **bank card** works contactless (the fare comes straight from the bank account). PTM2's **transport card** uses one of its rides.
- Taps work on the reader of any PTM2 vehicle with a validator, not only these buses.
- **Ticket & Top-up Machine**:
  - Right-click with a Cube Card to add ten fares of credit (sneak for one fare), paid from your PTM2 bank account.
  - Right-click with an empty hand for a new card.
- **Card Reader**: a stand-alone reader on a post. An accepted tap gives a one-second redstone pulse, for doors or gates.
- **Bus Countdown Sign**: a London-style live arrivals board. Place it inside or next to a PTM2 stop and it shows the next three buses from PTM2's own departures, plus the stop name and the time. It stands on a pole, or hangs from a shelter roof.
- **Road markings**: BUS STOP (yellow) and BUS LANE (white). They read for traffic coming from where you stood when placing them.
- **Road signs** (in the road signs tab): bus lane, buses only, bus stop clearway.

## How it works with PTM2

The UK signals extend PTM2's own `TrafficLight` block, so as far as PTM2 is concerned they *are* PTM2 traffic lights:

- They're registered in PTM2's traffic storage when placed.
- PTM2's intersection controller switches them, so you set up intersections exactly as you do now with PTM2's tools.
- PTM2's AI cars stop and go for them like any PTM2 light.

PTM2 itself cycles red → yellow → green → flashing green → yellow → red. The addon shows a yellow that comes straight after red as **red + amber**, and a yellow after green as **amber only**. It shows the flashing green as steady green. The colour PTM2 stores, which is what the cars obey, is never changed.

Each head type copies the matching PTM2 light's settings: arrow heads act like PTM2's arrow lights, filter heads like its 4-light section lights, and cycle and pedestrian heads like its cycle and pedestrian lights. So they apply to the same lanes and road users.

Place them like PTM2 lights: against a PTM2 streetpost or wall, or free-standing.

## Building

Needs JDK 17. PTM2 is downloaded automatically from CurseMaven (it is MIT licensed) and is a required dependency at runtime.

```sh
./gradlew build                # mod jar in build/libs/
./gradlew runClient            # dev client with PTM2 loaded
./gradlew runGameTestServer    # automated in-game tests
```

### Models and textures

The signal models, blockstates and textures are generated by `tools/generate_assets.py` (needs Python 3, Pillow and numpy). The HD textures are painted procedurally by `tools/signal_textures.py`: 128 px lenses, faceplates, housings and 256 px boards. Hoods are rounded tubes built from rotated plates. Each head is split into parts: housing, board, and each lens on, off or flashing. `client/SignalModels.java` bakes every part once and combines them per block state, which keeps memory use low. Edit the script and re-run it rather than editing the generated JSON by hand:

```sh
pip install pillow numpy
python3 tools/generate_assets.py
```

## Roadmap ideas

- Speed cameras (Gatso, Truvelo, SPECS average-speed, HADECS) that detect PTM2 vehicles
- Belisha beacons and zebra crossings
