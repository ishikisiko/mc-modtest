#!/usr/bin/env python3
"""Exercise the local prototype through browser controls, without an HTTP server."""
import argparse
import json
from pathlib import Path

from playwright.sync_api import sync_playwright


def check(html: Path, out: Path, executable: str) -> None:
    out.mkdir(parents=True, exist_ok=True)
    selected = ['responsive_tabs', 'beginner_journey', 'reload_save', 'crafting',
                'profession_and_commission', 'master_crafting', 'salvage_confirmation',
                'repair_and_trade', 'station_upgrade', 'reset_cancel']
    report = {'selected': selected, 'completed': [], 'failed': [], 'unrun': [], 'console_errors': []}
    active = None
    with sync_playwright() as pw:
        browser = pw.chromium.launch(executable_path=executable, headless=True,
                                     args=['--no-sandbox', '--disable-dev-shm-usage'])
        context = browser.new_context(viewport={'width': 1280, 'height': 1100}, device_scale_factor=1)
        page = context.new_page()
        page.on('pageerror', lambda error: report['console_errors'].append(str(error)))
        page.on('console', lambda message: report['console_errors'].append(message.text) if message.type == 'error' else None)

        def state():
            return page.evaluate('window.ForgeDemo.state()')

        def tab(name):
            page.get_by_role('tab', name=name, exact=True).click()

        def reset(preset='beginner'):
            page.locator('#preset').select_option(preset)
            # Selecting the current value does not emit a change in all browsers.
            if not page.locator('#modal').evaluate('(d) => d.open'):
                page.get_by_role('button', name='重置', exact=True).click()
            page.get_by_role('button', name='确认重新开始', exact=True).click()

        def place(name):
            page.locator('article.place-card').filter(has=page.get_by_role('heading', name=name, exact=True)).get_by_role('button').click()

        def learn(name):
            page.locator('.scroll-card').filter(has=page.locator('strong', has_text=name)).get_by_role('button', name='研读器谱').click()

        def no_overflow():
            dims = page.evaluate('({width: innerWidth, scroll: document.documentElement.scrollWidth})')
            assert dims['scroll'] <= dims['width'], dims

        try:
            page.goto(html.as_uri(), wait_until='load')
            page.wait_for_function('!!window.ForgeDemo')
            active = 'responsive_tabs'
            for width in [1280, 728, 390]:
                page.set_viewport_size({'width': width, 'height': 1100})
                for name in ['装备成长', '打造器物', '外出探索', '炼器修习', '系统规则']:
                    tab(name)
                    no_overflow()
                tab('装备成长')
                page.screenshot(path=str(out / f'workshop-{width}.png'), full_page=True)
                page.get_by_role('button', name='材料与交易', exact=True).click()
                no_overflow()
                page.get_by_role('button', name='关闭', exact=True).click()
            report['completed'].append(active)
            page.set_viewport_size({'width': 1280, 'height': 1100})

            active = 'beginner_journey'
            page.locator('#perform-operation').click()
            assert state()['items'][0]['refine'] == 1
            tab('外出探索'); place('山麓矿坑')
            page.get_by_role('button', name='继续探索', exact=True).click()
            place('山中古库')
            assert len(state()['items']) == 3
            page.get_by_role('button', name='前往研读器谱', exact=True).click()
            learn('霜纹器谱')
            assert 'frost' in state()['known']
            tab('装备成长')
            page.get_by_role('button', name='器性', exact=True).click()
            page.locator('#reshape-pattern').select_option('frost')
            page.locator('#perform-operation').click()
            assert state()['items'][0]['traits'] == ['frost']
            page.get_by_role('button', name='升阶', exact=True).click()
            page.locator('#perform-operation').click()
            assert state()['items'][0]['tier'] == 2
            assert state()['items'][0]['traits'] == ['frost']
            page.screenshot(path=str(out / 'completed-journey.png'), full_page=True)
            report['completed'].append(active)

            active = 'reload_save'
            before = state()
            page.reload(wait_until='load')
            assert state() == before
            report['completed'].append(active)

            active = 'crafting'
            tab('打造器物')
            page.locator('#craft-kind').select_option('focus')
            page.locator('#craft-tier').select_option('1')
            page.locator('#craft-pattern').select_option('plain')
            count = len(state()['items'])
            page.locator('#perform-craft').click()
            assert len(state()['items']) == count + 1
            assert state()['items'][-1]['kind'] == 'focus'
            report['completed'].append(active)

            active = 'profession_and_commission'
            reset()
            tab('外出探索'); place('山中古库')
            page.get_by_role('button', name='前往研读器谱', exact=True).click(); learn('霜纹器谱')
            assert state()['xp'] == 230
            tab('装备成长'); page.get_by_role('button', name='器性', exact=True).click()
            page.locator('#reshape-pattern').select_option('frost')
            assert page.locator('#perform-operation').is_disabled()
            page.locator('#commission').check()
            assert page.locator('#perform-operation').is_enabled()
            page.locator('#perform-operation').click()
            assert state()['xp'] == 230
            assert state()['items'][0]['traits'] == ['frost']
            report['completed'].append(active)

            active = 'master_crafting'
            reset('master'); tab('打造器物')
            page.locator('#craft-kind').select_option('focus')
            page.locator('#craft-tier').select_option('5')
            page.locator('#craft-pattern').select_option('echo')
            page.locator('#perform-craft').click()
            assert state()['items'][-1]['tier'] == 5
            assert state()['items'][-1]['traits'] == ['echo']
            page.screenshot(path=str(out / 'legendary-focus.png'), full_page=True)
            report['completed'].append(active)

            active = 'salvage_confirmation'
            before = state()
            page.get_by_role('button', name='分解回收', exact=True).click()
            page.get_by_role('button', name='取消', exact=True).click()
            assert state() == before
            page.get_by_role('button', name='分解回收', exact=True).click()
            page.get_by_role('button', name='确认分解', exact=True).click()
            assert len(state()['items']) == len(before['items']) - 1
            report['completed'].append(active)

            active = 'repair_and_trade'
            tab('外出探索'); place('古修洞府')
            page.get_by_role('button', name='继续探索', exact=True).click()
            tab('装备成长')
            assert state()['items'][0]['condition'] < 100
            page.get_by_role('button', name='修补', exact=True).click()
            page.locator('#perform-operation').click()
            assert state()['items'][0]['condition'] == 100
            page.get_by_role('button', name='材料与交易', exact=True).click()
            before = state()
            page.get_by_role('button', name='买入 5 份 · 15 灵石', exact=True).click()
            assert state()['inventory']['iron'] == before['inventory']['iron'] + 5
            assert state()['coin'] == before['coin'] - 15
            page.get_by_role('button', name='关闭', exact=True).click()
            report['completed'].append(active)

            active = 'station_upgrade'
            reset('artisan'); tab('炼器修习')
            page.get_by_role('button', name='升级工位', exact=True).click()
            assert state()['station'] == 4
            report['completed'].append(active)

            active = 'reset_cancel'
            before = state(); page.locator('#preset').select_option('beginner')
            page.get_by_role('button', name='保留当前进度', exact=True).click()
            assert state() == before
            assert page.locator('#preset').input_value() == 'artisan'
            report['completed'].append(active)
            assert not report['console_errors'], report['console_errors']
        except Exception as exc:
            report['failed'].append({'case': active, 'error': str(exc)})
            page.screenshot(path=str(out / 'failure.png'), full_page=True)
            raise
        finally:
            report['unrun'] = [x for x in selected if x not in report['completed'] and x != active]
            report['status'] = 'passed' if len(report['completed']) == len(selected) and not report['failed'] and not report['console_errors'] else 'failed'
            (out / 'browser-results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
            context.close(); browser.close()
            print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--html', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--browser', default='/home/ubuntu/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome')
    args = parser.parse_args()
    check(args.html.resolve(), args.out.resolve(), args.browser)
