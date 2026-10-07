"""The preview pages: index.html for the server (out/preview is served on 8766) and artifact.html,
the same content as an Artifact body (no doctype; <title> and tokens first)."""
from __future__ import annotations

import html
import os

STYLE = '''<style>
/* one reading column; pixel art on near-black panels like the in-game H panel; gold and jade from PanelTheme */
:root{--bg:#111416;--fg:#e9e4d6;--muted:#9aa39c;--panel:#1b2123;--line:#333c3b;--accent:#c8a860;--accent-2:#6cc5b0;--card:#171c1e;color-scheme:dark;
 --serif:"Noto Serif SC","Songti SC","SimSun",serif;--sans:system-ui,"PingFang SC","Microsoft YaHei","Noto Sans CJK SC",sans-serif;--mono:ui-monospace,Menlo,Consolas,monospace}
@media (prefers-color-scheme: light){:root:not([data-theme="dark"]){--bg:#f6f4ee;--fg:#1f2322;--muted:#6b716d;--panel:#1b2123;--line:#d6d2c6;--accent:#8a6e2c;--accent-2:#2f7f73;--card:#fffdf8;color-scheme:light}}
:root[data-theme="light"]{--bg:#f6f4ee;--fg:#1f2322;--muted:#6b716d;--panel:#1b2123;--line:#d6d2c6;--accent:#8a6e2c;--accent-2:#2f7f73;--card:#fffdf8;color-scheme:light}
body{background:var(--bg);color:var(--fg);font-family:var(--sans);line-height:1.6;padding-block:24px 48px;padding-inline:16px;margin:0}
main{max-width:1180px;margin:0 auto;min-width:0}
h1,h2,h3{font-family:var(--serif);text-wrap:balance;line-height:1.25}
h1{font-size:1.9rem;margin:0 0 .4rem;color:var(--accent)} h2{font-size:1.35rem;margin:2.4rem 0 .6rem;border-bottom:1px solid var(--line);padding-bottom:.3rem}
h3{font-size:1rem;margin:1.2rem 0 .4rem;color:var(--muted);font-family:var(--sans);font-weight:600}
p{max-width:70ch} .muted{color:var(--muted)}
code{font-family:var(--mono);font-size:.9em;background:color-mix(in srgb,var(--fg) 8%,transparent);padding:1px 5px;border-radius:3px}
img{max-width:100%;image-rendering:pixelated;display:block}
.scroll{overflow-x:auto;max-width:100%;background:var(--panel);border:1px solid var(--line);padding:6px} .scroll img{max-width:none}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(148px,1fr));gap:12px}
.card{margin:0;background:var(--card);border:1px solid var(--line);padding:10px;font-size:12px;min-width:0}
.card img{width:128px;height:128px;background:var(--panel);border:1px solid var(--line)}
.card figcaption{margin-top:6px;line-height:1.4} .card.dead img{opacity:.85}
.hero{display:flex;flex-wrap:wrap;gap:20px;align-items:flex-start;background:var(--panel);border:1px solid var(--line);padding:16px}
.hero img{width:256px;height:256px;max-width:100%;aspect-ratio:1} .hero div{min-width:0;flex:1 1 320px;color:#e9e4d6}
.mocks{display:flex;flex-wrap:wrap;gap:16px;align-items:flex-start}
.mock{background:#0a0e10;padding:20px;overflow-x:auto;max-width:100%;min-width:0;color:#efe8d4;font-family:var(--mono)}
.panel{background:#151b1d;border:1px solid #75643c;width:620px;padding:18px;display:flex;gap:18px;box-sizing:border-box}
.panel img{width:192px;height:192px;border:1px solid #39433f;background:#1c2426;flex:none}
.t{color:#ebd490;font-size:17px} .m{color:#9da7a0;font-size:13px;margin:4px 0 10px} .body{font-size:14px;line-height:1.5}
.btn{display:inline-block;border:1px solid #39433f;background:#26322f;padding:4px 18px;margin:14px 8px 0 0;font-size:13px}
.list{width:300px;font-size:13px} .row{display:flex;gap:10px;align-items:center;margin-top:6px} .row img{width:48px;height:48px;border:1px solid #39433f}
.row .s{color:#9da7a0}
.tbl{overflow-x:auto} table{border-collapse:collapse;font-size:14px;min-width:520px} td,th{border:1px solid var(--line);padding:5px 10px;vertical-align:top;text-align:left}
th{color:var(--muted);font-weight:600}
ul{padding-left:1.2em} li{margin:.2em 0}
a{color:var(--accent-2)}
</style>'''


