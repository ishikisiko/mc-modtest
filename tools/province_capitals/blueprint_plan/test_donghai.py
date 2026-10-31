"""东海正式重排：海侧容量、公共双埠及不填海的海床。"""
from .test_formal_support import FormalCityCase
from .manor_relayout import half_cells


class DonghaiPlanTests(FormalCityCase):
    region='donghai'

    def test_seabed_is_kept_outside_the_explicit_new_tidal_channel(self):
        p=self.planner;canal=set().union(*(half_cells(b) for b in p.layout['water_channels']))
        changed=[]
        water=p.city['terrain']['sea_y']
        for z in range(p.depth):
            for x in range(p.width):
                old=p.original_terrain[z*p.width+x]
                if old<water and (x,z) not in canal and p.ground(x,z)!=old:changed.append([x,z,old,p.ground(x,z)])
        self.assertEqual([],changed)
        self.assertEqual(256,p.width)
        piers=[r for r in p.layout['roads'] if r['name'] in ('北埠','南埠')]
        self.assertEqual([56,51],[abs(r['points'][-1][0]-r['points'][0][0]) for r in piers])
