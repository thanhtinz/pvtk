// ---- helpers (mirror app.js) ----
const TOKEN = localStorage.getItem('pvtk_token');
const ROLE = localStorage.getItem('pvtk_role');
async function api(path, method = 'GET', body) {
  const h = { 'Content-Type': 'application/json' };
  if (TOKEN) h['Authorization'] = 'Bearer ' + TOKEN;
  const r = await fetch('/api' + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data.error || ('HTTP ' + r.status));
  return data;
}
function toast(m) { const t = document.getElementById('toast'); t.textContent = m; t.classList.add('show'); setTimeout(() => t.classList.remove('show'), 2600); }
// <template> so table fragments (<tr>/<td>) parse correctly (a <div> drops them).
function el(h) { const t = document.createElement('template'); t.innerHTML = h.trim(); return t.content.firstChild; }
function esc(s) { return String(s ?? '').replace(/[&<>"]/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;' }[c])); }
function iconUrl(id) { return `/api/items/${id}/icon.svg`; }
function modal(h) {
  const root = document.getElementById('modal-root');
  const bg = el(`<div class="modal-bg"><div class="modal">${h}</div></div>`);
  bg.addEventListener('click', e => { if (e.target === bg) root.innerHTML = ''; });
  root.innerHTML = ''; root.appendChild(bg); return bg;
}
function closeModal() { document.getElementById('modal-root').innerHTML = ''; }
window.closeModal = closeModal;

if (!TOKEN || ROLE !== 'ADMIN') { location.href = '/'; }
document.getElementById('logout').onclick = () => { localStorage.clear(); location.href = '/'; };

// ---- reusable item picker (search + multi-select + icons) ----
// onDone(selected) -> selected: [{itemId, name, icon, count}]
function openItemPicker(onDone, multi = true) {
  modal(`
    <h3>Chọn vật phẩm</h3>
    <input id="q" placeholder="Tìm theo tên hoặc ID..."/>
    <div class="chips" id="sel" style="margin:10px 0"></div>
    <div class="itempick" id="list"></div>
    <div class="row" style="margin-top:14px">
      <button class="btn" id="ok">Xong</button>
      <button class="btn sec" onclick="closeModal()">Hủy</button>
    </div>`);
  const selected = new Map(); // itemId -> {itemId,name,icon,count}
  const listEl = document.getElementById('list');
  const selEl = document.getElementById('sel');
  function renderSel() {
    selEl.innerHTML = '';
    selected.forEach(s => {
      const c = el(`<span class="chip"><img class="icon" style="width:20px;height:20px" src="${iconUrl(s.icon)}"/>
        ${esc(s.name)} <b>x<input type="number" value="${s.count}" min="1" style="width:46px;padding:2px"/></b> ✕</span>`);
      c.querySelector('input').onchange = e => s.count = Math.max(1, Number(e.target.value));
      c.onclick = e => { if (e.target.tagName !== 'INPUT') { selected.delete(s.itemId); refresh(); } };
      selEl.appendChild(c);
    });
  }
  async function load(q) {
    const { items } = await api('/items?q=' + encodeURIComponent(q || ''));
    listEl.innerHTML = '';
    items.forEach(it => {
      const node = el(`<div class="it ${selected.has(it.id) ? 'sel' : ''}">
        <img class="icon" src="${iconUrl(it.icon)}"/>
        <div style="font-size:13px">${esc(it.name)}<div class="muted">#${it.id}</div></div></div>`);
      node.onclick = () => {
        if (selected.has(it.id)) selected.delete(it.id);
        else { if (!multi) selected.clear(); selected.set(it.id, { itemId: it.id, name: it.name, icon: it.icon, count: 1 }); }
        refresh();
      };
      listEl.appendChild(node);
    });
  }
  function refresh() { renderSel(); document.querySelectorAll('.itempick .it').forEach(n => {}); load(document.getElementById('q').value); }
  document.getElementById('q').oninput = e => load(e.target.value);
  document.getElementById('ok').onclick = () => { onDone([...selected.values()]); closeModal(); };
  load('');
}
window.openItemPicker = openItemPicker;

// ---- tabs ----
const app = () => document.getElementById('app');
const tabs = {
  async dashboard() {
    const s = await api('/admin/stats');
    app().innerHTML = `<div class="stat-tiles">
      ${card('Đang online', s.online, '🟢')}${card('Tài khoản', s.accounts, '👥')}${card('Vật phẩm', s.items, '🎒')}
      ${card('Quái', s.monsters, '🐲')}${card('Giftcode', s.giftcodes, '🎁')}${card('Webshop', s.products, '🛒')}</div>
      <div class="card" style="margin-top:18px"><h3>⚡ Thao tác nhanh</h3>
        <button class="btn red" id="rb">Reset Boss (respawn toàn bộ)</button></div>`;
    document.getElementById('rb').onclick = async () => { const r = await api('/admin/reset-boss', 'POST', {}); toast('Đã respawn ' + r.respawned + ' quái'); };
  },

  async users() {
    app().innerHTML = `<h2>Quản lý người chơi</h2>
      <div class="row"><input id="q" placeholder="Tìm tài khoản..." style="max-width:300px"/></div>
      <div class="card" style="margin-top:12px"><table id="t"><tr>
        <th>Tài khoản</th><th>Quyền</th><th>Cấp</th><th>Vàng</th><th>Số dư</th><th>TT</th><th>Hành động</th></tr></table></div>`;
    const draw = async () => {
      const { users } = await api('/admin/users?q=' + encodeURIComponent(document.getElementById('q').value || ''));
      const t = document.getElementById('t');
      t.querySelectorAll('tr:not(:first-child)').forEach(r => r.remove());
      users.forEach(u => {
        const tr = el(`<tr>
          <td>${esc(u.username)}</td>
          <td>${u.role === 'ADMIN' ? '<span class="tag admin">ADMIN</span>' : 'USER'}</td>
          <td>${u.level}</td><td>${u.gold}</td><td>${u.balance}</td>
          <td>${u.online ? '<span class="tag on">ON</span>' : '<span class="tag off">OFF</span>'}</td>
          <td class="row"></td></tr>`);
        const act = tr.querySelector('td:last-child');
        addBtn(act, '💰 Kinh tế', () => economyDialog(u, draw));
        addBtn(act, u.banned ? 'Mở khóa' : 'Khóa', async () => { await api('/admin/users/ban', 'POST', { username: u.username, banned: !u.banned }); draw(); });
        addBtn(act, u.role === 'ADMIN' ? 'Bỏ admin' : 'Cấp admin', async () => { await api('/admin/users/role', 'POST', { username: u.username, role: u.role === 'ADMIN' ? 'USER' : 'ADMIN' }); draw(); });
        addBtn(act, '🔑', async () => { const np = prompt('Mật khẩu mới cho ' + u.username); if (np) { await api('/admin/users/reset-password', 'POST', { username: u.username, newPassword: np }); toast('Đã đổi mật khẩu'); } });
        t.appendChild(tr);
      });
    };
    document.getElementById('q').oninput = draw; draw();
  },

  mail() {
    let items = [];
    app().innerHTML = `<h2>Gửi vật phẩm qua thư</h2>
      <div class="card" style="max-width:640px">
        <label>Người nhận (tên nhân vật/tài khoản)</label><input id="to"/>
        <label>Tiêu đề</label><input id="subj" value="Quà từ Admin"/>
        <label>Nội dung thư</label><textarea id="body" rows="3">Chúc bạn chơi game vui vẻ!</textarea>
        <label>Vàng kèm theo</label><input id="gold" type="number" value="0"/>
        <label>Vật phẩm (chọn nhiều)</label>
        <div class="chips" id="chips"><span class="muted">Chưa chọn</span></div>
        <button class="btn sec small" id="pick" style="margin-top:8px">+ Chọn vật phẩm</button>
        <div style="margin-top:16px"><button class="btn" id="send">📨 Gửi thư</button></div>
      </div>`;
    const drawChips = () => {
      const c = document.getElementById('chips');
      c.innerHTML = items.length ? '' : '<span class="muted">Chưa chọn</span>';
      items.forEach(s => c.appendChild(el(`<span class="chip"><img class="icon" style="width:20px;height:20px" src="${iconUrl(s.icon)}"/>${esc(s.name)} <b>x${s.count}</b></span>`)));
    };
    document.getElementById('pick').onclick = () => openItemPicker(sel => { items = sel; drawChips(); });
    document.getElementById('send').onclick = async () => {
      try {
        await api('/admin/mail', 'POST', {
          toUser: document.getElementById('to').value,
          subject: document.getElementById('subj').value,
          body: document.getElementById('body').value,
          gold: Number(document.getElementById('gold').value),
          items: items.map(i => ({ itemId: i.itemId, count: i.count })),
        });
        toast('Đã gửi thư!'); items = []; drawChips();
      } catch (e) { toast(e.message); }
    };
  },

  async items() {
    app().innerHTML = `<h2>Vật phẩm (item.txt)</h2>
      <input id="q" placeholder="Tìm vật phẩm..." style="max-width:300px"/>
      <div class="grid cards" id="g" style="margin-top:12px"></div>`;
    const draw = async () => {
      const { items } = await api('/items?q=' + encodeURIComponent(document.getElementById('q').value || ''));
      const g = document.getElementById('g'); g.innerHTML = '';
      items.forEach(it => g.appendChild(el(`<div class="card row"><img class="icon" src="${iconUrl(it.icon)}"/>
        <div><b>${esc(it.name)}</b><div class="muted">#${it.id} · type ${it.type} · ${it.price}💰</div></div></div>`)));
    };
    document.getElementById('q').oninput = draw; draw();
  },

  async monsters() {
    const { monsters } = await api('/admin/monsters');
    app().innerHTML = `<h2>Quái / Boss</h2><div class="card"><table><tr>
      <th>ID</th><th>Tên</th><th>Cấp</th><th>HP</th><th>EXP</th><th>Vàng</th></tr>
      ${monsters.map(m => `<tr><td>${m.id}</td><td>${esc(m.name)}</td><td>${m.level}</td>
        <td>${m.hpMax}</td><td>${m.rewardExp}</td><td>${m.rewardGold}</td></tr>`).join('')}</table></div>
      <button class="btn red" id="rb" style="margin-top:12px">Reset Boss</button>`;
    document.getElementById('rb').onclick = async () => { const r = await api('/admin/reset-boss', 'POST', {}); toast('Respawn ' + r.respawned); };
  },

  async maps() {
    const { maps } = await api('/admin/maps');
    app().innerHTML = `<h2>Máy chủ / Bản đồ</h2><div class="card"><table><tr>
      <th>ID</th><th>Tên</th><th>Kích thước</th><th>Người chơi</th></tr>
      ${maps.map(m => `<tr><td>${m.id}</td><td>${esc(m.name)}</td><td>${m.width}×${m.height}</td><td>${m.players}</td></tr>`).join('')}</table></div>`;
  },

  async giftcodes() {
    const { giftcodes } = await api('/admin/giftcodes');
    app().innerHTML = `<h2>Giftcode</h2>
      <button class="btn" id="new">+ Tạo giftcode</button>
      <div class="card" style="margin-top:12px"><table id="t"><tr>
        <th>Mã</th><th>Vàng</th><th>Số dư</th><th>Vật phẩm</th><th>Lượt</th><th></th></tr>
      ${giftcodes.map(g => `<tr><td><b>${esc(g.code)}</b></td><td>${g.rewardGold}</td><td>${g.rewardBalance}</td>
        <td>${g.items.length}</td><td>${g.used}/${g.maxUses || '∞'}</td>
        <td><button class="btn small red" data-c="${esc(g.code)}">Xóa</button></td></tr>`).join('')}</table></div>`;
    document.getElementById('new').onclick = () => giftcodeDialog();
    app().querySelectorAll('button[data-c]').forEach(b => b.onclick = async () => { await api('/admin/giftcodes/' + encodeURIComponent(b.dataset.c), 'DELETE'); tabs.giftcodes(); });
  },

  async products() {
    const { products } = await api('/shop');
    app().innerHTML = `<h2>Webshop</h2>
      <button class="btn" id="new">+ Thêm sản phẩm</button>
      <div class="grid cards" id="g" style="margin-top:12px"></div>`;
    const g = document.getElementById('g');
    products.forEach(p => {
      const c = el(`<div class="card row" style="justify-content:space-between">
        <div class="row"><img class="icon" src="${iconUrl(p.icon)}"/><div><b>${esc(p.name)}</b>
          <div class="muted">${esc(p.itemName)} x${p.count} · ${p.price}💎</div></div></div>
        <button class="btn small red">Xóa</button></div>`);
      c.querySelector('button').onclick = async () => { await api('/admin/products/' + p.id, 'DELETE'); tabs.products(); };
      g.appendChild(c);
    });
    document.getElementById('new').onclick = () => productDialog();
  },

  async news() {
    const { news } = await api('/news');
    app().innerHTML = `<h2>Tin tức / Sự kiện</h2>
      <button class="btn" id="new">+ Viết bài</button>
      <div class="grid" style="margin-top:12px" id="l"></div>`;
    const l = document.getElementById('l');
    news.forEach(n => {
      const c = el(`<div class="card"><div class="row" style="justify-content:space-between">
        <b>${n.type === 'event' ? '🎉' : '📰'} ${esc(n.title)}</b>
        <button class="btn small red">Xóa</button></div><div class="muted" style="white-space:pre-wrap">${esc(n.body)}</div></div>`);
      c.querySelector('button').onclick = async () => { await api('/admin/news/' + n.id, 'DELETE'); tabs.news(); };
      l.appendChild(c);
    });
    document.getElementById('new').onclick = () => newsDialog();
  },

  async online() {
    const { players } = await api('/admin/online');
    app().innerHTML = `<h2>Người chơi đang online (${players.length})</h2><div class="card"><table><tr>
      <th>Nhân vật</th><th>Bản đồ</th><th>Cấp</th><th>Vàng</th><th>Vị trí</th></tr>
      ${players.map(p => `<tr><td>${esc(p.name)}</td><td>${esc(p.map)}</td><td>${p.level}</td>
        <td>${p.gold}</td><td class="muted">(${p.x},${p.y})</td></tr>`).join('')}
      ${players.length ? '' : '<tr><td colspan="5" class="muted">Không có ai online.</td></tr>'}</table></div>`;
  },

  announce() {
    app().innerHTML = `<h2>Gửi thông báo toàn server</h2>
      <div class="card" style="max-width:560px">
        <p class="muted">Tin nhắn sẽ hiện ở kênh Hệ thống cho mọi người đang chơi.</p>
        <label>Nội dung</label><textarea id="msg" rows="3" placeholder="VD: Bảo trì lúc 22h, sự kiện x2 EXP cuối tuần!"></textarea>
        <button class="btn" id="go" style="margin-top:14px">📢 Phát thông báo</button>
      </div>`;
    document.getElementById('go').onclick = async () => {
      const r = await api('/admin/announce', 'POST', { message: document.getElementById('msg').value });
      toast('Đã gửi tới ' + r.online + ' người chơi online');
    };
  },

  async market() {
    const { listings } = await api('/admin/market');
    app().innerHTML = `<h2>Chợ giao dịch ingame (${listings.length})</h2>
      <div class="card"><table id="t"><tr>
        <th>ID</th><th>Người bán</th><th>Vật phẩm</th><th>SL</th><th>Giá</th><th></th></tr></table></div>`;
    const t = document.getElementById('t');
    if (!listings.length) t.appendChild(el('<tr><td colspan="6" class="muted">Chợ trống.</td></tr>'));
    listings.forEach(l => {
      const tr = el(`<tr><td>${l.listingId}</td><td>${esc(l.sellerName)}</td>
        <td><img class="icon" style="width:22px;height:22px" src="${iconUrl(l.itemId)}"/> ${esc(l.itemName)}</td>
        <td>${l.count}</td><td>${l.price}💰</td><td><button class="btn small red">Gỡ</button></td></tr>`);
      tr.querySelector('button').onclick = async () => { await api('/admin/market/remove', 'POST', { listingId: l.listingId }); tabs.market(); };
      t.appendChild(tr);
    });
  },

  async transactions() {
    const { transactions } = await api('/admin/transactions');
    app().innerHTML = `<h2>Lịch sử giao dịch (toàn server)</h2><div class="card"><table><tr>
      <th>Thời gian</th><th>Tài khoản</th><th>Loại</th><th>Chi tiết</th><th>Số lượng</th></tr>
      ${transactions.map(t => `<tr><td class="muted">${new Date(t.time).toLocaleString('vi')}</td>
        <td>${esc(t.user)}</td><td><span class="tag">${esc(t.type)}</span></td><td>${esc(t.detail)}</td>
        <td style="color:${t.amount < 0 ? 'var(--red)' : 'var(--green)'}">${t.amount > 0 ? '+' : ''}${t.amount} ${esc(t.currency)}</td></tr>`).join('')}
      ${transactions.length ? '' : '<tr><td colspan="5" class="muted">Chưa có giao dịch.</td></tr>'}</table></div>`;
  },

  async sepay() {
    const { sepay } = await api('/admin/sepay');
    app().innerHTML = `<h2>Cổng nạp SePay</h2>
      <div class="card" style="max-width:560px">
        <p class="muted">SePay theo dõi biến động số dư ngân hàng và gọi webhook tới
          <code>/api/sepay/webhook</code>. Khai báo URL này + Apikey trong trang quản trị SePay.</p>
        <label><input type="checkbox" id="en" ${sepay.enabled ? 'checked' : ''} style="width:auto"> Bật cổng nạp</label>
        <label>Mã ngân hàng (bankCode)</label><input id="bank" value="${esc(sepay.bankCode)}"/>
        <label>Số tài khoản</label><input id="acc" value="${esc(sepay.accountNumber)}"/>
        <label>Chủ tài khoản</label><input id="holder" value="${esc(sepay.accountHolder)}"/>
        <label>Nội dung CK (prefix)</label><input id="prefix" value="${esc(sepay.prefix)}"/>
        <label>Webhook API Key (Authorization: Apikey ...)</label><input id="key" value="${esc(sepay.apiKey)}"/>
        <button class="btn" id="save" style="margin-top:14px">Lưu cấu hình</button>
      </div>`;
    document.getElementById('save').onclick = async () => {
      await api('/admin/sepay', 'POST', {
        enabled: document.getElementById('en').checked,
        bankCode: document.getElementById('bank').value,
        accountNumber: document.getElementById('acc').value,
        accountHolder: document.getElementById('holder').value,
        prefix: document.getElementById('prefix').value,
        apiKey: document.getElementById('key').value,
      });
      toast('Đã lưu cấu hình SePay');
    };
  },

  async packages() {
    const { packages } = await api('/admin/packages');
    app().innerHTML = `<h2>Gói nạp</h2><button class="btn" id="new">+ Thêm gói</button>
      <div class="card" style="margin-top:12px"><table id="t"><tr>
        <th>Tên</th><th>Giá (VND)</th><th>Xu</th><th>Bonus</th><th>Bật</th><th></th></tr>
      ${packages.map(p => `<tr><td>${esc(p.name)}</td><td>${p.priceVnd.toLocaleString('vi')}</td>
        <td>${p.xu}</td><td>${p.bonus}</td><td>${p.enabled ? '✅' : '❌'}</td>
        <td><button class="btn small red" data-id="${p.id}">Xóa</button></td></tr>`).join('')}</table></div>`;
    document.getElementById('new').onclick = () => packageDialog();
    app().querySelectorAll('button[data-id]').forEach(b => b.onclick = async () => { await api('/admin/packages/' + b.dataset.id, 'DELETE'); tabs.packages(); });
  },

  async orders() {
    const { orders } = await api('/admin/orders');
    app().innerHTML = `<h2>Đơn nạp</h2><div class="card"><table><tr>
      <th>Mã</th><th>Tài khoản</th><th>Số tiền</th><th>Xu</th><th>Trạng thái</th><th>Thời gian</th></tr>
      ${orders.map(o => `<tr><td><b>${esc(o.code)}</b></td><td>${esc(o.user)}</td>
        <td>${o.amountVnd.toLocaleString('vi')}đ</td><td>${o.xu}</td>
        <td><span class="tag ${o.status === 'paid' ? 'on' : 'off'}">${o.status}</span></td>
        <td class="muted">${new Date(o.createdAt).toLocaleString('vi')}</td></tr>`).join('')}
      ${orders.length ? '' : '<tr><td colspan="6" class="muted">Chưa có đơn.</td></tr>'}</table></div>`;
  },

  async economy() {
    const e = await api('/admin/economy');
    app().innerHTML = `<h2>Kinh tế toàn server</h2><div class="grid cards">
      ${card('Tổng vàng', e.totalGold)}${card('Tổng số dư (💎)', e.totalBalance)}${card('Tài khoản', e.accounts)}</div>
      <p class="muted" style="margin-top:12px">Điều chỉnh tiền từng người ở tab <b>Người chơi → 💰 Kinh tế</b>.</p>`;
  },
};

function card(label, val, icon) { return `<div class="stat-tile"><span class="ic">${icon || ''}</span><div class="k">${label}</div><div class="v">${val}</div></div>`; }
function addBtn(parent, label, fn) { const b = el(`<button class="btn small sec">${label}</button>`); b.onclick = fn; parent.appendChild(b); }

function economyDialog(u, after) {
  modal(`<h3>Kinh tế: ${esc(u.username)}</h3>
    <p class="muted">Vàng: ${u.gold} · Số dư: ${u.balance}💎. Nhập số âm để trừ.</p>
    <label>+/- Vàng</label><input id="dg" type="number" value="0"/>
    <label>+/- Số dư (💎)</label><input id="db" type="number" value="0"/>
    <div class="row" style="margin-top:14px"><button class="btn" id="ok">Áp dụng</button>
      <button class="btn sec" onclick="closeModal()">Hủy</button></div>`);
  document.getElementById('ok').onclick = async () => {
    await api('/admin/users/economy', 'POST', { username: u.username,
      deltaGold: Number(document.getElementById('dg').value), deltaBalance: Number(document.getElementById('db').value) });
    toast('Đã cập nhật'); closeModal(); after && after();
  };
}

function giftcodeDialog() {
  let items = [];
  modal(`<h3>Tạo Giftcode</h3>
    <label>Mã</label><input id="code"/>
    <label>Thưởng vàng</label><input id="g" type="number" value="0"/>
    <label>Thưởng số dư (💎)</label><input id="b" type="number" value="0"/>
    <label>Số lượt dùng (0 = không giới hạn)</label><input id="m" type="number" value="100"/>
    <label>Vật phẩm</label><div class="chips" id="chips"><span class="muted">Chưa chọn</span></div>
    <button class="btn sec small" id="pick" style="margin-top:8px">+ Chọn vật phẩm</button>
    <div class="row" style="margin-top:14px"><button class="btn" id="ok">Lưu</button>
      <button class="btn sec" onclick="closeModal()">Hủy</button></div>`);
  const draw = () => { const c = document.getElementById('chips'); c.innerHTML = items.length ? '' : '<span class="muted">Chưa chọn</span>';
    items.forEach(s => c.appendChild(el(`<span class="chip"><img class="icon" style="width:20px;height:20px" src="${iconUrl(s.icon)}"/>${esc(s.name)} x${s.count}</span>`))); };
  document.getElementById('pick').onclick = () => openItemPicker(sel => { items = sel; draw(); });
  document.getElementById('ok').onclick = async () => {
    await api('/admin/giftcodes', 'POST', { code: document.getElementById('code').value,
      rewardGold: Number(document.getElementById('g').value), rewardBalance: Number(document.getElementById('b').value),
      maxUses: Number(document.getElementById('m').value), items: items.map(i => ({ itemId: i.itemId, count: i.count })) });
    toast('Đã lưu giftcode'); closeModal(); tabs.giftcodes();
  };
}

function productDialog() {
  let item = null;
  modal(`<h3>Thêm sản phẩm Webshop</h3>
    <label>Tên hiển thị</label><input id="name"/>
    <label>Vật phẩm</label><div class="chips" id="chips"><span class="muted">Chưa chọn</span></div>
    <button class="btn sec small" id="pick" style="margin-top:8px">+ Chọn vật phẩm</button>
    <label>Số lượng</label><input id="count" type="number" value="1"/>
    <label>Giá (💎)</label><input id="price" type="number" value="100"/>
    <div class="row" style="margin-top:14px"><button class="btn" id="ok">Lưu</button>
      <button class="btn sec" onclick="closeModal()">Hủy</button></div>`);
  document.getElementById('pick').onclick = () => openItemPicker(sel => {
    item = sel[0];
    document.getElementById('chips').innerHTML = item ? `<span class="chip"><img class="icon" style="width:20px;height:20px" src="${iconUrl(item.icon)}"/>${esc(item.name)}</span>` : '';
    if (item && !document.getElementById('name').value) document.getElementById('name').value = item.name;
  }, false);
  document.getElementById('ok').onclick = async () => {
    if (!item) return toast('Hãy chọn vật phẩm');
    await api('/admin/products', 'POST', { name: document.getElementById('name').value, itemId: item.itemId,
      count: Number(document.getElementById('count').value), price: Number(document.getElementById('price').value) });
    toast('Đã lưu'); closeModal(); tabs.products();
  };
}

function packageDialog() {
  modal(`<h3>Thêm gói nạp</h3>
    <label>Tên (để trống = tự đặt)</label><input id="name"/>
    <label>Giá chuyển khoản (VND)</label><input id="price" type="number" value="10000"/>
    <label>Xu nhận được</label><input id="xu" type="number" value="100"/>
    <label>Xu thưởng</label><input id="bonus" type="number" value="0"/>
    <div class="row" style="margin-top:14px"><button class="btn" id="ok">Lưu</button>
      <button class="btn sec" onclick="closeModal()">Hủy</button></div>`);
  document.getElementById('ok').onclick = async () => {
    await api('/admin/packages', 'POST', { name: document.getElementById('name').value,
      priceVnd: Number(document.getElementById('price').value), xu: Number(document.getElementById('xu').value),
      bonus: Number(document.getElementById('bonus').value) });
    toast('Đã lưu gói nạp'); closeModal(); tabs.packages();
  };
}

function newsDialog() {
  modal(`<h3>Viết bài</h3>
    <label>Loại</label><select id="type"><option value="news">Tin tức</option><option value="event">Sự kiện</option></select>
    <label>Tiêu đề</label><input id="title"/>
    <label>Nội dung</label><textarea id="body" rows="5"></textarea>
    <div class="row"><div style="flex:1"><label>Bắt đầu (sự kiện)</label><input id="start" type="datetime-local"/></div>
      <div style="flex:1"><label>Kết thúc (đếm ngược)</label><input id="end" type="datetime-local"/></div></div>
    <div class="row" style="margin-top:14px"><button class="btn" id="ok">Đăng</button>
      <button class="btn sec" onclick="closeModal()">Hủy</button></div>`);
  const ms = id => { const v = document.getElementById(id).value; return v ? new Date(v).getTime() : 0; };
  document.getElementById('ok').onclick = async () => {
    await api('/admin/news', 'POST', { type: document.getElementById('type').value,
      title: document.getElementById('title').value, body: document.getElementById('body').value,
      startAt: ms('start'), endAt: ms('end') });
    toast('Đã đăng'); closeModal(); tabs.news();
  };
}

const TAB_TITLES = {
  dashboard: 'Tổng quan', users: 'Người chơi', online: 'Đang online', economy: 'Kinh tế',
  items: 'Vật phẩm', monsters: 'Quái / Boss', maps: 'Máy chủ / Map', mail: 'Gửi vật phẩm',
  market: 'Chợ ingame', products: 'Webshop', sepay: 'Cổng nạp (SePay)', packages: 'Gói nạp',
  orders: 'Đơn nạp', news: 'Tin / Sự kiện', announce: 'Thông báo', giftcodes: 'Giftcode',
  transactions: 'Lịch sử giao dịch',
};
function show(tab) {
  document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('active', a.dataset.tab === tab));
  const title = document.getElementById('pageTitle');
  if (title) title.textContent = TAB_TITLES[tab] || 'Quản trị';
  document.getElementById('sidebar').classList.remove('open');
  (tabs[tab] || tabs.dashboard)().catch(e => toast(e.message));
}
document.querySelectorAll('#nav a').forEach(a => a.onclick = () => show(a.dataset.tab));

// admin identity + mobile sidebar toggle
const whoName = document.getElementById('whoName');
if (whoName) whoName.textContent = localStorage.getItem('pvtk_name') || 'admin';
const menuBtn = document.getElementById('menuBtn');
if (menuBtn) menuBtn.onclick = () => document.getElementById('sidebar').classList.toggle('open');

show('dashboard');