def body_html(people: list, layers: list, everyone: int) -> str:
    esc = lambda s: html.escape(str(s))

    def name(e):
        return e["caption"].split(" · ")[0]

    def rest(e, a=1, b=None):
        return " · ".join(e["caption"].split(" · ")[a:b])

    s = people[0]
    cards = "".join(
        f'<figure class="card{" dead" if not e["alive"] else ""}"><img src="p/{e["id"]}.png" width="64" height="64" alt="{esc(name(e))}">'
        f'<figcaption><b>{esc(name(e))}</b><br>{esc(rest(e))}<br><span class="muted">{esc(e["mood"])} · {e["age"]}岁</span></figcaption></figure>'
        for e in people)
    rows = "".join(f'<div class="row"><img src="p/{e["id"]}.png" alt=""><span class="n">{esc(name(e))}</span><span class="s">{esc(rest(e, 2, 4))}</span></div>'
                   for e in people[1:6])
    parts = "".join(f'<h3>{esc(t)}</h3><div class="scroll"><img src="parts_{k}.png" alt="{esc(t)}"></div>' for k, t in layers)
    return f'''<main>
<h1>命簿人物像素头像</h1>
<p class="lede muted">第二版，2026-10-07。第一版生成器被否（"不好看"）；手绘样张得到认可（"大致方向对了，好多了，但是可以优化一点，然后你让子代理画吧"）。现在的生成器就是那张样张拆成的零件：脸、眼、刘海、后发、衣饰五个模块，每个变体都是子代理按样张风格逐像素手放的，人物的调色板再套上去。游戏代码没动。</p>
<div class="hero"><img src="hand/hand3.png" alt="认可的手绘样张"><div><b>认可的样张</b>（64×64，放大 4 倍）<br><span class="muted">所有零件的默认变体合成出来和它逐像素一致，有回归测试守着。</span></div></div>

<h2>一、48 人总览</h2>
<p>人物来自一份真实命簿样本（小档，{everyone} 个在世、87 座坟），名字、性别、境界、职位、宗门、年龄、五行根骨、性格、伤势都是真值。按职位排：宗主、长老、内门、外门、散修，最后 4 个是已故（灰）。注里的性格最强项决定眉和嘴，五行主根决定眼色（元婴异彩）。</p>
<div class="grid">{cards}</div>
<h3>3 倍带注整张图</h3><div class="scroll"><img src="sheet.png" alt="48 人总览"></div>

<h2>二、放进界面里是什么样</h2>
<p>HTML 示意，不是游戏截图。对话面板：头像放左边 64 GUI 像素（GUI 缩放 3 时是 192 屏幕像素），名字和职位挪到头像下，正文靠右。</p>
<div class="mocks">
<div class="mock"><div class="panel"><div><img src="p/{s["id"]}.png" alt=""><div class="t" style="margin-top:8px">{esc(name(s))}</div><div class="m">{esc(rest(s, 2))}</div></div>
<div><div class="t">{esc(name(s))} · 宗主 · {esc(s["caption"].split(" · ")[-1])}</div><div class="m">中州 · 外门弟子 · 声望 12</div>
<div class="body">「你既来了，便先在外门磨三年性子。宗门不养闲人，也不苛待肯用功的。」<br><br>「山门内的规矩，守山执事自会教你。去吧。」</div>
<span class="btn">拜入</span><span class="btn">告辞</span></div></div></div>
<div class="mock"><div class="list"><div class="m">天下 · 人物（列表行的小头像 16 GUI 像素）</div>{rows}</div></div>
</div>

<h2>三、各种缩放下的大小</h2>
<div class="scroll"><img src="sizes.png" alt="缩放对比"></div>

<h2>四、同一个人的一生</h2>
<p>同一个 id（零件不变），只改境界、职位、年龄、伤、生死。</p>
<div class="scroll"><img src="life.png" alt="同一人的一生"></div>

<h2>五、零件表（每行只换一个零件，左女右男）</h2>
{parts}

<h2>六、全员 {everyone} 人撞脸检查（放大 2 倍）</h2>
<div class="scroll"><img src="everyone.png" alt="全员"></div>

<h2>七、哪些数据决定哪些零件</h2>
<div class="tbl"><table>
<tr><th>命簿字段</th><th>决定</th></tr>
<tr><td>性别</td><td>脸型和下颌、刘海与后发的可选集、眼形偏好（女圆眼多、男杏眼多）、睫毛粗细与翘尾、唇色</td></tr>
<tr><td>境界</td><td>道袍档次（炼气素布 / 筑基染色 / 金丹锦缎金边玉佩 / 元婴玄色金绣）；元婴眼色异彩</td></tr>
<tr><td>职位</td><td>头饰：宗主玉冠（女凤钗）、长老抹额加簪（女玉簪）、内门发带、外门无、散修偶有布抹额；男长老和宗主必束道髻</td></tr>
<tr><td>年龄 ÷ 境界寿元</td><td>≥ 0.55 发色变灰，≥ 0.8 白发并加老态</td></tr>
<tr><td>五行主根</td><td>眼色：金→灰银、木→绿、水→蓝、火→琥珀、土→褐金</td></tr>
<tr><td>性格最强项（≥ 70）</td><td>眉与嘴：好斗吊眼怒眉抿嘴、野心挑眉斜笑、谨慎愁眉小嘴、好游圆眼张嘴笑、忠诚平眉；都不突出则平和微笑</td></tr>
<tr><td>伤势</td><td>≥ 20 绷带，≥ 50 加疤</td></tr>
<tr><td>在世 / 已故</td><td>已故整体转灰</td></tr>
<tr><td>宗门 id</td><td>发带、抹额、马尾绳的点缀色</td></tr>
<tr><td>人物 id</td><td>其余零件的确定性抽取（splitmix64，Java 端照抄即可得到同一张脸）</td></tr>
</table></div>

<h2>八、接进游戏要做的事（没动）</h2>
<ul>
<li>零件由 <code>tools/portraitgen</code> 画成分层贴图，Java 端按上表选索引逐层叠加；或者在服务端把每人的头像合成一次发给客户端（更简单，一张 64×64）。</li>
<li>对话包现在只传名字，要加性别、境界、职位、根骨主属、性格主项、年龄、伤势、人物 id。</li>
<li>对话面板左侧放头像；天下页人物详情顶部放头像，列表行放 16 px 缩略图。</li>
</ul>

<h2>九、请你定</h2>
<ul>
<li>哪些零件还不行（指行、指人）。</li>
<li>男修的脸够不够俊朗。</li>
<li>发色要不要加浅色（银白、淡紫、浅棕）。</li>
<li>接入方式：服务端合成发图，还是客户端分层拼。</li>
</ul>
<p class="muted">生成：<code>/usr/bin/python3 -m tools.portraitgen sheet --ledger out/preview/cultivator/portraits/sample/ledger.json --out out/preview/cultivator/portraits</code>。参照研究仍在 <a href="refs/NOTES.md">refs/NOTES.md</a>。</p>
</main>'''


def write_pages(out_dir: str, people: list, layers: list, everyone: int) -> None:
    body = body_html(people, layers, everyone)
    head = ('<title>命簿人物像素头像</title>\n'
            '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@600&display=swap">\n' + STYLE)
    with open(os.path.join(out_dir, "artifact.html"), "w", encoding="utf-8") as f:
        f.write(head + "\n" + body + "\n")
    with open(os.path.join(out_dir, "index.html"), "w", encoding="utf-8") as f:
        f.write('<!doctype html><html lang="zh"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">\n'
                + head + '</head><body>\n' + body + '\n</body></html>')
