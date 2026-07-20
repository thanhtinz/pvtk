// ---- shared helpers ----
const T = {
  get token() { return localStorage.getItem('pvtk_token'); },
  set token(v) { v ? localStorage.setItem('pvtk_token', v) : localStorage.removeItem('pvtk_token'); },
  get role() { return localStorage.getItem('pvtk_role'); },
  set role(v) { v ? localStorage.setItem('pvtk_role', v) : localStorage.removeItem('pvtk_role'); },
  get name() { return localStorage.getItem('pvtk_name'); },
  set name(v) { v ? localStorage.setItem('pvtk_name', v) : localStorage.removeItem('pvtk_name'); },
};
async function api(path, method = 'GET', body) {
  const h = { 'Content-Type': 'application/json' };
  if (T.token) h['Authorization'] = 'Bearer ' + T.token;
  const r = await fetch('/api' + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data.error || ('HTTP ' + r.status));
  return data;
}
function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg; t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 2600);
}
function iconUrl(iconId) { return `/api/items/${iconId}/icon.svg`; }
// Public site settings (contact info + download links), configured in admin.
let _site = null;
async function getSite() {
  if (_site) return _site;
  try { _site = (await api('/site')).site || {}; } catch (e) { _site = {}; }
  return _site;
}
// Use a <template> so table fragments (<tr>/<td>) parse correctly — a <div>
// silently drops them, which collapses table rows into one line of text.
function el(html) { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstChild; }
function modal(html) {
  const root = document.getElementById('modal-root');
  const bg = el(`<div class="modal-bg"><div class="modal">${html}</div></div>`);
  bg.addEventListener('click', e => { if (e.target === bg) root.innerHTML = ''; });
  root.innerHTML = ''; root.appendChild(bg);
  return bg;
}
function closeModal() { document.getElementById('modal-root').innerHTML = ''; }

// ---- auth ----
function showLogin(register) {
  modal(`
    <h3>${register ? 'Đăng ký' : 'Đăng nhập'}</h3>
    <label>Tên đăng nhập</label><input id="u"/>
    <label>Mật khẩu</label><input id="p" type="password"/>
    <div class="row" style="margin-top:16px">
      <button class="btn" id="go">${register ? 'Tạo tài khoản' : 'Đăng nhập'}</button>
      <button class="btn sec" onclick="closeModal()">Hủy</button>
    </div>`);
  document.getElementById('go').onclick = async () => {
    try {
      const u = document.getElementById('u').value, p = document.getElementById('p').value;
      const res = await api(register ? '/auth/register' : '/auth/login', 'POST', { username: u, password: p });
      T.token = res.token; T.role = res.role; T.name = res.username;
      closeModal(); renderUser(); toast('Xin chào ' + res.username + '!');
      if (location.hash === '#login') location.hash = '#home';
    } catch (e) { toast(e.message); }
  };
}
function logout() { T.token = T.role = T.name = null; renderUser(); go('home'); }
function renderUser() {
  const box = document.getElementById('userbox');
  document.querySelectorAll('.auth-only').forEach(e => e.classList.toggle('hidden', !T.token));
  if (T.token) {
    box.innerHTML = `${T.role === 'ADMIN' ? '<a class="btn small" href="/admin.html">Admin</a>' : ''}
      <span>${ICON.user} ${T.name}</span><a class="btn small sec" id="logoutBtn">Thoát</a>`;
    document.getElementById('logoutBtn').onclick = logout;
  } else {
    box.innerHTML = `<a class="btn small" id="loginBtn">Đăng nhập</a>
      <a class="btn small sec" id="registerBtn">Đăng ký</a>`;
    document.getElementById('loginBtn').onclick = () => showLogin(false);
    document.getElementById('registerBtn').onclick = () => showLogin(true);
  }
  // Bootstrap navbar userbox (logged-in pages)
  const bbox = document.getElementById('bsUserbox');
  if (bbox) {
    if (T.token) {
      bbox.innerHTML = `${T.role === 'ADMIN' ? '<a class="bbtn outline" href="/admin.html">Admin</a>' : ''}
        <span class="who">${ICON.user} ${T.name}</span><a class="bbtn primary" id="bsLogout">Thoát</a>`;
      document.getElementById('bsLogout').onclick = logout;
    } else {
      bbox.innerHTML = `<a class="bbtn outline" id="bsLogin">Đăng nhập</a><a class="bbtn primary" id="bsReg">Đăng ký</a>`;
      document.getElementById('bsLogin').onclick = () => showLogin(false);
      document.getElementById('bsReg').onclick = () => showLogin(true);
    }
  }
}

