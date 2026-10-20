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
- **Fences:** palisade, black railings, pedestrian guardrail (galvanised or black), close board, Heras temporary fencing. They connect like vanilla fences.
- **Town centre:** a black **heritage lamp post** (in the poles tab: stack it like the other poles; the bottom one gets a cast iron base) with a **globe lantern** or a **cluster of five globes** on top, lit at night; **brick planters** with shrubs and flowers (line them up for a long bed); black **flower troughs**; **shop awnings** in green or red stripes (put one on the wall just over a shop window and it slopes out over the pavement); and a **tower clock**, a dial three blocks across in a stone surround, for the side of a clock tower.

### Prefab buildings

The **Prefab Tool** (in the **UK Buildings** tab, creative mode only) places ready-made British buildings to fill gaps in a town:

- **Sneak + right-click** to open the catalogue: pick a building from Houses, Estates, Flats, Shops, Food & Drink, Pubs & Bars, Hotels, Commercial, Offices, Civic or Other (or your own schematics in Custom), with a picture and its size.
- **Right-click** to place it where you're looking. Its front faces you, and the middle of its front edge goes on the block you're aiming at. An outline shows where it will go, with the front edge in green.
- **R** (or **Turn 90°** in the catalogue) turns it. **Mirrored** flips it left to right.
- **Undo last placed** puts back what was there before (the last 8 placements).
- **Custom** tab: import your own schematics. Put `.schem` (WorldEdit / Sponge), `.schematic` (old MCEdit / WorldEdit, or Sponge saved with that name), `.litematic` (Litematica, every region) or `.nbt` (structure block) files in the `schematics` folder in your game folder (**Open folder** opens it; WorldEdit's `config/worldedit/schematics` is listed too), press **Refresh** and pick one. It's read on your computer, shown as a map from above, and sent to the server; then place it like any prefab, turned, mirrored and undone the same way. Its south side counts as the front (the bottom of the map). **Bottom layer** chooses whether its lowest layer replaces the ground or sits on top of it. Blocks and chests from older game versions are brought up to date; blocks from mods you don't have are left out (it says how many). Up to 8 million blocks in its box.
- The ground layer replaces the surface it stands on, and on a slope the gap underneath is filled in so it doesn't float. The whole area has to be loaded, so stand near the middle of where a big estate will go.

The buildings use vanilla blocks wherever they can, plus the mod's roofing, brick, windows and shop signs. Most have simple furnished interiors, lights that show through the windows at night, gardens and bins.

They are built to the same scale as the Road Planner's roads and real maps (about 1.8 blocks to a metre: a 6-block lane is a real 3.3 m lane), so a street of them lines up with an imported map: storeys are 5 blocks floor to floor (4-block ceilings) in houses, offices and flats, a little more for shop floors and entrance lobbies, plots are about real widths (a Victorian terraced house is 8 blocks wide, a new-build detached house 21) and a 16-storey tower block is 85 blocks tall. The big sheds, campuses and country houses (supermarkets, the cinema, retail parks, the bus garage, the big schools, the leisure centre, the petrol station, the drive-thru and the two country houses) were already about real size and stay as they were.

- **Houses:** Victorian terraced house (stock brick or red brick) and a terrace of four, Edwardian and 1930s semi-detached pairs, a terrace of four post-war council houses, a new-build detached house with garage, a 1960s bungalow, a Georgian townhouse. Bigger houses: a double-fronted Victorian villa, an Edwardian Arts & Crafts house, an executive detached house with double garage, a 1930s mock-Tudor detached house, a Georgian country house with portico, wings and formal gardens, and a thatched cottage. The biggest is a red-brick **country mansion** (60 x 64): four bedrooms (two en-suite), a family bathroom, drawing, dining and family rooms, a study, a big kitchen and a boot room, round a block-paved courtyard with a fountain, room to park and a car care corner with a lift and workbench; a separate four-bay coach house garage with a workshop, joined to the house by a covered walkway; and behind it a terrace, a swimming pool sunk into the ground with a pool house, lawns, a greenhouse and vegetable beds.
- **Estates:** whole streets with roads, pavements, kerbs and street lights, opening onto your road at the front: a 1930s semi-detached cul-de-sac (ten homes), a Victorian terraced street (fourteen homes), a post-war council estate round a green with a playground, flats and lock-up garages, a new-build cul-de-sac (eight detached homes), and a tower block estate with maisonettes, car park and playground.
- **Shopfronts:** Victorian (stone pilasters, green tiled riser, timber door, cornice over a gold-on-green fascia) and modern (grey frame, tall glazing, lit fascia), 5, 7, 9 or 11 wide, to refit an old build's shops without knocking them down. Stand on the pavement and aim at the block in front of the wall where the middle of the shop goes: the shopfront replaces that stretch of wall up to the fascia, and the pavement in front (and anything on it) and the wall above are left as they are. Right-click the sign to name the shop.
- **Shops:** a corner shop with a flat above, a Victorian high street parade of three shops, a Portland stone bank, a modern convenience store, and terraced high street units that line up in a row (each with its fit-out, stairs to a flat or offices above, a roof and a back yard): a video game shop, a pharmacy, a newsagent with a parcels counter, a bookmakers, a launderette, a barber's, a phone shop, an estate agent and a discount variety store. Bigger: a four-storey Edwardian department store, a three-floor high street fashion store and an out-of-town fashion store with a lit glass entrance block.
- **Food & Drink:** a fish and chip shop, a Chinese takeaway, a kebab and pizza shop, a pizza delivery takeaway, a bakery, a coffee shop, a peri-peri chicken restaurant, an Italian pizza restaurant, an Indian restaurant, a cafe and takeaway pair and a drive-thru.
- **Pubs & Bars:** the Victorian corner pub, a big town-centre pub with an island bar, a function room and a beer garden, a Georgian coaching inn with rooms and a carriage arch, a thatched country pub, a 1960s estate pub with a pool table and darts, a gastropub, a cocktail bar with a light-up dance floor, a wine bar and a nightclub.
- **Hotels:** a budget hotel with 30 rooms, a grand Victorian hotel and a seaside B&B.
- **Civic:** a Victorian parish church, a library, a police station with cells, a fire station with appliance bays and a drill tower, a GP surgery, a Victorian primary school, a large modern primary school, a secondary academy (three storeys of classrooms, labs and computer rooms, a sports hall, canteen, library, all-weather pitch and bus bays), a leisure centre with a swimming pool, a village hall and a Victorian **clock tower** (red brick and Portland stone, an arched door, a statue in a niche, a clock on every side and a slate spire).
- **Commercial:** a **retail park terrace** (100 x 74) of five big units under a canopy on tall masts, with a landscaped car park, zebra crossings, bollards and trolley bays; a smaller three-unit retail park; a DIY warehouse with a garden centre; a gym; a bus garage; a **multiplex cinema** (56 x 64) with a car park, a glass foyer with box office, ticket machines, popcorn, drinks and pick and mix, and a corridor to four stadium-seated screens with a projector over each; a **supermarket superstore** and a **discount supermarket** (48 x 58) with car parks, trolleys, cash machines, checkouts and self-checkouts, aisles of shelves, chillers, freezers and fruit and veg; a **car dealership** with a forecourt of display bays, a glass showroom with sales desks and a service workshop with car lifts; The cinema, supermarket, dealership and restaurant names (CUBEPLEX, FRESHWAYS, SAVEMORE, ROADSTAR MOTORS, BUN & GONE) are all made up.
- **Offices:** a modern glass office block, a 1960s concrete office block, a 1990s brick office.
- **Flats:** 16- and 24-storey council tower blocks, a 12-storey 1960s slab block, a 24-storey modern residential tower, a 7-storey courtyard apartment block, a 1930s mansion block, a modern apartment block, 1960s deck-access flats and a maisonette block.
- **Other:** a Victorian parish church, a petrol station with a canopy over two pump islands, EV chargers, a kiosk shop, a price sign, an air and water machine and a working car wash.

All the shop, restaurant, pub, hotel, school and office names are made up. Change them in game: right-click a shop sign. Shop signs stacked on top of each other join into one taller sign with bigger writing (up to 8 high and 24 wide); the bottom left-hand board holds the text.

**Your own logos on signs:** put PNG pictures (with see-through backgrounds for lettering) in the `ptmuk/images` folder in your game folder (the sign editor's **Open folder** button opens it), right-click a shop sign and click **Picture** through to yours. With no shop name the picture fills the whole sign (so make the sign the shape of the logo: 8 wide and 2 high for a long one); with a name it sits at the left of it. **Board: None (on the wall)** takes the painted board away, so the logo or lettering goes straight on the brick, like the big letters on a supermarket or shopping centre. The mod comes with no real logos: the pictures are your own files, and each player's game shows the ones in its own folder (on a server, everyone who should see a logo needs the file too).

Every building is checked when the prefabs are generated: no gaps beside windows or doors, every staircase climbable (Minecraft needs three blocks of headroom over each step, not two, or you bump your head stepping up), every door with somewhere to stand on both sides (no wall, counter or sofa in the way), and every room reachable on foot from the street. Rooms left with nothing in them are listed too, and have been furnished: tills and desks in the bank, kitchens, tables, sofas and beds in the deck-access flats, workbenches and tool chests in the garages, lecterns and plants on the school and village hall stages, benches and a lifeguard chair at the pool. Garages open with their up-and-over doors (click any panel) or a door from the house.

### Road Planner (roads and junctions)

The **Road Planner** (UK Buildings tab, creative mode) opens a top-down map of the area round you to draw roads on, then builds them for you, junctions and all.

- **Draw:** click to lay a road point by point; it curves smoothly through the points. End on another road to join it (that makes a junction), or right-click to finish. Right-drag or WASD moves the map, the scroll wheel zooms.
- **Edit:** drag points around, drag the dot in the middle of a road to bend it, Delete removes, Ctrl+Z undoes. Select a point to change its junction, or a road to change its lanes, markings and pavements.
- The map reaches 24 chunks each way whatever your render distance (handy with Distant Horizons): the server sends what your game hasn't loaded, read from the saved world without loading or generating anything, and roads level against the real ground there too.
- The map shows exactly what will be built before you press **Build**. Building happens over a few seconds so big plans don't freeze the game. **Undo** puts everything back, and the plan stays on the item so you can change it and build again.
- **Follow an old road** (in Draw mode): click a road that's already built in the world (any block: an old build's coal or concrete, under carpet markings) and the planner follows it both ways down its middle to its junctions and ends, measuring its width and pavements, and turns it into planner roads of that width. Follow the other roads and they join up at the junctions; then pick a style and **Build** to rebuild them properly, on the same line.

Roads come in UK styles you can mix and adjust: main road (21 wide: two 6-block lanes, dashed centre line and 4-block pavements), residential street (15 wide), high street, wide 4-lane road, dual carriageway with a central reservation and crash barrier, country road with grass verges, one-way street and slip road. Each has settings for lanes, lane width, centre line (dashed, hazard, double white or none), pavement width and surface (light grey concrete, tarmac, paving slabs or block paving), grass verge, zebra crossings, raised kerbs, street lights, white edge lines and yellow lines (single, double, or auto: single along the road and double near junctions).

Junctions:

- **Give way** (the default where three or more roads meet): rounded kerbs, the main road's lines carry through, two broken give-way lines and a triangle on the side roads. **Stop** junctions get a solid stop line with STOP written before it.
- **Roundabouts:** a kerbed central island sized to the roads (or a size you pick) with a black and white chevron board facing each entry, a single broken give-way line at every entry, hatched splitter islands between the ways in and out with a raised kerbed nose and a keep left sign by the ring, and a roundabout warning triangle and SLOW on the road coming up to it. **Mini roundabouts** get a round white painted disc and the blue mini roundabout sign at each entry.
- **Traffic lights:** stop lines, a pedestrian crossing on every arm with red tactile paving at the dropped kerbs and LOOK RIGHT (LOOK LEFT where the traffic comes the other way) by each kerb, and black UK signal poles with LED heads facing each approach and a push-button unit for the crossing. A PTM2 intersection is set up automatically so the lights cycle straight away and PTM2's AI traffic obeys them.
- **Slip roads:** draw a one-way road onto a dual carriageway (or hold Shift when ending on any road) and it merges into the side of the road with a taper instead of making a junction.

Streets come out looking like London's:

- **Junction signs:** a give way or stop sign on a galvanised pole on the left at every give way or stop line (roundabout entries included), and no entry signs both sides where a one-way street comes out.
- **Direction signs:** a white map-type sign on its legs at the back of the pavement about 30 blocks before every roundabout, and before junctions on roads with a centre line (along the main road, and every way into traffic lights or a mini roundabout). It draws the junction as it is (crossroads, T-junction, roundabout) and says where each road goes: the nearest PTM2 district that way (or **Town Centre** for a town you laid out with the planner) and the nearest PTM2 station, or else the road's name. Right-click one to change it like any direction sign.
- **Street nameplates:** a black-on-white nameplate on posts at the back of the pavement on each corner of a junction, a few blocks along the street going off there, with its name and facing it. Streets you draw get a British street name when they're built (carried on through bends, and shown on the map); streets without pavements (motorways, country lanes) don't.
- **Zebra crossings:** the high street style (or any road with **Zebra: yes**) gets one halfway along each stretch between junctions: black and white stripes, give way lines, white zig-zags along the kerbs and down the middle, dropped kerbs with tactile paving and a Belisha beacon each end. PTM2's cars stop for people crossing.
- **Yellow box junctions:** where the roads cross at every traffic light junction is hatched in yellow inside a yellow border.

Markings are PTM2's own blocks (white, and yellow for the kerb lines), so PTM2 recognises them, and UK style: hazard lines (long dashes) approaching junctions, solid up to stop lines, and yellow lines that follow the kerb round corners (or, with kerbs turned off, the edge of the road). Pavements use PTM2's sidewalk blocks by default, so PTM2's pedestrians walk on them.

Roads are levelled into the ground: hills are cut through and dips built up with a smooth gradient (no steeper than 1 in 10, in half-block steps), junctions are flat, and the ground either side is sloped to meet the road. Trees in the way are cleared. Set PTM2 to left-hand traffic in its settings so its AI cars use the left lane.

**AI traffic:** every road you build gets PTM2 traffic points down the middle of each lane, already joined up, so PTM2's cars spawn and drive on them straight away. They keep left, carry on through bends, turn left and right at junctions (left from the left lane, right from the right lane on wider roads), go round roundabouts clockwise to every exit, merge onto and off roads from slip roads, and turn round at dead ends. Lanes stop at the edge of a junction (not in its middle), so every turn starts ahead of the car, never a loop back, and every lane has a way in and a way on. Give way junctions and roundabouts get a PTM2 junction area too, so cars take turns through them (and know a roundabout when they reach one) and never appear in the middle of one. Building onto roads you built before joins the new points onto the old ones. Hold PTM2's traffic point item to see them and their links, and change any of them by hand as usual. If PTM2 is set to drive on the right in a world, the points are turned round to suit, and switching PTM2 to left-hand traffic afterwards turns them back.

### Upgrading an old build (Block swap)

Worlds brought up from older versions lose their modded blocks: roads come back as coal and carpet, pavements as concrete. The Road Planner's **Swap** mode swaps them over a whole area at once:

- Drag a box over part of the old build (up to 256 x 256). It lists every block in it with how many there are. **Look at: open ground** takes in roads, pavements and anything thin on them (carpet markings, post bases) but not buildings, so a block used both in the ground and in walls only changes in the ground; **everything** takes in the buildings too.
- Click the arrow under a block to pick what it becomes: suggestions first (coal and dark concrete to tarmac, grey concrete and stone to paving slabs, red to red tarmac or block paving, carpet markings removed), then *remove*. Or click it and type any block's name. **Suggested** picks the first suggestion for every block that has one.
- **Swap** does it, keeping stairs and slabs turned the way they were; the map updates straight away. **Undo swap** puts back the last swap (the last 5 are kept). The box is remembered next time you open the planner.

Follow the old roads (above) to rebuild them with proper markings and kerbs, and swap the rest of the ground.

### Real maps (OpenStreetMap import)

Build a real place without measuring: the Road Planner's **Map** mode (the Map button, or M) brings in a real area's roads and buildings from OpenStreetMap.

1. On [openstreetmap.org](https://www.openstreetmap.org), find the area, press **Export**, and save the `.osm` file (the Export button handles a town centre or neighbourhood at a time).
2. Put it in the `osm_maps` folder in your game folder (the planner's **Open folder** button opens it).
3. Open the planner, move the map so its middle is where the middle of the real area should go, choose **Map** and click the file.

- **Scale:** 1.8 blocks to a metre, so a real 3.3 m lane is one of the planner's 6-block lanes and real roads come out the size of your roads. Buildings and distances use the same scale, so everything lines up. Change it with Scale - and +.
- **Roads** come in as ordinary planner roads you can edit, in the nearest UK style (main road, wide main road, residential, one-way, each side of a dual carriageway, slip roads) with the lane count from the map. Roundabouts, mini roundabouts and traffic lights become those junctions. Pavements are widened or narrowed to meet the building fronts. Only what the planner's map reaches is brought in (about 380 blocks each way); move along and import again for more.
- **Buildings** show as orange outlines on the planner map, and in the world (at their real size and height from their number of floors) while you hold the planner, to build over. **Guides in world** turns that off. If the buildings sit a little off the roads in the map data, **Nudge** them into line.
- **Measure:** in Map mode click two points to see the distance in blocks and in real metres, to check against Google Maps.

Map data © OpenStreetMap contributors (ODbL). The import never goes online: it only reads the file you saved.

### Site planners: car parks, retail parks, drive-thrus, shop parades and whole towns

The Road Planner's **Sites** mode (the Sites button, or Q) lays out whole sites. Drag out a rectangle on the map, pick what it is in the panel, and it's laid out to fit, shown on the map with everything else. Drag a site to move it or a corner to resize it. End a road on a site's edge and that becomes its way in (any number of them); the side the widest road reaches is the road side, or set it yourself. **Build** builds the sites with the roads, and **Undo build** takes them away again.

- **Car park:** UK-sized bays (3 wide, 5 deep) in rows with 6-block aisles, cross aisles joining the rows, kerbed islands with trees and lamp posts at the row ends, a planted verge with trees round the edge, a blue P sign at each way in and pay and display machines. Wider bays nearest the way in for **disabled drivers**, **parents with children** and **electric cars** (with a charger at each), each with its symbol painted on. Choose how many of each, whether the bays face the road or run along it (or let it fit the most), trees (none, at the row ends, or lots) and lights.
- **Retail park:** a terrace of big shop units across the back (as many as fit, or choose 1 to 8, and how deep), glazed fronts with double doors, a white fascia with each shop's lit sign, clad sides and a flat roof, and inside checkouts and fittings for what each one sells (clothes, sports, beauty, homeware, pets, electricals, DIY or food). A canopy on tall steel masts runs along the shop fronts over a paved walkway with bollards, bins, benches and trolleys, and zebra crossings with tactile paving lead to each door from the car park in front. The shop names are made up (TRAILHEAD, GLOW, FASHION FIX, NEST HOME, SPRINT, PET PLANET, SPARKS, FIX & BUILD, FRESHWAYS, COMFY SOFAS) and you can rename the signs in game.
- **Shop parade:** fills the frontage with a row of the terraced high street units (chippy, pharmacy, coffee shop, newsagent, bakery, bookmakers, barber's and so on, each with its fit-out and a flat or office over it), side by side and facing the road, as many as fit (it needs about 22 deep). **Shuffle shops** changes the mix. Good for filling a gap in a high street, or rebuilding an old one.
- **Rows of houses and the like:** **Terraced houses** (Victorian and Georgian), **Semi-detached houses** (1930s and Edwardian), **Detached houses** (new-build, 1930s and bungalows) and **Church, pub and surgery** (with a library and a village hall) fill the frontage the same way, each with a garden round it. **Shuffle** changes the mix.
- **Town:** drag out a big area (at least 120 x 80; 400 x 260 makes a good small town) and choose what it has: a **main road** through it or just residential streets, a **high street** of shops in the middle, which **houses** (Victorian terraces, 1930s semis, detached, or a mix with terraces near the middle and detached at the edge) and a **church, pub, surgery** and so on at the ends of the high street. **Streets: winding** (the default) gives an old British town: a high street that wanders the long way through it, side roads off it at staggered angles, some crossing it as crossroads, back streets off those that bend, join up into crescents or stop as closes, and a few running out of town (ready to carry on), with the houses stepping round the bends. **Streets: grid** gives the tidier plan: the main road the long way, side streets either side and cross streets about every 120 blocks. **Kerbs: no** leaves the kerbs off all its roads (the yellow lines follow the edge of the road). The crossroads in the middle of the high street gets **traffic lights**. A **big town** (a main road 600 blocks or more) gets lights at all its high street crossroads and where its busiest side roads join, spaced well apart, **mini roundabouts** where back streets cross, its long roads in from out of town as proper main roads, and more back streets off back streets, so it fills right out to its edges. **Lay out the town** turns it into ordinary roads and rows of shops or houses along them; the rest of the town becomes a **green space**, so the ground between and behind the buildings is grass, level with them, with trees (oak, lime, plane, birch and cherry) wherever it opens out. Every street gets a made-up British name (the main road is High Street), and the names go on the map when it's built. Change anything you like before you **Build**, or **Undo** to try another layout.
- **Keep clear:** drag one out over a town before you lay it out (a park, a lake, a building you want to keep) and the town leaves it alone: no streets, houses or shops on it, and the builder doesn't touch the ground there. To leave out a single row afterwards, select it and **Delete site**; to add houses somewhere, drag out a Terraced, Semi-detached or Detached site there.
- **Green space:** grass, level with the roads and buildings round it, filling whatever they leave.
- **Drive-thru:** a brick restaurant with a glass front and its sign, a one-way lane round it (clockwise, so the order point and the pay and collect windows are on the driver's side) with arrows painted on, the menu board and order point on the island by the way in, picnic tables beside it, and a car park in front. Inside: tables, the counter with tills, menu boards and the kitchen with fryers. Pick from four made-up names (BUN & GONE, BEAN THERE, CLUCK SHACK, DOUGH TO GO).

A site that's too small for a retail park or drive-thru is laid out as a car park and the planner says so. Sites sit level at the height of the ground under them (or raise and lower them), and roads that reach them meet them level. The bay markings (disabled, parent and child, EV) are also blocks of their own in the street furniture tab.

### Home furniture

In the **UK Home Furniture** tab, all drawn for this mod:

- **Living room:** sofas in grey, navy, green, brown leather and cream. Put seats side by side and they join into one long sofa, with arms only at the ends; a single seat is an armchair. Also a coffee table, a TV unit, a flat screen TV, a floor lamp, a table lamp, a ceiling pendant light, a bookcase, an electric fire with a marble mantel, a radiator, a house plant, a dining table (tables side by side join into one long table) and dining chairs.
- **Kitchen** (white or oak): a cupboard, drawers, a sink (a real stainless bowl let into the worktop, a drainer and a swan-neck mixer tap), an oven and hob (steel control strip with knobs and a clock, a black glass door with a bar handle, a ceramic hob), and a wall cupboard, all with 3D handles. Also a fridge freezer, a washing machine, a microwave, a kettle and a toaster.
- **Bathroom:** a close-coupled toilet, a pedestal basin with a proper bowl and mixer tap, a fitted bath (two blocks long) with a real tub, deck mixer and sloped back, a walk-in shower, a heated towel rail and a mirror cabinet.
- **Bedroom and study** (oak or white): a wardrobe, a chest of drawers and a bedside table. Also a desk, an office chair and a computer.
- **On the walls:** framed pictures (rolling hills, the seaside, flowers in a vase and colour blocks, all made up), a wall clock, a wall mirror, coat hooks with coats on, and curtains (cream, red, blue or green) to hang beside a window.

What they do:
- **Sit:** right-click a sofa, chair, toilet or bath to sit down, and sneak to get up.
- **Store things:** cupboards, drawers, the wardrobe, the fridge, the bedside table, the desk, the TV unit and the cabinet hold items like a chest (the wardrobe holds as much as a double chest).
- **Cook:** the oven and the microwave cook food like a smoker, on mains power: put food in and they switch themselves on, no fuel needed. The hob rings glow red and the oven window lights up while it's cooking.
- **Switch on:** right-click the TV (it shows made-up programmes), lamps, the fire, the computer, the washing machine, the kettle, the toaster and the shower to switch them on. Lamps and the fire light the room. The kettle, toaster, washing machine and shower switch themselves off after a while.
- **Taps:** the sink and basin run water, and fill an empty bucket or glass bottle. Click the sink's worktop for the tap and its doors for the cupboard. Sneak and right-click the bath to run it full (sneak and right-click again to pull the plug), or the toilet to flush it.
- **Tabletop things** (TV, lamps, kettle, toaster, microwave, computer, plant) sit down onto whatever they're placed on: a TV on its unit, a kettle on the worktop, a lamp on a bedside table.

The prefab houses, flats, shops and offices are furnished with these. Every home in them (houses, flats, and the flats over shops, pubs and takeaways) is dressed room by room: what's in a room says what it is, and it gets what such a room is missing along its free walls: bedside tables and a lamp, a chest of drawers, a basin, mirror cabinet and towel rail by the bath, a washing machine, kettle, toaster and microwave in the kitchen, a coffee table, lamp, plant and pictures in the living room, a radiator under the window, coat hooks by the front door, a clock, curtains beside the windows and a light in the ceiling, never in front of a door, a piece of furniture or the stairs. Floors that used to be left empty are fitted out first: the storeys of the tower blocks, slab block, mansion flats and apartments are divided by partition walls (each with a doorway) into flats with a kitchen, a sofa and TV, a bed and a bathroom; the flats over the high street parade, the cafe pair and the corner pub have their rooms; the pound shop has offices upstairs, and the office blocks have desks with computers.

### Shop and commercial fittings

In the **UK Shop & Commercial Fittings** tab, for fitting out shops, cinemas, garages and restaurants anywhere:

- **Supermarket:** double-sided shelving two blocks high (groceries, drinks, household, snacks, DIY, games, pharmacy, newsagent), a chiller cabinet, an upright freezer, a chest freezer, a fruit and veg stand, a two-block checkout with belt, scanner, till, card reader and lane light, a self-checkout, a shopping trolley, a till, a cash machine (set it into a wall), pallet racking, a clothes rail and a garden centre plant bench. Shelves, chillers, freezers and racking store items.
- **Cinema:** red and blue cinema seats (sit in them), a popcorn machine (switch it on), pick and mix, a drinks dispenser, a ticket machine, a projector and lit poster cases for five made-up films.
- **Garage and fast food:** a car lift post, a tool chest (stores items), a stack of tyres, an oil drum, a deep fat fryer (switch it on to lower the baskets), a two-block lit menu board and a drive-thru order point.
- **Pubs, bars and clubs:** beer pumps, back bar shelves of optics and bottles, bar stools (sit on them), a pool table (two blocks), a dartboard, a fruit machine (switch it on and the reels spin), a bottle fridge, DJ decks and a light-up dance floor.
- **Cafes and takeaways:** a coffee machine, a cake display counter, a pizza oven (cooks like the home oven) and a doner kebab grill.
- **Barber's and gym:** a barber's chair, a treadmill and a weight bench.
- More shelving stock: games, pharmacy and newsagent.

All the packaging, films and menus are made up; no real brands.

### Map, road names and directions

Press **M** for the map, a bit like the one on a phone. It fills itself in from everywhere you've been (and keeps up as you build), saved on your computer per world. Drag to move, scroll to zoom.

- **Minimap** in the top right corner: the way you're facing is up, with the name of the road you're on underneath. Turn it off or on from the map.
- **Pins:** right-click the map and **Drop a pin here**, and name it.
- **Places:** shops (from their shop signs, named as the sign says) and petrol stations (from their price signs). From PTM2: its stations and bus stops, car parks (with how full they are), its districts as area names, congestion zones in red, and the buses (red, with their route number when you zoom in) and cars on the move.
- **Road names:** right-click a road and **Name this road** (creative mode). A **Street Nameplate** (street furniture tab) does it in survival too: put it on a wall, or on the ground for one on two legs, write the street name and, if you like, the district and postcode under it (red, London style, or black), and it names the road beside it. Put one down by a named road and it takes that road's name; by an unnamed one it picks a British street name (Station Road, Church Lane, Victoria Avenue and so on) and names the road with it, and you can change it whenever you like. Roads built in the Road Planner from a real map keep their OpenStreetMap names.
- **Search** for a shop, stop, road, area or pin, then **Directions**. Or right-click anywhere and **Directions to here**.
- **Directions:** a blue route along the roads (tarmac and PTM2's roads), on the map and the minimap, and the next turn across the top of the screen: "In 40 blocks, turn right onto Broadway". Go off the route and it works out a new one; get there and it says you've arrived. With no road there, it points the way as the crow flies. Routes are worked out by the server a slice at a time, reading roads straight from the save, so they don't stall the game.

### Town Builder

The **Town Builder** (UK Buildings tab, creative mode) puts down trees, bushes, hedges and street furniture. Sneak + right-click (or right-click the sky) to choose; right-click to put it down, from up to 128 blocks away. **Undo last** in the same screen takes back your last ten goes.

- **Trees:** London plane, English oak, lime, silver birch, cherry blossom, Scots pine, holly and leylandii, in four sizes (young, semi-mature, mature, veteran) at the same scale as the buildings and roads, so a mature plane is about 28 blocks tall and a veteran about 40. They're built from ordinary logs and leaves (the leaves don't decay). Right-click the ground to plant one; right-click a tree you planted to grow it a size bigger. **Scatter a few** plants a handful round the spot, on grass and earth, some smaller.
- **Bushes and hedges:** box balls, flowering shrubs and laurel bushes (plant or scatter, as trees); privet, beech and conifer hedges from low to a tall screen: right-click where the hedge starts, then where it ends.
- **Street furniture:** wooden and metal benches, litter and dog waste bins, cast iron and steel bollards, cycle stands, brick planters, flower troughs, street lights (LED or the old orange ones), heritage lamp posts with one or three lanterns, black guard rail, pillar boxes and grit bins. Right-click where the row starts, then where it ends (the same spot for just one): it puts them along the line, evenly spaced (set how far apart), each facing the road (the side with tarmac, or else the side a kerb lower). Guard rail runs the whole way.

### Interior Designer

Fit out an empty room in one click. Take the **Interior Designer** (UK Buildings tab, creative mode), right-click the floor of a room, and pick what the room is:

- **Shops:** convenience store, newsagent, pharmacy, game shop (shelving round the walls, back-to-back aisles across the middle, chillers and freezers along the back of a convenience store, and a counter with a till by the door, with room behind it for the staff) and clothes shop (rails round the walls and across the floor, a till counter at the back).
- **Eating and drinking:** cafe (a back counter with a coffee machine, cake displays and a till, and tables for two), restaurant (tables for four), takeaway (fryers and a pizza oven at the back, a serving counter in front of them, stools for waiting) and pub or bar (a back bar, a bar with beer pumps and stools, fruit machines and tables).
- **Work:** office (rows of desks with computers and chairs, bookcases, plants).
- **Home:** living room (a sofa, coffee table, TV facing it, floor lamp, bookcases, radiators under the windows), bedroom (a bed with bedside tables, wardrobes and drawers), kitchen (fitted units round the walls with a sink under the window, oven, washing machine, fridge freezer, kettle, microwave and toaster, and a table) and bathroom (a bath, toilet, basin and towel rail).

The room is everything you can walk to from where you clicked, indoors, up to its walls and doors (up to 40 x 40). An open doorway onto the street ends it too, and the door to the street counts as the front. Doorways are kept clear, tall things stay out of the windows, and furniture already in the room is cleared out first, so you can try another kind of room. Right-click the air and press **Undo last room** to put the room back as it was (the last five rooms).

### Building blocks

In the **UK Buildings** tab, each material as a full block, a slab and stairs:

- **Paving:** concrete paving slabs, red and grey block paving, buff and red blister tactile paving, corduroy tactile paving, granite setts, concrete kerb.
- **Tarmac:** standard and red (bus and cycle lanes).
- **Walls:** red brick, London stock brick, blue engineering brick, pebbledash, white render, Portland stone, green and burgundy glazed faience tiles (pub and shop fronts).
- **Roofs:** Welsh slate, clay tiles, concrete tiles, thatch.
- **Windows** that join up like glass panes: white UPVC, sash, Georgian, leaded lights, shop window, office glazing, grey-framed.
- **Garage doors:** up-and-over doors in white steel, oak boards or painted green. Fill an opening with panels (placed from outside so they all face out); right-click any panel, or give one a redstone signal, and the whole door swings up flat under the lintel, leaving the opening clear to drive a car in. Every prefab garage uses them.
- **Shop Sign:** a fascia board for over a shop front. Boards placed side by side make one sign, with the name centred across them. Right-click to set the name, a second line, the board and letter colours and whether it's lit at night.

### Petrol station

In the **UK Street Furniture** tab:

- **Fuel Price Sign:** the tall price totem by the forecourt entrance. Right-click it to change:
  - the name at the top and its logo
  - the sign colour, the name colour, and the colour of the price digits (amber, red, green, white or blue)
  - four fuels, each with its name, a coloured plate and a price on LED digits
  - a line of extras at the bottom (shop, car wash and so on); put a `|` where it should split onto two lines
  - whether it lights up at night

  It shows the same thing on both sides.
- **Your own logos:** the sign comes with eight made-up logos. To use your own design (a real brand you want in your world, for example), save it as a PNG file in `.minecraft/ptmuk/images/`, then pick it with the sign's **Logo** button; the **Images folder** button opens the folder for you. Pictures can be up to 2048 pixels on each side and 8 MB, and transparent backgrounds work. The sign only saves the file's name, so on a server everyone who wants to see the logo needs the same file in their own images folder; anyone without it just sees the brand name.
- **Automatic Car Wash:** place it on the floor in the middle of the wash bay, facing you; the bay needs about 5 by 9 blocks of space. It starts by itself when a PTM2 vehicle, a minecart, a boat or a player comes into the bay, or when you right-click it or give it a redstone pulse. The wash takes about 22 seconds: a gantry with spinning brushes runs over the car spraying water, comes back drying it, then the board tells you to drive out. The board on the gantry shows what it's doing (DRIVE IN, STOP - ENGINE OFF, WASHING, RINSING, DRYING, PLEASE EXIT).
- **Fuel Pump** with a display and nozzles on both sides, and an **Air & Water Machine**.

The **petrol station** prefab (Prefab Tool, **Other** tab) has all of these set up together.

### Road signs

In the **UK Road Signs** tab, mounted on poles with clamps like the signal heads:

- **Speed limits:** 20 to 70, and national speed limit.
- **Regulatory:** no entry, give way, stop, one way, keep left, mini roundabout.
- **Roundabout chevron board.**
- **Speed camera** sign.
- **Parking:** parking, pay at machine, disabled parking.
- **Warnings:** traffic signals ahead, pedestrian crossing, children, roundabout ahead, road works.

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

**Overhead Line Tool:** right-click a tower, then the next one, to string the conductors between them. Spans can be up to 256 blocks. Each conductor connects to its partner on the other tower, with a realistic sag; 400 kV lines are drawn as twin bundles, plus the earth wire. Sneak + right-click a tower to take down its spans. The wires are drawn by the client, so LOD mods (Distant Horizons, Voxy) don't show them beyond your normal render distance; the towers are real blocks, so those do. Wires, sign boards and LED displays draw the same way as chests and banners, so they work with Oculus shader packs (checked with BSL).

### Buses and cars

In the **UK Buses & Cars** tab. These are PTM2 vehicles, so they drive, take routes and carry passengers like PTM2's own:

- **Alexander ALX400** and **Alexander Dennis Enviro400** (London spec): dual door, red, with a Stagecoach-style interior.
- **Scania OmniCity** (London spec): dual door, red, 10.8 m long, with the OmniCity's flat front, round lamp clusters and upper deck engine louvre.
- **UK Double Decker**: the original stand-alone design.
- **Len Livery** versions of the ALX400 and Enviro400: red at the front fading to white at the back, with yellow and black stripes and music notes. To paint your own artwork on the white part, override `assets/ptmuk/textures/entity/bus/alx400_len.png` or `e400_len.png` in a resource pack. Also override the matching `_left.png` file, which is used with left-hand traffic on.
- **Kia K4 GT-Line S** (red), as a car.

With PTM2's left-hand traffic setting on, the buses have their doors on the left, as in the UK. All of them have:

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
./gradlew runClient -PwithShaders   # with Oculus + Embeddium (put a shader pack in run/shaderpacks)
```

### Models and textures

The signal models, blockstates and textures are generated by `tools/generate_assets.py` (needs Python 3, Pillow and numpy). The HD textures are painted procedurally by `tools/signal_textures.py`: 128 px lenses, faceplates, housings and 256 px boards. Hoods are rounded tubes built from rotated plates. Each head is split into parts: housing, board, and each lens on, off or flashing. `client/SignalModels.java` bakes every part once and combines them per block state, which keeps memory use low. Edit the script and re-run it rather than editing the generated JSON by hand:

```sh
pip install pillow numpy
python3 tools/generate_assets.py
```

The prefab buildings are designed in Python too: `tools/prefab_designs.py` (built from the parts in `tools/prefab_parts.py`) writes them as structure files in `data/ptmuk/structures/prefab/`, draws the catalogue pictures and generates `PrefabCatalog.java`. They are regenerated along with everything else by `generate_assets.py`.

## Roadmap ideas

- Speed cameras (Gatso, Truvelo, SPECS average-speed, HADECS) that detect PTM2 vehicles
- Belisha beacons and zebra crossings
