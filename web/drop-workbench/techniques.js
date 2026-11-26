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
    let activeTag = '';
    let selectedId;

    function options(select, entries) {
        select.replaceChildren(new Option('全部', ''));
        for (const [value, label] of entries) select.add(new Option(label, value));
    }

    function categoryEntries() {
        if (activeTag) {
            const order = new Map(catalogue.realms.map(([id], index) => [id, index]));
            return catalogue.techniques.filter(row => row.traits.some(trait => trait.id === activeTag))
                .sort((a, b) => (order.get(a.realm_id) ?? 0) - (order.get(b.realm_id) ?? 0)
                    || a.grade - b.grade || a.name.localeCompare(b.name, 'zh-CN'));
        }
        return catalogue.techniques.filter(row => row.category === category);
    }

    function renderCategories() {
        categoryButtons.innerHTML = catalogue.categories.map(({id, name}) => {
            const count = catalogue.techniques.filter(row => row.category === id).length;
            return `<button type="button" data-technique-category="${escape(id)}" aria-pressed="${!activeTag && id === category}">
                <span>${escape(name)}</span><span class="technique-count">${count}</span>
            </button>`;
        }).join('');
    }

    function facts(rows) {
        return `<dl class="technique-facts">${rows.map(([label, value]) => `
            <div><dt>${escape(label)}</dt><dd>${escape(value)}</dd></div>`).join('')}</dl>`;
    }

    function link(params = {}) {
        const query = new URLSearchParams(params).toString();
        return query ? '#techniques?' + query : '#techniques';
    }

    function renderTraits(entry, section) {
        if (!entry.traits.length) return '';
        const rows = entry.traits.map(trait => `
            <li>
                <a class="technique-tag" href="${escape(link({tag: trait.id, id: entry.id, category: entry.category}))}">
                    ${escape(trait.label)}
                </a>
                <span class="technique-trait-role">${escape(trait.role)}</span>
                <span>${escape(trait.description)}</span>
            </li>`).join('');
        return section('特性', `<ul class="technique-traits">${rows}</ul>`);
    }

    function renderProgressions(entry, section) {
        return entry.progressions.map(progression => {
            const chapters = progression.chapters.map(chapter => chapter.id === entry.id
                ? `<strong aria-current="step">${escape(chapter.name)}</strong>`
                : `<a href="${escape(link({id: chapter.id}))}">${escape(chapter.name)}</a>`).join(' → ');
            return section(progression.name + ' · ' + progression.tag, `<p class="technique-chapters">${chapters}</p>`);
        }).join('');
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
            ${renderTraits(entry, section)}
            ${renderProgressions(entry, section)}
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
            const keywords = [row.name, row.id, row.school, row.heart?.direction, ...row.elements, ...row.heritages,
                ...row.traits.map(trait => trait.label)].join(' ').toLowerCase();
            return (!grade.value || row.grade === Number(grade.value))
                && (!realm.value || row.realm_id === realm.value)
                && (!slot.value || row.heart?.slot === slot.value)
                && keywords.includes(query);
        });

        get('technique-results').textContent = `显示 ${filtered.length} / ${entries.length} 门`;
        list.innerHTML = filtered.map(row => `
            <button type="button" class="technique-row" data-technique-id="${escape(row.id)}">
                <span class="technique-row-name">${escape(row.name)}</span>
                <span class="technique-row-realm">${escape(activeTag
                    ? row.realm + ' · ' + row.traits.filter(trait => trait.id === activeTag).map(trait => trait.role).join('、')
                    : row.realm)}</span>
                <span class="technique-grade" data-grade="${row.grade}">${escape(catalogue.grades[row.grade])}</span>
            </button>`).join('');
        selectEntry(filtered.find(row => row.id === selectedId) || filtered[0]);
    }

    function configureFilters() {
        renderCategories();
        const info = catalogue.categories.find(row => row.id === category);
        get('technique-category-description').textContent = activeTag
            ? '同标签功法 · ' + catalogue.trait_tags[activeTag]
            : info.description;
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
    }

    function route() {
        return new URLSearchParams(location.hash.slice('#techniques'.length + 1));
    }

    function selectRoute() {
        const params = route();
        const id = params.get('id');
        const entry = catalogue.techniques.find(row => row.id === id);
        const requestedTag = params.get('tag');
        const tag = Object.hasOwn(catalogue.trait_tags, requestedTag) ? requestedTag : '';
        const requestedCategory = catalogue.categories.find(row => row.id === params.get('category'))?.id;
        const categoryId = (tag ? requestedCategory ?? entry?.category : entry?.category ?? requestedCategory)
            ?? catalogue.categories[0].id;
        if (category !== categoryId || activeTag !== tag) {
            category = categoryId;
            activeTag = tag;
            configureFilters();
        }
        selectedId = id ?? undefined;
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
            selectRoute();
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
        const showTechniques = location.hash === '#techniques' || location.hash.startsWith('#techniques?');
        view.hidden = !showTechniques;
        get('drop-view').hidden = showTechniques;
        get('drop-actions').hidden = showTechniques;
        for (const [link, active] of [['nav-drops', !showTechniques], ['nav-techniques', showTechniques]]) {
            if (active) get(link).setAttribute('aria-current', 'page');
            else get(link).removeAttribute('aria-current');
        }
        if (showTechniques) {
            if (catalogue) selectRoute();
            else loadCatalogue();
        }
    }

    categoryButtons.addEventListener('click', event => {
        const button = event.target.closest('[data-technique-category]');
        if (button && (activeTag || button.dataset.techniqueCategory !== category))
            location.hash = link({category: button.dataset.techniqueCategory});
    });
    list.addEventListener('click', event => {
        const button = event.target.closest('[data-technique-id]');
        if (!button) return;
        const id = button.dataset.techniqueId;
        const params = activeTag ? {tag: activeTag, id, category} : {id, category};
        location.hash = link(params);
        if (window.matchMedia('(max-width: 760px)').matches) {
            detail.focus({preventScroll: true});
            detail.scrollIntoView({block: 'start'});
        }
    });
    detail.addEventListener('click', event => {
        const anchor = event.target.closest('a[href^="#techniques?"]');
        if (anchor && selectedId && route().get('id') !== selectedId) {
            const params = activeTag ? {tag: activeTag, id: selectedId, category} : {id: selectedId, category};
            history.replaceState(null, '', link(params));
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