// ---- views ----
const app = () => document.getElementById('app');
function go(view) { location.hash = '#' + view; }
function setActive(view) {
  document.querySelectorAll('[data-view]').forEach(a => a.classList.toggle('active', a.dataset.view === view));
}

const views = {
  async home() {
    const site = await getSite();
    app().innerHTML = `
      <section class="hero-banner">
        <div class="hero-wrap">
          <img class="hero-banner-img" src="/img/hero-banner.jpg" alt="Phong Vân Truyền Kỳ · 10 Năm Kinh Điển"/>
          <a class="hero-play" onclick="playTrailer()" title="Xem trailer"><img src="/img/btn-play.png" alt="▶"/></a>
        </div>
      </section>

      <div class="action-row imgs">
        <a class="img-btn" onclick="showDownload()" title="Tải game"><img src="/img/btn-tai.png" alt="Tải game"/></a>
        <a class="img-btn big" onclick="go('topup')" title="Nạp thẻ"><img src="/img/btn-nap.jpg" alt="Nạp thẻ"/></a>
        <a class="img-btn" onclick="go('giftcode')" title="Nhận giftcode"><img src="/img/btn-gift.jpg" alt="Nhận giftcode"/></a>
      </div>

      <section class="portal-sec">
        <img class="cal-img" src="/img/title-tintuc.png" alt="Tin Tức"/>
        <div class="news-tabs" id="newsTabs">
          <a data-f="all" class="active">TIN TỨC</a>
          <a data-f="event">SỰ KIỆN</a>
          <a data-f="news">THÔNG BÁO</a>
        </div>
        <div class="news-list" id="newsList"><div class="nrow"><span class="nt muted">Đang tải…</span></div></div>
        <div class="contact-row">
          <a href="mailto:${esc(site.supportEmail || '')}">${ICON.mail} ${esc(site.supportEmail || '')}</a>
          <a href="tel:${esc((site.hotline || '').replace(/\s/g, ''))}">${ICON.phone} ${esc(site.hotline || '')}</a>
        </div>
      </section>

      <section class="portal-sec nhanvat">
        <img class="cal-img" src="/img/title-nhanvat.png" alt="Nhân Vật"/>
        <div class="char-stage">
          <a class="char-arrow left" id="charPrev"><img src="/img/arrow-left.png" alt="‹"/></a>
          <img class="char-portrait" id="charImg" src="/img/char-nam-1.png" alt="Nhân vật"/>
          <a class="char-arrow right" id="charNext"><img src="/img/arrow-right.png" alt="›"/></a>
          <div class="gender-toggle">
            <a id="genNam" title="Nam"><img id="genNamImg" src="/img/nam-on.png" alt="Nam"/></a>
            <a id="genNu" title="Nữ"><img id="genNuImg" src="/img/nu-off.png" alt="Nữ"/></a>
          </div>
        </div>
      </section>

      <section class="portal-sec">
        <img class="cal-img" src="/img/title-dacsac.png" alt="Đặc Sắc"/>
        <div class="feat-carousel">
          <a class="char-arrow left" id="featPrev"><img src="/img/arrow-left.png" alt="‹"/></a>
          <img class="feat-slide" id="featImg" src="/img/feat-1.jpg" alt="Đặc sắc"/>
          <a class="char-arrow right" id="featNext"><img src="/img/arrow-right.png" alt="›"/></a>
        </div>
        <div class="feat-dots" id="featDots"></div>
      </section>`;

    // Nhân Vật carousel: NAM/NỮ rosters, prev/next arrows.
    const roster = {
      nam: [1, 2, 3, 4, 5, 6, 7, 8].map(i => `/img/char-nam-${i}.png`),
      nu: ['/img/char-nu-1.png', '/img/char-nu-1b.png', '/img/char-nu-2.png', '/img/char-nu-3.png',
           '/img/char-nu-4.png', '/img/char-nu-5.png', '/img/char-nu-6.png', '/img/char-nu-7.png', '/img/char-nu-8.png'],
    };
    let gender = 'nam', ci = 0;
    const charImg = document.getElementById('charImg');
    const paint = () => {
      charImg.src = roster[gender][((ci % roster[gender].length) + roster[gender].length) % roster[gender].length];
      document.getElementById('genNamImg').src = gender === 'nam' ? '/img/nam-on.png' : '/img/nam-off.png';
      document.getElementById('genNuImg').src = gender === 'nu' ? '/img/nu-on.png' : '/img/nu-off.png';
    };
    document.getElementById('charPrev').onclick = () => { ci--; paint(); };
    document.getElementById('charNext').onclick = () => { ci++; paint(); };
    document.getElementById('genNam').onclick = () => { gender = 'nam'; ci = 0; paint(); };
    document.getElementById('genNu').onclick = () => { gender = 'nu'; ci = 0; paint(); };
    paint();

    // Đặc Sắc carousel of feature banners.
    const feats = ['/img/feat-1.jpg', '/img/feat-2.jpg', '/img/feat-3.jpg', '/img/feat-4.jpg', '/img/feat-5.jpg'];
    let fidx = 0;
    const featImg = document.getElementById('featImg');
    const featDots = document.getElementById('featDots');
    feats.forEach((_, i) => featDots.appendChild(el(`<span class="dot${i === 0 ? ' on' : ''}" data-i="${i}"></span>`)));
    const paintFeat = () => {
      fidx = ((fidx % feats.length) + feats.length) % feats.length;
      featImg.src = feats[fidx];
      featDots.querySelectorAll('.dot').forEach((d, i) => d.classList.toggle('on', i === fidx));
    };
    document.getElementById('featPrev').onclick = () => { fidx--; paintFeat(); };
    document.getElementById('featNext').onclick = () => { fidx++; paintFeat(); };
    featDots.querySelectorAll('.dot').forEach(d => d.onclick = () => { fidx = +d.dataset.i; paintFeat(); });
    if (window._featTimer) clearInterval(window._featTimer);
    window._featTimer = setInterval(() => { fidx++; paintFeat(); }, 4500);

    // News with client-side tab filtering.
    let allNews = [];
    try { allNews = (await api('/news')).news || []; } catch (e) { /* ignore */ }
    const listEl = document.getElementById('newsList');
    const renderNews = (filter) => {
      const rows = allNews.filter(n => filter === 'all' || n.type === filter);
      listEl.innerHTML = '';
      if (!rows.length) { listEl.innerHTML = '<div class="nrow"><span class="nt muted">Chưa có tin.</span></div>'; return; }
      rows.forEach((n, i) => listEl.appendChild(el(
        `<div class="nrow">
           ${i === 0 ? '<span class="new">New!</span>' : ''}
           <span class="nt">[${n.type === 'event' ? 'SỰ KIỆN' : 'THÔNG BÁO'}] ${esc(n.title)}</span>
           <span class="nd">${new Date(n.date).toLocaleDateString('vi')}</span></div>`)));
    };
    document.querySelectorAll('#newsTabs a').forEach(a => a.onclick = () => {
      document.querySelectorAll('#newsTabs a').forEach(x => x.classList.toggle('active', x === a));
      renderNews(a.dataset.f);
    });
    renderNews('all');
  },

  async news() {
    app().innerHTML = `<div class="container"><h2>Tin tức & Sự kiện</h2><div id="list" class="grid"></div></div>`;
    const { news } = await api('/news');
    const list = document.getElementById('list');
    if (!news.length) list.innerHTML = '<p class="muted">Chưa có tin tức.</p>';
    news.forEach(n => {
      const cd = n.type === 'event' && n.endAt ? `<div class="tag" data-end="${n.endAt}" style="margin-top:6px">${ICON.clock} …</div>` : '';
      list.appendChild(el(`<div class="card">
        <h3>${n.type === 'event' ? ICON.megaphone + ' ' : ICON.news + ' '}${esc(n.title)}</h3>
        <div class="muted" style="font-size:12px">${new Date(n.date).toLocaleString('vi')}</div>
        <p style="white-space:pre-wrap">${esc(n.body)}</p>${cd}</div>`));
    });
    tickCountdowns();
  },

  async leaderboard() {
    app().innerHTML = `<div class="bs-page">
      <h1 class="page-title">Bảng xếp hạng</h1>
      <p class="lead">Top cao thủ theo cấp độ và vàng.</p>
      <div class="card"><div class="card-body" style="padding:0">
        <table class="table table-striped table-hover" style="margin:0">
          <thead><tr><th style="width:70px">Hạng</th><th>Nhân vật</th><th>Cấp</th><th>Vàng</th></tr></thead>
          <tbody id="t"></tbody></table></div></div></div>`;
    const { top } = await api('/leaderboard');
    const t = document.getElementById('t');
    top.forEach(r => t.appendChild(el(`<tr>
      <td><span class="rank-badge ${r.rank <= 3 ? 'g' + r.rank : ''}">${r.rank}</span></td>
      <td class="fw-bold">${esc(r.username)}</td>
      <td><span class="badge rounded-pill text-bg-primary">Lv ${r.level}</span></td>
      <td>${r.gold.toLocaleString('vi')}</td></tr>`)));
    if (!top.length) t.appendChild(el('<tr><td colspan="4" class="text-muted">Chưa có dữ liệu.</td></tr>'));
  },

  async shop() {
    app().innerHTML = `<div class="bs-page">
      <h1 class="page-title">Webshop</h1>
      <p class="lead">Mua bằng KNB (nạp thẻ). Vật phẩm được gửi vào hòm thư trong game.</p>
      <div class="row" id="g"></div></div>`;
    const { products } = await api('/shop');
    const g = document.getElementById('g');
    if (!products.length) g.innerHTML = '<div class="col-12"><div class="card"><div class="card-body text-muted">Cửa hàng trống.</div></div></div>';
    products.forEach(p => {
      const c = el(`<div class="col-4"><div class="card"><div class="card-body">
        <div class="flex"><img class="icon-thumb" src="${iconUrl(p.icon)}"/>
          <div><div class="card-title" style="margin:0">${esc(p.name)}</div>
            <div class="text-muted small">${esc(p.itemName)} × ${p.count}</div></div></div>
        <div class="price" style="margin:14px 0 10px">${p.price} KNB</div>
        <button class="btn btn-primary btn-sm" style="width:100%">Mua ngay</button>
      </div></div></div>`);
      c.querySelector('button').onclick = async () => {
        if (!T.token) return showLogin(false);
        try { const r = await api('/shop/buy', 'POST', { productId: p.id }); toast(r.message); }
        catch (e) { toast(e.message); }
      };
      g.appendChild(c);
    });
  },

  async topup() {
    if (!T.token) return showLogin(false);
    app().innerHTML = `<div class="bs-page">
      <h1 class="page-title">Nạp KNB qua SePay</h1>
      <p class="lead">Chọn gói → quét QR / chuyển khoản đúng nội dung → hệ thống <b class="text-primary">tự cộng KNB thẳng vào tài khoản game</b>, dùng được ngay.</p>
      <div class="row" id="packs"></div></div>`;
    const { packages, sepay } = await api('/packages');
    const g = document.getElementById('packs');
    if (!sepay.enabled) {
      g.innerHTML = '<div class="col-12"><div class="card"><div class="card-body text-muted">Cổng nạp SePay chưa được bật. Vui lòng liên hệ quản trị viên.</div></div></div>';
      return;
    }
    if (!packages.length) g.innerHTML = '<div class="col-12"><div class="card"><div class="card-body text-muted">Chưa có gói nạp.</div></div></div>';
    packages.forEach(p => {
      const c = el(`<div class="col-3"><div class="card"><div class="card-body" style="text-align:center">
        <div class="text-muted small">${esc(p.name)}</div>
        <div class="price" style="margin:8px 0 2px">${p.knb + p.bonus} KNB</div>
        <div class="text-muted small">${p.priceVnd.toLocaleString('vi')} đ</div>
        ${p.bonus ? `<div style="margin:8px 0"><span class="badge rounded-pill text-bg-success">+${p.bonus} thưởng</span></div>` : '<div style="height:8px"></div>'}
        <button class="btn btn-primary btn-sm" style="width:100%">Nạp gói này</button>
      </div></div></div>`);
      c.querySelector('button').onclick = () => startOrder(p.id);
      g.appendChild(c);
    });
  },

  giftcode() {
    if (!T.token) return showLogin(false);
    app().innerHTML = `<div class="bs-page">
      <h1 class="page-title">Nhập Giftcode</h1>
      <p class="lead">Nhập mã quà tặng để nhận thưởng vào tài khoản.</p>
      <div class="row"><div class="col-6"><div class="card"><div class="card-body">
        <label class="form-label">Mã quà tặng</label>
        <input id="code" class="form-control" placeholder="VD: TANTHU2024"/>
        <button class="btn btn-primary" id="go" style="margin-top:16px">Nhận quà</button>
      </div></div></div></div></div>`;
    document.getElementById('go').onclick = async () => {
      try { const r = await api('/giftcode', 'POST', { code: document.getElementById('code').value });
        toast(r.message); } catch (e) { toast(e.message); }
    };
  },

  async profile() {
    if (!T.token) return showLogin(false);
    const me = await api('/me');
    const badge = (t, c) => `<span class="badge rounded-pill ${c}">${t}</span>`;
    app().innerHTML = `<div class="bs-page">
      <h1 class="page-title">Trang cá nhân</h1>
      <div class="row">
        <div class="col-6"><div class="card"><div class="card-body">
          <h4>Thông tin tài khoản</h4>
          <div class="info-row"><span class="k">Tài khoản</span><span class="v">${esc(me.username)} ${me.role === 'ADMIN' ? badge('ADMIN', 'text-bg-danger') : ''}</span></div>
          <div class="info-row"><span class="k">KNB</span><span class="v text-primary">${me.knb ?? 0} KNB</span></div>
          <div class="info-row"><span class="k">Vàng trong game</span><span class="v">${me.gold}</span></div>
          <div class="info-row"><span class="k">Cấp độ</span><span class="v">${me.level}</span></div>
          <div class="info-row"><span class="k">Trạng thái</span><span class="v">${me.online ? badge('Đang online', 'text-bg-success') : badge('Offline', 'text-bg-secondary')}</span></div>
        </div></div></div>
        <div class="col-6"><div class="card"><div class="card-body">
          <h4>Đổi mật khẩu</h4>
          <label class="form-label">Mật khẩu cũ</label><input id="op" type="password" class="form-control"/>
          <label class="form-label" style="margin-top:12px">Mật khẩu mới</label><input id="np" type="password" class="form-control"/>
          <button class="btn btn-primary" id="cp" style="margin-top:16px">Đổi mật khẩu</button>
        </div></div></div>
        <div class="col-12"><div class="card"><div class="card-body">
          <h4>Lịch sử giao dịch</h4>
          <table class="table table-striped table-hover">
            <thead><tr><th>Thời gian</th><th>Loại</th><th>Chi tiết</th><th>Số lượng</th></tr></thead>
            <tbody id="txt"></tbody></table>
        </div></div></div>
      </div></div>`;
    document.getElementById('cp').onclick = async () => {
      try { await api('/auth/change-password', 'POST',
        { oldPassword: document.getElementById('op').value, newPassword: document.getElementById('np').value });
        toast('Đổi mật khẩu thành công!'); } catch (e) { toast(e.message); }
    };
    const { transactions } = await api('/me/transactions');
    const txt = document.getElementById('txt');
    if (!transactions.length) txt.appendChild(el('<tr><td colspan="4" class="text-muted">Chưa có giao dịch.</td></tr>'));
    transactions.forEach(t => txt.appendChild(el(`<tr>
      <td class="text-muted small">${new Date(t.time).toLocaleString('vi')}</td>
      <td><span class="badge rounded-pill text-bg-secondary">${esc(t.type)}</span></td><td>${esc(t.detail)}</td>
      <td style="color:${t.amount < 0 ? '#dc3545' : '#198754'};font-weight:600">${t.amount > 0 ? '+' : ''}${t.amount} ${esc(t.currency)}</td></tr>`)));
  },
};

