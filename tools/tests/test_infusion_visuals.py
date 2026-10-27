import json,math,unittest
from tools.forging import infusion
from tools.forging.models import Catalogue
from tools.equipment import mesh
class InfusionVisualsTest(unittest.TestCase):
 def test_each_item_is_centered_fits_the_effect_and_clears_the_ding_ears(self):
  output=infusion.outputs();cfg=json.loads(output[infusion.ROOT/'src/main/resources/data/myvillage/forging/infusion_visuals.json']);catalogue=Catalogue()
  for rid,faces in catalogue.sources.items():
   pose=cfg['items'][rid];angle=math.radians(pose['rotate_z']);transformed=[]
   for face in faces:
    for point in face['points']:
     x,y,z=[(v/16-pose['center'][i])*pose['scale'] for i,v in enumerate(point)]
     transformed.append((x*math.cos(angle)-y*math.sin(angle),x*math.sin(angle)+y*math.cos(angle),z))
   for axis in range(3):self.assertAlmostEqual(min(v[axis] for v in transformed),-max(v[axis] for v in transformed),places=6)
   self.assertLessEqual(max(math.hypot(v[0],v[2]) for v in transformed),.75,rid)
   self.assertGreater(cfg['item_height']+min(v[1] for v in transformed),17.3/16,rid)
 def test_workshop_fallback_does_not_share_a_mutable_freetype_face_across_fonts(self):
  from tools.forging import workshop
  data=json.loads(workshop.outputs()[infusion.ROOT/'src/main/resources/assets/myvillage/font/forge_detail.json'])
  self.assertEqual('回',data['providers'][0]['skip'])
  self.assertTrue(any(p.get('file')=='myvillage:panel_body.ttf' for p in data['providers']))
  self.assertFalse(any(p.get('type')=='reference' and p['id'].startswith('myvillage:') for p in data['providers']))
 def test_generated_resources_match(self):
  for path,content in infusion.outputs().items():self.assertEqual(content,path.read_bytes(),str(path))
if __name__=='__main__':unittest.main()
