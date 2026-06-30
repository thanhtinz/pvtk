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
  home() {
    app().innerHTML = `
      <div class="hero">
        <h1>Phong Vân Online</h1>
        <p>Bản dựng lại đa nền tảng (PC · Android · iOS · Java) của tựa game kiếm hiệp huyền thoại.
           Đăng nhập, lập bang, chinh chiến và trở thành đệ nhất cao thủ!</p>
        <div class="row" style="justify-content:center;margin-top:22px">
          <button class="btn" onclick="go('topup')">Nạp thẻ</button>
          <button class="btn sec" onclick="go('shop')">Vào Webshop</button>
        </div>
      </div>
      <div class="container grid cards" id="homeCards"></div>`;
    const c = document.getElementById('homeCards');
    [['⚔ Combat lượt & real-time', 'Đánh quái, PK, đấu trường, chiến tranh bang.'],
     ['🛡 Trang bị & kỹ năng', 'Dữ liệu vật phẩm/kỹ năng gốc từ game.'],
     ['🤝 Xã hội', 'Bang hội, tổ đội, bạn bè, chợ giao dịch, thư.'],
     ['🐾 Pet & hộ tống', 'Thú cưng đi theo, nhiệm vụ tiêu xa, boss.']]
      .forEach(([h, b]) => c.appendChild(el(`<div class="card"><h3>${h}</h3><div class="muted">${b}</div></div>`)));
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