function esc(s) { return String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }
function fmtDur(ms) {
  if (ms <= 0) return 'Đã kết thúc';
  const s = Math.floor(ms / 1000), d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600),
    m = Math.floor(s % 3600 / 60), ss = s % 60;
  return (d ? d + 'n ' : '') + String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(ss).padStart(2, '0');
}
let _cdTimer = null;
function tickCountdowns() {
  if (_cdTimer) clearInterval(_cdTimer);
  const upd = () => {
    const now = Date.now();
    document.querySelectorAll('[data-end]').forEach(e => {
      e.innerHTML = ICON.clock + ' Còn lại: ' + fmtDur(Number(e.dataset.end) - now);
    });
  };
  upd(); _cdTimer = setInterval(upd, 1000);
}

let _pollTimer = null;
async function startOrder(packageId) {
  try {
    const o = await api('/topup/order', 'POST', { packageId });
    modal(`
      <h3>Chuyển khoản để nhận ${o.knb} KNB</h3>
      <div style="text-align:center"><img src="${esc(o.qrUrl)}" alt="QR" style="width:230px;max-width:100%;background:#fff;border-radius:10px"/></div>
      <table style="margin-top:10px">
        <tr><th>Ngân hàng</th><td>${esc(o.bankCode)}</td></tr>
        <tr><th>Số tài khoản</th><td><b>${esc(o.accountNumber)}</b></td></tr>
        <tr><th>Chủ tài khoản</th><td>${esc(o.accountHolder)}</td></tr>
        <tr><th>Số tiền</th><td><b>${o.amountVnd.toLocaleString('vi')} đ</b></td></tr>
        <tr><th>Nội dung CK</th><td><b style="color:var(--gold)">${esc(o.content)}</b></td></tr>
      </table>
      <p class="muted" id="ost">${ICON.clock} Đang chờ thanh toán... (tự cộng KNB khi nhận được tiền)</p>
      <div class="row"><button class="btn sec" onclick="stopPoll();closeModal()">Đóng</button></div>`);
    if (_pollTimer) clearInterval(_pollTimer);
    _pollTimer = setInterval(async () => {
      try {
        const s = await api('/topup/order/' + o.orderId);
        if (s.status === 'paid') {
          stopPoll();
          document.getElementById('ost').innerHTML = ICON.check + ' Đã nhận thanh toán! KNB: <b>' + s.balance + '</b>';
          toast('Nạp thành công +' + s.knb + ' KNB!');
        }
      } catch (e) { /* keep polling */ }
    }, 3000);
  } catch (e) { toast(e.message); }
}
function stopPoll() {
  if (_pollTimer) { clearInterval(_pollTimer); _pollTimer = null; }
  if (window._featTimer) { clearInterval(window._featTimer); window._featTimer = null; }
}
window.startOrder = startOrder; window.stopPoll = stopPoll;

