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
function el(html) { const d = document.createElement('div'); d.innerHTML = html.trim(); return d.firstChild; }
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
      <span>👤 ${T.name}</span><a class="btn small sec" id="logoutBtn">Thoát</a>`;
    document.getElementById('logoutBtn').onclick = logout;
  } else {
    box.innerHTML = `<a class="btn small" id="loginBtn">Đăng nhập</a>
      <a class="btn small sec" id="registerBtn">Đăng ký</a>`;
    document.getElementById('loginBtn').onclick = () => showLogin(false);
    document.getElementById('registerBtn').onclick = () => showLogin(true);
  }
}

// ---- views ----
const app = () => document.getElementById('app');
function go(view) { location.hash = '#' + view; }
function setActive(view) {
  document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('active', a.dataset.view === view));
}

const views = {
  async home() {
    app().innerHTML = `
      <section class="hero">
        <div class="eyebrow">Kiếm Hiệp · MMORPG</div>
        <h1>PHONG VÂN ONLINE</h1>
        <p class="sub">Bản dựng lại đa nền tảng (PC · Android · iOS · Java) của tựa game kiếm hiệp
          huyền thoại. Luyện công, lập bang, chinh chiến — trở thành đệ nhất cao thủ thiên hạ!</p>
        <div class="cta">
          <button class="btn big" onclick="go('topup')">💳 Nạp thẻ</button>
          <button class="btn big sec" onclick="go('shop')">🛒 Vào Webshop</button>
        </div>
        <div class="server-bar" id="serverBar">
          <div class="stat"><div class="k">Máy chủ</div><div class="v on" id="srvState">● Hoạt động</div></div>
          <div class="stat"><div class="k">Đang online</div><div class="v" id="srvOnline">—</div></div>
          <div class="stat"><div class="k">Cao thủ</div><div class="v" id="srvTop">—</div></div>
          <div class="stat"><div class="k">Nền tảng</div><div class="v" style="font-size:16px">PC · Mobile · Java</div></div>
        </div>
      </section>

      <div class="quick">
        <a onclick="go('topup')"><span class="qi">💳</span><span class="ql">Nạp thẻ</span></a>
        <a onclick="go('giftcode')"><span class="qi">🎁</span><span class="ql">Giftcode</span></a>
        <a onclick="go('shop')"><span class="qi">🛒</span><span class="ql">Webshop</span></a>
        <a onclick="go('leaderboard')"><span class="qi">🏆</span><span class="ql">Xếp hạng</span></a>
        <a onclick="go('news')"><span class="qi">📰</span><span class="ql">Tin tức</span></a>
        <a onclick="go('profile')"><span class="qi">👤</span><span class="ql">Cá nhân</span></a>
      </div>

      <div class="section">
        <div class="section-title"><span>⚔ Tính năng nổi bật</span></div>
        <div class="grid cards" id="homeCards"></div>
      </div>

      <div class="section">
        <div class="two-col grid">
          <div>
            <div class="section-title"><span>📰 Tin tức & Sự kiện</span></div>
            <div class="card" id="homeNews"><div class="muted" style="padding:8px">Đang tải…</div></div>
            <div class="row" style="justify-content:center;margin-top:14px">
              <button class="btn sec small" onclick="go('news')">Xem tất cả tin tức →</button>
            </div>
          </div>
          <div>
            <div class="section-title"><span>🏆 Cao thủ</span></div>
            <div class="card" id="homeTop"><div class="muted" style="padding:8px">Đang tải…</div></div>
          </div>
        </div>
      </div>`;

    const c = document.getElementById('homeCards');
    [['⚔️', 'Combat lượt & real-time', 'Đánh quái, PK, đấu trường, chiến tranh bang hội.'],
     ['🛡️', 'Trang bị & kỹ năng', 'Dữ liệu vật phẩm, kỹ năng, rơi đồ gốc từ game.'],
     ['🤝', 'Xã hội phong phú', 'Bang hội, tổ đội, bạn bè, chợ giao dịch, hòm thư.'],
     ['🐾', 'Pet & hộ tống', 'Thú cưng đi theo, nhiệm vụ tiêu xa, săn boss.']]
      .forEach(([i, h, b]) => c.appendChild(el(
        `<div class="card feature"><div class="fi">${i}</div><h3>${h}</h3><div class="muted">${b}</div></div>`)));

    // Live server stats + previews (best-effort; ignore failures).
    try {
      const s = await api('/status');
      document.getElementById('srvOnline').textContent = s.online;
      document.getElementById('srvState').textContent = s.up ? '● Hoạt động' : '● Bảo trì';
    } catch (e) { document.getElementById('srvState').textContent = '● Offline'; }
    try {
      const { top } = await api('/leaderboard');
      document.getElementById('srvTop').textContent = top.length ? esc(top[0].username) : '—';
      const ht = document.getElementById('homeTop');
      ht.innerHTML = '';
      if (!top.length) ht.innerHTML = '<div class="muted" style="padding:8px">Chưa có dữ liệu.</div>';
      top.slice(0, 8).forEach((r, i) => ht.appendChild(el(
        `<div class="news-item" style="padding:9px 12px">
           <div class="badge" style="width:34px;height:34px;font-size:15px;color:var(--gold)">${['🥇','🥈','🥉'][i] || (i + 1)}</div>
           <div style="flex:1"><div class="nt">${esc(r.username)}</div>
             <div class="nd">Cấp ${r.level} · ${r.gold} vàng</div></div></div>`)));
    } catch (e) { /* ignore */ }
    try {
      const { news } = await api('/news');
      const hn = document.getElementById('homeNews');
      hn.innerHTML = '';
      if (!news.length) hn.innerHTML = '<div class="muted" style="padding:8px">Chưa có tin tức.</div>';
      news.slice(0, 5).forEach(n => hn.appendChild(el(
        `<div class="news-item">
           <div class="badge ${n.type === 'event' ? 'event' : ''}">${n.type === 'event' ? '🎉' : '📰'}</div>
           <div style="flex:1"><div class="nt">${esc(n.title)}</div>
             <div class="nd">${new Date(n.date).toLocaleString('vi')}</div>
             <div class="nb">${esc((n.body || '').slice(0, 120))}${(n.body || '').length > 120 ? '…' : ''}</div></div></div>`)));
    } catch (e) { /* ignore */ }
  },

  async news() {
    app().innerHTML = `<div class="container"><h2>Tin tức & Sự kiện</h2><div id="list" class="grid"></div></div>`;
    const { news } = await api('/news');
    const list = document.getElementById('list');
    if (!news.length) list.innerHTML = '<p class="muted">Chưa có tin tức.</p>';
    news.forEach(n => {
      const cd = n.type === 'event' && n.endAt ? `<div class="tag" data-end="${n.endAt}" style="margin-top:6px">⏳ …</div>` : '';
      list.appendChild(el(`<div class="card">
        <h3>${n.type === 'event' ? '🎉 ' : '📰 '}${esc(n.title)}</h3>
        <div class="muted" style="font-size:12px">${new Date(n.date).toLocaleString('vi')}</div>
        <p style="white-space:pre-wrap">${esc(n.body)}</p>${cd}</div>`));
    });
    tickCountdowns();
  },

  async leaderboard() {
    app().innerHTML = `<div class="container"><h2>Bảng xếp hạng</h2><div class="card"><table id="t">
      <tr><th>#</th><th>Nhân vật</th><th>Cấp</th><th>Vàng</th></tr></table></div></div>`;
    const { top } = await api('/leaderboard');
    const t = document.getElementById('t');
    top.forEach(r => t.appendChild(el(`<tr><td>${r.rank}</td><td>${esc(r.username)}</td>
      <td>${r.level}</td><td>${r.gold}</td></tr>`)));
    if (!top.length) t.appendChild(el('<tr><td colspan="4" class="muted">Chưa có dữ liệu.</td></tr>'));
  },

  async shop() {
    app().innerHTML = `<div class="container"><h2>Webshop</h2>
      <p class="muted">Mua bằng số dư (nạp thẻ). Vật phẩm gửi vào hòm thư trong game.</p>
      <div class="grid cards" id="g"></div></div>`;
    const { products } = await api('/shop');
    const g = document.getElementById('g');
    if (!products.length) g.innerHTML = '<p class="muted">Cửa hàng trống.</p>';
    products.forEach(p => {
      const c = el(`<div class="card row" style="justify-content:space-between">
        <div class="row"><img class="icon" src="${iconUrl(p.icon)}"/>
          <div><b>${esc(p.name)}</b><div class="muted">${esc(p.itemName)} x${p.count}</div></div></div>
        <div style="text-align:right"><div class="tag">${p.price} 💎</div><br/>
          <button class="btn small" style="margin-top:6px">Mua</button></div>`);
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
    app().innerHTML = `<div class="container"><h2>Nạp Xu qua SePay (chuyển khoản ngân hàng)</h2>
      <p class="muted">Chọn gói → quét QR / chuyển khoản đúng nội dung → hệ thống tự cộng Xu.
        Vào game gõ lệnh <b>convert</b> (hoặc nút Đổi) để đổi <b>Xu → Tiền nạp</b> trong game.</p>
      <div class="grid cards" id="packs"></div></div>`;
    const { packages, sepay } = await api('/packages');
    const g = document.getElementById('packs');
    if (!sepay.enabled) {
      g.innerHTML = '<div class="card">Cổng nạp SePay chưa được bật. Vui lòng liên hệ quản trị viên.</div>';
      return;
    }
    if (!packages.length) g.innerHTML = '<div class="card muted">Chưa có gói nạp.</div>';
    packages.forEach(p => {
      const c = el(`<div class="card">
        <h3>${esc(p.name)}</h3>
        <div class="muted">${p.priceVnd.toLocaleString('vi')} đ</div>
        <div style="font-size:22px;color:var(--gold);font-weight:800">${p.xu + p.bonus} Xu</div>
        ${p.bonus ? `<div class="tag on">+${p.bonus} thưởng</div>` : ''}
        <button class="btn small" style="margin-top:10px">Nạp gói này</button></div>`);
      c.querySelector('button').onclick = () => startOrder(p.id);
      g.appendChild(c);
    });
  },

  giftcode() {
    if (!T.token) return showLogin(false);
    app().innerHTML = `<div class="container"><h2>Nhập Giftcode</h2>
      <div class="card" style="max-width:460px">
        <label>Mã quà tặng</label><input id="code" placeholder="VD: TANTHU2024"/>
        <button class="btn" id="go" style="margin-top:14px">Nhận quà</button>
      </div></div>`;
    document.getElementById('go').onclick = async () => {
      try { const r = await api('/giftcode', 'POST', { code: document.getElementById('code').value });
        toast(r.message); } catch (e) { toast(e.message); }
    };
  },

  async profile() {
    if (!T.token) return showLogin(false);
    const me = await api('/me');
    app().innerHTML = `<div class="container"><h2>Trang cá nhân</h2>
      <div class="grid cards">
        <div class="card"><h3>Thông tin</h3>
          <p>Tài khoản: <b>${esc(me.username)}</b> ${me.role === 'ADMIN' ? '<span class="tag admin">ADMIN</span>' : ''}</p>
          <p>Số dư Xu (web): <b>${me.balance}</b> 💎</p>
          <p>Tiền nạp trong game: <b>${me.coin ?? 0}</b></p>
          <p>Vàng trong game: <b>${me.gold}</b></p>
          <p class="muted" style="font-size:13px">Đổi Xu → Tiền nạp: đăng nhập game và gõ lệnh <b>convert &lt;số xu&gt;</b>.</p>
          <p>Cấp độ: <b>${me.level}</b></p>
          <p>Trạng thái: ${me.online ? '<span class="tag on">Đang online</span>' : '<span class="tag off">Offline</span>'}</p>
        </div>
        <div class="card"><h3>Đổi mật khẩu</h3>
          <label>Mật khẩu cũ</label><input id="op" type="password"/>
          <label>Mật khẩu mới</label><input id="np" type="password"/>
          <button class="btn" id="cp" style="margin-top:14px">Đổi mật khẩu</button>
        </div>
      </div></div>`;
    document.getElementById('cp').onclick = async () => {
      try { await api('/auth/change-password', 'POST',
        { oldPassword: document.getElementById('op').value, newPassword: document.getElementById('np').value });
        toast('Đổi mật khẩu thành công!'); } catch (e) { toast(e.message); }
    };
    // Transaction history
    const { transactions } = await api('/me/transactions');
    const tx = el(`<div class="card" style="grid-column:1/-1"><h3>Lịch sử giao dịch</h3>
      <table id="txt"><tr><th>Thời gian</th><th>Loại</th><th>Chi tiết</th><th>Số lượng</th></tr></table></div>`);
    app().querySelector('.cards').appendChild(tx);
    const txt = tx.querySelector('#txt');
    if (!transactions.length) txt.appendChild(el('<tr><td colspan="4" class="muted">Chưa có giao dịch.</td></tr>'));
    transactions.forEach(t => txt.appendChild(el(`<tr>
      <td class="muted">${new Date(t.time).toLocaleString('vi')}</td>
      <td><span class="tag">${esc(t.type)}</span></td><td>${esc(t.detail)}</td>
      <td style="color:${t.amount < 0 ? 'var(--red)' : 'var(--green)'}">${t.amount > 0 ? '+' : ''}${t.amount} ${esc(t.currency)}</td></tr>`)));
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
      e.textContent = '⏳ Còn lại: ' + fmtDur(Number(e.dataset.end) - now);
    });
  };
  upd(); _cdTimer = setInterval(upd, 1000);
}

