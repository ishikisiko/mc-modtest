"""Temporary baseline probe; replaced by feature acceptance before delivery."""
import pathlib
import subprocess
import sys
import zipfile

out = pathlib.Path('reports/worldplan')
out.mkdir(parents=True, exist_ok=True)
with (out/'baseline-compile.log').open('w') as log:
    result = subprocess.run(['./gradlew','compileJava','-x','generateAllStructures','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx3g'], stdout=log, stderr=subprocess.STDOUT)
api = out/'api'
api.mkdir(exist_ok=True)
classes = ['net.minecraft.world.level.chunk.ChunkGenerator','net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator','net.minecraft.world.level.biome.BiomeSource','net.minecraft.world.level.levelgen.RandomState','net.minecraft.world.level.chunk.ChunkGeneratorStructureState','net.minecraft.world.level.levelgen.structure.Structure','net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate','net.minecraft.server.level.WorldGenRegion','net.minecraft.world.level.chunk.ChunkAccess','net.minecraft.world.level.NoiseColumn']
for p in pathlib.Path.home().joinpath('.gradle').rglob('*.jar'):
    try:
        with zipfile.ZipFile(p) as z:
            if classes[0].replace('.','/')+'.class' not in z.namelist(): continue
            for cls in classes:
                with (api/(cls.split('.')[-1]+'.javap.txt')).open('w') as f:
                    subprocess.run(['javap','-p','-c','-classpath',str(p),cls], stdout=f, stderr=subprocess.STDOUT)
            break
    except zipfile.BadZipFile:
        continue
sys.exit(result.returncode)