function router() {
  stopPoll();
  const view = (location.hash || '#home').slice(1);
  setActive(view);
  // Logged-in content pages use the Bootstrap-5 style; the landing keeps its own.
  document.body.classList.toggle('bs', view !== 'home' && view !== 'news');
  (views[view] || views.home)();
}
async function showDownload() {
  const site = await getSite();
  const dl = (url, cls, ico, label) => {
    const has = url && url.trim();
    return has
      ? `<a class="btn ${cls}" href="${esc(url)}" target="_blank" rel="noopener">${ico} ${label}</a>`
      : `<a class="btn ${cls}" style="opacity:.55;cursor:default" onclick="toast('Đang cập nhật')">${ico} ${label}</a>`;
  };
  modal(`
    <h3>${ICON.mobile} Tải Phong Vân Online</h3>
    <p class="muted">Chọn nền tảng để tải/cài client. Tất cả dùng chung một máy chủ.</p>
    <div class="grid" style="grid-template-columns:1fr 1fr;gap:10px;margin-top:12px">
      ${dl(site.downloadPc, '', ICON.monitor, 'PC (Windows/macOS/Linux)')}
      ${dl(site.downloadAndroid, '', ICON.mobile, 'Android (APK)')}
      ${dl(site.downloadIos, 'sec', ICON.apple, 'iOS')}
      ${dl(site.downloadJava, 'sec', ICON.coffee, 'Java Client')}
    </div>
    <div class="row" style="margin-top:14px"><button class="btn sec" onclick="closeModal()">Đóng</button></div>`);
}
window.showDownload = showDownload;

