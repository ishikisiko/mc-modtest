"""检查预览灰瓦网格的表面闭合和朝向，避免简化后产生屋面缺口。"""
import json,math,unittest
from pathlib import Path
from tools.complex_houses.preview_models import boundary_rectangles,ComplexModels,ROOT

class PreviewModelsTest(unittest.TestCase):
    def test_cube_has_six_complete_faces(self):
        occupied={(x,y,z)for x in range(16)for y in range(16)for z in range(16)}
        faces=boundary_rectangles(occupied)
        self.assertEqual(len(faces),6)
        self.assertEqual(sum(math.prod(hi[i]-lo[i]for i in range(3)if i!=axis)for axis,sign,lo,hi in faces),1536)

    def test_disjoint_voxels_keep_both_surfaces(self):
        self.assertEqual(len(boundary_rectangles({(0,0,0),(2,0,0)})),12)

    def test_roof_surface_area_matches_exposed_voxels_in_every_facing(self):
        models=ComplexModels(Path('/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'))
        catalog=json.loads((ROOT/'src/main/resources/data/yuanxian/courtyard/assemblies.json').read_text())
        variants=next(f for f in catalog['families']if f['id']=='courtyard_roof')['variants']
        for index in range(8):
            occupied=set()
            for a,b,c,d,e,f in variants[index]['parts'][0]['boxes']:occupied.update((x,y,z)for x in range(a,d)for y in range(b,e)for z in range(c,f))
            expected=sum((x+dx,y+dy,z+dz)not in occupied for x,y,z in occupied for dx,dy,dz in((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)))
            for facing in('north','east','south','west'):
                faces=models.roof_surface(index,facing);area=0
                for points,normal,uv,texture,tint,cull,transparent in faces:
                    self.assertGreater(max(p[0]for p in uv)-min(p[0]for p in uv),0)
                    self.assertGreater(max(p[1]for p in uv)-min(p[1]for p in uv),0)
                    u=[points[1][i]-points[0][i]for i in range(3)];v=[points[3][i]-points[0][i]for i in range(3)]
                    cross=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
                    area+=math.sqrt(sum(c*c for c in cross))
                    self.assertTrue(all(-1e-7<=n<=16+1e-7 for p in points for n in p))
                self.assertAlmostEqual(area,expected,places=6,msg=f'{index} {facing}')
        models.jar.close()

if __name__=='__main__':unittest.main()
