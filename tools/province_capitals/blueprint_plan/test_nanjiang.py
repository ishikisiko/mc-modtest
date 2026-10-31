"""南疆正式重排：干栏、公共双岸及偏轴府门。"""
from .test_formal_support import FormalCityCase


class NanjiangBlueprintTests(FormalCityCase):
    region='nanjiang'

    def test_raised_longhouses_are_kept_and_front_gate_uses_true_axis(self):
        p=self.planner
        original={b['id'] for b in p.source_city['buildings'] if b['family']=='stilt_longhouse'}
        self.assertTrue(original);self.assertTrue(original<=p.templates.keys())
        self.assertEqual(38,p.layout['manor']['entry_axis_source_x'])
        self.assertEqual(100,p.manor_entry[0])
        crossings={r['name']:r for r in p.layout['roads'] if r['name'] in ('北桥','中桥','南桥')}
        self.assertEqual({'北桥','中桥','南桥'},set(crossings))
        for name,z,width in (('北桥',61,5),('中桥',144,7),('南桥',228,5)):
            self.assertEqual(width,crossings[name]['width'])
            self.assertTrue(all(point[1]==z for point in crossings[name]['points']))