// Wire footer contact/social links from site config (both footers).
async function applyFooter() {
  const site = await getSite();
  const fb = document.getElementById('fbLink');
  if (fb) { fb.href = site.facebookUrl || '#'; fb.onclick = site.facebookUrl ? null : (e) => { e.preventDefault(); toast('Chưa cấu hình Facebook'); }; }
  const guide = document.getElementById('guideLink');
  if (guide) guide.onclick = () => { if (site.guideUrl) window.open(site.guideUrl, '_blank'); else go('news'); };
  // Bootstrap footer
  const set = (id, txt) => { const e = document.getElementById(id); if (e) e.textContent = txt; };
  set('bsEmailT', site.supportEmail || '');
  set('bsPhoneT', site.hotline || '');
  const be = document.getElementById('bsEmail'); if (be) be.href = 'mailto:' + (site.supportEmail || '');
  const bp = document.getElementById('bsPhone'); if (bp) bp.href = 'tel:' + (site.hotline || '').replace(/\s/g, '');
  const bfb = document.getElementById('bsFb'); if (bfb) { bfb.href = site.facebookUrl || '#'; bfb.onclick = site.facebookUrl ? null : (e) => { e.preventDefault(); toast('Chưa cấu hình Facebook'); }; }
  const bg = document.getElementById('bsGuide'); if (bg) bg.onclick = () => { if (site.guideUrl) window.open(site.guideUrl, '_blank'); else go('news'); };
  applySiteIdentity(site);
}

