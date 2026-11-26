// Made in Fenrir by scanning the site's pages (Make extension → Auto).
// The RULES block holds what the scan found; the code below is the same for every site.
const { fetchApi } = require('@libs/fetch');
const { load } = require('cheerio');

class FenrirScannedSite {
  constructor() {
    this.id = RULES.id;
    this.name = RULES.name;
    this.version = '1.0.0';
    this.site = RULES.site;
    this.icon = '';
  }

  absolute(path) {
    if (!path) return '';
    if (/^https?:/.test(path)) return path;
    if (path.indexOf('//') === 0) return 'https:' + path;
    return this.site.replace(/\/$/, '') + '/' + path.replace(/^\//, '');
  }

  relative(href) {
    if (!href) return '';
    return href.replace(/^https?:\/\/[^/]+/, '').replace(/#.*$/, '') || '/';
  }

  async loadPage(path) {
    const url = this.absolute(path);
    const result = await fetchApi(url);
    if (!result.ok) throw new Error('HTTP ' + result.status + ' for ' + url);
    return load(await result.text());
  }

  image(img) {
    if (!img || !img.length) return '';
    const src = img.attr('data-src') || img.attr('data-lazy-src') || img.attr('data-original') || img.attr('src') || '';
    return src.indexOf('data:') === 0 ? '' : this.absolute(src);
  }

  books($) {
    const seen = {};
    const out = [];
    $(RULES.items).toArray().forEach(item => {
      const box = $(item);
      const links = box.find('a').toArray().filter(a => {
        const href = $(a).attr('href') || '';
        return href && (!RULES.linkFilter || href.indexOf(RULES.linkFilter) !== -1);
      });
      if (!links.length) return;
      const path = this.relative($(links[0]).attr('href'));
      if (!path || path === '/' || seen[path]) return;
      let name = '';
      links.forEach(a => {
        const t = ($(a).attr('title') || $(a).text() || '').replace(/\s+/g, ' ').trim();
        if (t.length > name.length) name = t;
      });
      if (!name) name = (box.find('img').attr('alt') || '').trim();
      if (!name) return;
      seen[path] = true;
      out.push({ name, path, cover: this.image(box.find('img').first()) });
    });
    return out;
  }

  async popularNovels(pageNo) {
    if (pageNo > 1 && !RULES.listPaged) return [];
    const url = pageNo > 1 ? RULES.listPaged.replace('{page}', String(pageNo)) : RULES.list;
    return this.books(await this.loadPage(url));
  }

  async searchNovels(searchTerm, pageNo) {
    if (RULES.search) {
      if (pageNo > 1) return [];
      return this.books(await this.loadPage(RULES.search.replace('{term}', encodeURIComponent(searchTerm))));
    }
    // No search on the site: look through the first pages of the list.
    const term = searchTerm.toLowerCase();
    if (pageNo > 1) return [];
    let all = [];
    for (let p = 1; p <= (RULES.listPaged ? 5 : 1); p++) {
      const page = await this.popularNovels(p);
      if (!page.length) break;
      all = all.concat(page);
    }
    return all.filter(b => b.name.toLowerCase().indexOf(term) !== -1);
  }

  chapterLinks($) {
    return $(RULES.chapterItems).toArray().map(item => {
      const box = $(item);
      const a = box.is('a') ? box : box.find('a').first();
      const href = a.attr('href');
      if (!href || (RULES.chapterFilter && href.indexOf(RULES.chapterFilter) === -1)) return null;
      return { name: (a.attr('title') || a.text() || '').replace(/\s+/g, ' ').trim(), path: this.relative(href) };
    }).filter(c => c && c.name && c.path);
  }

  nextPage($) {
    if (!RULES.nextWords) return '';
    const rel = $('a[rel="next"]').attr('href');
    if (rel) return rel;
    const hit = $('a').toArray().find(a => RULES.nextWords.indexOf($(a).text().replace(/\s+/g, ' ').trim()) !== -1);
    return hit ? $(hit).attr('href') || '' : '';
  }

  async parseNovel(novelPath) {
    const $ = await this.loadPage(novelPath);
    const name = ($(RULES.title).first().text() || $('meta[property="og:title"]').attr('content') || '').replace(/\s+/g, ' ').trim();
    const cover = this.absolute($('meta[property="og:image"]').attr('content') || '') || this.image($('img').first());
    const summary = ($('meta[property="og:description"]').attr('content') || $('meta[name="description"]').attr('content') || '').trim();

    let chapters = this.chapterLinks($);
    // Chapter lists split over pages: follow "Next" a limited number of times.
    let page = $;
    const visited = {};
    visited[this.relative(novelPath)] = true;
    for (let i = 0; i < 60; i++) {
      const next = this.relative(this.nextPage(page));
      if (!next || visited[next]) break;
      visited[next] = true;
      page = await this.loadPage(next);
      const more = this.chapterLinks(page);
      if (!more.length) break;
      chapters = chapters.concat(more);
    }

    const seen = {};
    chapters = chapters.filter(c => (seen[c.path] ? false : (seen[c.path] = true)));
    const number = c => {
      const m = c.name.match(/(\d+(\.\d+)?)/);
      return m ? parseFloat(m[1]) : NaN;
    };
    if (chapters.length > 1) {
      const first = number(chapters[0]);
      const last = number(chapters[chapters.length - 1]);
      if (!isNaN(first) && !isNaN(last) && first > last) chapters.reverse();
    }
    return {
      path: novelPath,
      name,
      cover,
      summary,
      chapters: chapters.map((c, i) => ({ name: c.name, path: c.path, chapterNumber: i + 1 })),
    };
  }

  async parseChapter(chapterPath) {
    const $ = await this.loadPage(chapterPath);
    const box = $(RULES.content).first();
    box.find('script, style, ins, iframe, noscript, form, button, .ads, [class*="adsbygoogle"]').remove();
    return box.html() || '';
  }
}

exports.default = new FenrirScannedSite();
