"""Create current item definitions; copy original Minecraft sprites without modifying pixels."""
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/sexmod'
EGGS = {'jenny': (3286592, 12655237), 'ellie': (0x161616, 0x980000),
        'slime': (13167780, 8244330), 'bia': (7488816, 7254603),
        'bee': (16701032, 4400155), 'luna': (7881787, 7940422),
        'goblin': (39424, 19456), 'galath': (0xff0000, 0xff0000), 'manglelie': (0xf9f9f9, 8485574)}

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

with ZipFile(ROOT / '.tooling/downloads/minecraft-1.12.2-client.jar') as archive:
    for sprite in ('spawn_egg', 'spawn_egg_overlay'):
        dest = ASSETS / f'textures/item/{sprite}.png'
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(archive.read(f'assets/minecraft/textures/items/{sprite}.png'))
with ZipFile(ROOT / '.gradle-user-home/caches/fabric-loom/26.3/minecraft-client.jar') as archive:
    dest = ASSETS / 'textures/mob_effect/horny_potion.png'
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(archive.read('assets/minecraft/textures/mob_effect/speed.png'))
    # Vanilla's complete fishing-rod condition model also handles casts by players.
    write(ASSETS / 'items/luna_rod.json', json.loads(archive.read('assets/minecraft/items/fishing_rod.json')))

write(ASSETS / 'models/item/legacy_spawn_egg.json', {'parent':'minecraft:item/generated',
      'textures':{'layer0':'sexmod:item/spawn_egg', 'layer1':'sexmod:item/spawn_egg_overlay'}})
for name, colors in EGGS.items():
    write(ASSETS / f'items/{name}_spawn_egg.json', {'model': {'type':'minecraft:model',
          'model':'sexmod:item/legacy_spawn_egg', 'tints':[{'type':'minecraft:constant','value':color} for color in colors]}})
for name in ('tribe_egg', 'npc_editor_wand'):
    write(ASSETS / f'items/{name}.json', {'model':{'type':'minecraft:model','model':f'sexmod:item/{name}'}})
english = json.loads((ASSETS / 'lang/en_us.json').read_text(encoding='utf-8'))
for name in EGGS:
    english[f'item.sexmod.{name}_spawn_egg'] = english.get(f'entity.sexmod.{name}', name.title()) + ' Spawn Egg'
english['effect.sexmod.horny_potion'] = english['effect.horny_potion']
write(ASSETS / 'lang/en_us.json', english)
for container in ('potion', 'splash_potion', 'lingering_potion'):
    write(RES / f'data/sexmod/recipe/brewing/{container}_horny_potion.json', {
        'type':'minecraft:brewing',
        'input':{'item':f'minecraft:{container}', 'potion_contents':{'potions':'minecraft:mundane'}},
        'output':{'id':f'minecraft:{container}', 'components':{'minecraft:potion_contents':{'potion':'sexmod:horny_potion'}}},
        'reagent':{'item':'minecraft:poppy'}})
for before, after, reagent in [('potion','splash_potion','gunpowder'), ('splash_potion','lingering_potion','dragon_breath')]:
    write(RES / f'data/sexmod/recipe/brewing/{before}_horny_potion_{reagent}.json', {
        'type':'minecraft:brewing',
        'input':{'item':f'minecraft:{before}', 'potion_contents':{'potions':'sexmod:horny_potion'}},
        'output':{'id':f'minecraft:{after}', 'components':{'minecraft:potion_contents':{'potion':'sexmod:horny_potion'}}},
        'reagent':{'item':f'minecraft:{reagent}'}})
print('Created 12 item definitions, 5 brewing recipes, and original spawn-egg sprites.')
