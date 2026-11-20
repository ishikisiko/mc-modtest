'use strict';

(() => {
    const get = id => document.getElementById(id);
    const view = get('technique-view');
    const categoryButtons = get('technique-categories');
    const search = get('technique-search');
    const grade = get('technique-grade');
    const realm = get('technique-realm');
    const slot = get('technique-slot');
    const list = get('technique-list');
    const detail = get('technique-detail');
    const status = get('technique-status');
    const retry = get('technique-retry');
    const escape = value => String(value).replace(/[&<>"']/g, char => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    })[char]);

    let catalogue;
    let loading = false;
    let category;
    let selectedId;

    function options(select, entries) {
        select.replaceChildren(new Option('全部', ''));
        for (const [value, label] of entries) select.add(new Option(label, value));
    }

    function categoryEntries() {
        return catalogue.techniques.filter(row => row.category === category);
    }

    function renderCategories() {
        categoryButtons.innerHTML = catalogue.categories.map(({id, name}) => {
            const count = catalogue.techniques.filter(row => row.category === id).length;
            return `<button type="button" data-technique-category="${escape(id)}" aria-pressed="${id === category}">
                <span>${escape(name)}</span><span class="technique-count">${count}</span>
            </button>`;
        }).join('');
    }

    function facts(rows) {
        return `<dl class="technique-facts">${rows.map(([label, value]) => `
            <div><dt>${escape(label)}</dt><dd>${escape(value)}</dd></div>`).join('')}</dl>`;
    }

    function renderDetail(entry) {
        if (!entry) {
            detail.innerHTML = '<p class="technique-empty">没有符合条件的功法。</p>';
            return;
        }
        const metadata = [['最低境界', entry.realm], ['五行', entry.elements.join('、') || '无']];
        if (entry.heart) metadata.push(['栏位', entry.heart.slot], ['方向', entry.heart.direction]);
        if (entry.school) metadata.push(['流派', entry.school]);
        if (entry.heritages.length) metadata.push(['传承', entry.heritages.join('、')]);

        const {effect, growth} = entry;
        const note = effect.note ? `<p>${escape(effect.note)}</p>` : '';
        const section = (title, body) => `<section><h3>${escape(title)}</h3>${body}</section>`;
        const base = effect.base?.length ? section('基础加值', facts(effect.base)) : '';
        const extra = effect.extra?.length ? section('额外结算', facts(effect.extra)) : '';
        const other = effect.stats.length || note ? section(effect.name, note + facts(effect.stats)) : '';
        const effects = base + extra + other || section('效果', '<p>当前效果待配置。</p>');
        const phases = effect.phases ? `<section><h3>招式分段</h3>${facts(effect.phases)}</section>` : '';
        const practice = growth ? `
            <section>
                <h3>修习与参悟</h3>
                ${facts(growth.stages)}
                ${facts(growth.choices)}
            </section>` : '';

        detail.innerHTML = `
            <div class="technique-detail-heading">
                <h2>${escape(entry.name)}</h2>
                <span class="technique-grade" data-grade="${entry.grade}">${escape(catalogue.grades[entry.grade])}</span>
            </div>
            <p class="technique-id mono">${escape(entry.id)}</p>
            ${facts(metadata)}
            ${effects}
            ${phases}
            <section><h3>学习条件</h3>${facts(entry.learning)}</section>
            ${practice}`;
    }

    function selectEntry(entry) {
        selectedId = entry?.id;
        for (const button of list.querySelectorAll('[data-technique-id]')) {
            button.setAttribute('aria-pressed', String(button.dataset.techniqueId === selectedId));
        }
        renderDetail(entry);
    }

    function renderResults() {
        const query = search.value.trim().toLowerCase();
        const entries = categoryEntries();
        const filtered = entries.filter(row => {
            const keywords = [row.name, row.id, row.school, row.heart?.direction, ...row.elements, ...row.heritages].join(' ').toLowerCase();
            return (!grade.value || row.grade === Number(grade.value))
                && (!realm.value || row.realm_id === realm.value)
                && (!slot.value || row.heart?.slot === slot.value)
                && keywords.includes(query);
        });

        get('technique-results').textContent = `显示 ${filtered.length} / ${entries.length} 门`;
        list.innerHTML = filtered.map(row => `
            <button type="button" class="technique-row" data-technique-id="${escape(row.id)}">
                <span class="technique-row-name">${escape(row.name)}</span>
                <span class="technique-row-realm">${escape(row.realm)}</span>
                <span class="technique-grade" data-grade="${row.grade}">${escape(catalogue.grades[row.grade])}</span>
            </button>`).join('');
        selectEntry(filtered.find(row => row.id === selectedId) || filtered[0]);
    }

    function selectCategory(id) {
        category = id;
        const info = catalogue.categories.find(row => row.id === id);
        get('technique-category-description').textContent = info.description;
        for (const button of categoryButtons.querySelectorAll('button')) {
            button.setAttribute('aria-pressed', String(button.dataset.techniqueCategory === id));
        }

        const entries = categoryEntries();
        const grades = [...new Set(entries.map(row => row.grade))];
        const realms = new Set(entries.map(row => row.realm_id));
        const slots = [...new Set(entries.filter(row => row.heart).map(row => row.heart.slot))];
        options(grade, grades.map(number => [number, catalogue.grades[number]]));
        options(realm, catalogue.realms.filter(([id]) => realms.has(id)));
        get('technique-slot-field').hidden = slots.length === 0;
        options(slot, slots.map(name => [name, name]));
        search.value = '';
        list.scrollTop = 0;
        renderResults();
    }

    async function loadCatalogue() {
        if (catalogue || loading) return;
        loading = true;
        retry.hidden = true;
        status.textContent = '正在载入功法目录';
        try {
            const response = await fetch(new URL('techniques.json', location.href), {cache: 'no-store'});
            if (!response.ok) throw new Error('无法读取功法目录');
            catalogue = await response.json();
            category = catalogue.categories[0].id;
            renderCategories();
            selectCategory(category);
            status.textContent = `源仙 ${catalogue.version} · ${catalogue.techniques.length} 门`;
            get('technique-content').hidden = false;
        } catch (error) {
            catalogue = undefined;
            status.textContent = '功法目录载入失败，请重试。';
            retry.hidden = false;
        } finally {
            loading = false;
        }
    }

    function updateView() {
        const showTechniques = location.hash === '#techniques';
        view.hidden = !showTechniques;
        get('drop-view').hidden = showTechniques;
        get('drop-actions').hidden = showTechniques;
        for (const [link, active] of [['nav-drops', !showTechniques], ['nav-techniques', showTechniques]]) {
            if (active) get(link).setAttribute('aria-current', 'page');
            else get(link).removeAttribute('aria-current');
        }
        if (showTechniques) loadCatalogue();
    }

    categoryButtons.addEventListener('click', event => {
        const button = event.target.closest('[data-technique-category]');
        if (button && button.dataset.techniqueCategory !== category) selectCategory(button.dataset.techniqueCategory);
    });
    list.addEventListener('click', event => {
        const button = event.target.closest('[data-technique-id]');
        if (!button) return;
        selectEntry(catalogue.techniques.find(row => row.id === button.dataset.techniqueId));
        if (window.matchMedia('(max-width: 760px)').matches) {
            detail.focus({preventScroll: true});
            detail.scrollIntoView({block: 'start'});
        }
    });
    search.addEventListener('input', renderResults);
    grade.addEventListener('change', renderResults);
    realm.addEventListener('change', renderResults);
    slot.addEventListener('change', renderResults);
    retry.addEventListener('click', loadCatalogue);
    window.addEventListener('hashchange', updateView);
    updateView();
})();