let _pollTimer = null;
async function startOrder(packageId) {
  try {
    const o = await api('/topup/order', 'POST', { packageId });
    modal(`
      <h3>Chuyển khoản để nhận ${o.xu} Xu</h3>
      <div style="text-align:center"><img src="${esc(o.qrUrl)}" alt="QR" style="width:230px;max-width:100%;background:#fff;border-radius:10px"/></div>
      <table style="margin-top:10px">
        <tr><th>Ngân hàng</th><td>${esc(o.bankCode)}</td></tr>
        <tr><th>Số tài khoản</th><td><b>${esc(o.accountNumber)}</b></td></tr>
        <tr><th>Chủ tài khoản</th><td>${esc(o.accountHolder)}</td></tr>
        <tr><th>Số tiền</th><td><b>${o.amountVnd.toLocaleString('vi')} đ</b></td></tr>
        <tr><th>Nội dung CK</th><td><b style="color:var(--gold)">${esc(o.content)}</b></td></tr>
      </table>
      <p class="muted" id="ost">⏳ Đang chờ thanh toán... (tự cộng Xu khi nhận được tiền)</p>
      <div class="row"><button class="btn sec" onclick="stopPoll();closeModal()">Đóng</button></div>`);
    if (_pollTimer) clearInterval(_pollTimer);
    _pollTimer = setInterval(async () => {
      try {
        const s = await api('/topup/order/' + o.orderId);
        if (s.status === 'paid') {
          stopPoll();
          document.getElementById('ost').innerHTML = '✅ Đã nhận thanh toán! Số dư: <b>' + s.balance + ' Xu</b>';
          toast('Nạp thành công +' + s.xu + ' Xu!');
        }
      } catch (e) { /* keep polling */ }
    }, 3000);
  } catch (e) { toast(e.message); }
}
function stopPoll() { if (_pollTimer) { clearInterval(_pollTimer); _pollTimer = null; } }
window.startOrder = startOrder; window.stopPoll = stopPoll;

function router() {
  stopPoll();
  const view = (location.hash || '#home').slice(1);
  setActive(view);
  (views[view] || views.home)();
}
window.addEventListener('hashchange', router);
document.querySelectorAll('#nav a').forEach(a => a.addEventListener('click', () => go(a.dataset.view)));
window.go = go; window.closeModal = closeModal;
renderUser();
router();