// Applies the admin-configured website name / tagline / copyright across the page.
function applySiteIdentity(site) {
  const name = (site.siteName || 'Phong Vân Online').trim();
  const tagline = (site.tagline || '').trim();
  document.title = tagline ? `${name} — ${tagline}` : name;
  // Brand labels keep their logo <img>; only the trailing text node changes.
  document.querySelectorAll('.bs-brand, .bs-foot-brand').forEach(el => {
    const last = el.lastChild;
    if (last && last.nodeType === 3) last.textContent = ' ' + name;
  });
  const logo = document.querySelector('footer.site .logo');
  if (logo) logo.textContent = name.toUpperCase();
  if (site.copyright) {
    const bottom = document.querySelector('.bs-foot-bottom');
    if (bottom) bottom.textContent = site.copyright;
  }
}

function playTrailer() {
  modal(`
    <h3>▶ Trailer Phong Vân Online</h3>
    <div style="background:#000;border-radius:10px;aspect-ratio:16/9;display:flex;align-items:center;justify-content:center;color:#c9b98a">
      Trailer sắp ra mắt — theo dõi Fanpage để xem sớm nhất!
    </div>
    <div class="row" style="margin-top:14px;justify-content:flex-end"><button class="btn sec" onclick="closeModal()">Đóng</button></div>`);
}
window.playTrailer = playTrailer;

window.addEventListener('hashchange', router);
// Mobile hamburger menus (wuxia header + bootstrap navbar).
const headerMenu = document.getElementById('headerMenu');
const hamburger = document.getElementById('hamburger');
const bsCollapse = document.getElementById('bsCollapse');
if (hamburger) hamburger.onclick = () => headerMenu.classList.toggle('open');
const bsToggler = document.getElementById('bsToggler');
if (bsToggler) bsToggler.onclick = () => bsCollapse.classList.toggle('open');
// Nav links exist in both headers + footer; wire them all.
document.querySelectorAll('[data-view]').forEach(a => a.addEventListener('click', () => {
  go(a.dataset.view);
  if (headerMenu) headerMenu.classList.remove('open');
  if (bsCollapse) bsCollapse.classList.remove('open');
}));
window.go = go; window.closeModal = closeModal;
renderUser();
if (window.hydrateIcons) hydrateIcons();
applyFooter();
router();
