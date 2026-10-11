/* ByteBazaar shared frontend library: API client, auth, header/footer, UI helpers. */
const BB = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  // "Remember me" unchecked => session-only login (cleared when the browser session ends)
  if (localStorage.getItem('bb_temp_login') === '1' && !sessionStorage.getItem('bb_alive')) { localStorage.removeItem('bb_token'); localStorage.removeItem('bb_user'); localStorage.removeItem('bb_temp_login'); }

  // ---------------------------------------------------------------- auth state
  const token = () => localStorage.getItem('bb_token');
  const user = () => { try { return JSON.parse(localStorage.getItem('bb_user') || 'null'); } catch { return null; } };
  const setAuth = (res) => { localStorage.setItem('bb_token', res.token); localStorage.setItem('bb_user', JSON.stringify(res.user)); };
  const clearAuth = () => { localStorage.removeItem('bb_token'); localStorage.removeItem('bb_user'); };
  const homeFor = role => role === 'ADMIN' ? '/admin/dashboard.html' : role === 'SELLER' ? '/seller/dashboard.html' : '/index.html';
  const logout = () => { api('/api/auth/logout', { method: 'POST' }).catch(() => {}); clearAuth(); location.href = '/index.html'; };
  const loginUrl = () => '/login.html?next=' + encodeURIComponent(location.pathname + location.search);

  /** Redirects to login if needed. roles = optional array of allowed roles. Returns true when the page may continue. */
  function requireAuth(roles) {
    const u = user();
    if (!token() || !u) { location.href = loginUrl(); return false; }
    if (roles && !roles.includes(u.role)) { location.href = homeFor(u.role); return false; }
    return true;
  }

  // ---------------------------------------------------------------- API
  async function api(path, { method = 'GET', body, form, raw } = {}) {
    const h = {}; const t = token();
    if (t) h.Authorization = 'Bearer ' + t;
    let b;
    if (form) b = form; else if (body !== undefined) { h['Content-Type'] = 'application/json'; b = JSON.stringify(body); }
    let r;
    try { r = await fetch(path, { method, headers: h, body: b }); }
    catch (e) { throw new Error('Cannot reach the ByteBazaar server. Is the backend running?'); }
    if (raw) return r;
    const txt = await r.text(); let data = null;
    try { data = txt ? JSON.parse(txt) : null; } catch { /* non-JSON */ }
    if (!r.ok) {
      if (r.status === 401 && t) { clearAuth(); location.href = loginUrl(); }
      if (r.status === 403 && data && data.blocked) { clearAuth(); location.href = '/login.html?blocked=1'; }
      const err = new Error((data && data.message) || ('Request failed (' + r.status + ')'));
      err.status = r.status; err.data = data; throw err;
    }
    return data;
  }

  // ---------------------------------------------------------------- formatting
  const money = n => '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  const dateOnly = d => d ? new Date(d).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '-';
  const timeOnly = d => d ? new Date(d).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: true }).toUpperCase() : '';
  const dt = d => d ? dateOnly(d) + ' — ' + timeOnly(d) : '-';
  const stars = (r, cnt) => {
    const f = Math.round((r || 0) * 2) / 2; let s = '';
    for (let i = 1; i <= 5; i++) s += `<i class="bi bi-star${f >= i ? '-fill' : (f >= i - .5 ? '-half' : '')}"></i>`;
    return `<span class="stars">${s}${cnt != null ? `<span class="cnt">(${Number(cnt).toLocaleString('en-IN')})</span>` : ''}</span>`;
  };
  const badge = s => `<span class="badge-st st-${esc(s)}">${esc(String(s || '').replace(/_/g, ' '))}</span>`;

  // placeholder product art (no external images needed)
  const EMOJI = { Clothing: '👕', Electronics: '💻', Toys: '🧸', Furniture: '🛋️', Beauty: '💄', 'Home Decor': '🪔', Sports: '🏏', Laptop: '💻', Mobile: '📱', Headphones: '🎧', Television: '📺', Smartwatch: '⌚', Camera: '📷', Accessories: '🖱️', Shoes: '👟', 'Fashion Accessories': '👜', 'Kids Toys': '🧸', 'Educational Toys': '🧩', Games: '🎲', 'Remote Control Toys': '🏎️', Sofa: '🛋️', Chair: '💺', Table: '🪑', Bed: '🛏️', Wardrobe: '🚪', Storage: '🗄️', Makeup: '💄', Skincare: '🧴', 'Hair Care': '💇', 'Personal Care': '🧼', Lighting: '💡', 'Wall Decor': '🖼️', 'Kitchen Decor': '🍽️', 'Decorative Items': '🏺', Cricket: '🏏', Football: '⚽', Fitness: '🧘', 'Gym Equipment': '🏋️', 'Sports Accessories': '🥤', "Men's Clothing": '👔', "Women's Clothing": '👗', 'Kids Clothing': '👶' };
  const PAL = [['#dbeafe', '#bfdbfe'], ['#fde68a', '#fcd34d'], ['#fbcfe8', '#f9a8d4'], ['#bbf7d0', '#86efac'], ['#ddd6fe', '#c4b5fd'], ['#fed7aa', '#fdba74'], ['#a5f3fc', '#67e8f9']];
  function ph(cat, parent, seed) {
    const e = EMOJI[cat] || EMOJI[parent] || '🛍️';
    const p = PAL[Math.abs([...String(seed || cat || 'x')].reduce((a, c) => a + c.charCodeAt(0), 0)) % PAL.length];
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${p[0]}"/><stop offset="1" stop-color="${p[1]}"/></linearGradient></defs><rect width="400" height="400" fill="url(#g)"/><text x="200" y="250" font-size="170" text-anchor="middle">${e}</text></svg>`;
    return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
  }
  const img = p => (p.images && p.images[0]) || ph(p.category, p.parentCategory, p.name);
  const src = (url, name) => url || ph(null, null, name);

  // ---------------------------------------------------------------- UI helpers
  function toast(msg, type = 'ok') {
    let w = $('.toast-wrap'); if (!w) { w = document.createElement('div'); w.className = 'toast-wrap'; document.body.appendChild(w); }
    const t = document.createElement('div'); t.className = 'bb-toast ' + (type === 'err' ? 'err' : type === 'info' ? '' : 'ok');
    t.innerHTML = `<i class="bi ${type === 'err' ? 'bi-exclamation-triangle-fill' : 'bi-check-circle-fill'}"></i><div>${esc(msg)}</div>`;
    w.appendChild(t); setTimeout(() => t.remove(), 3800);
  }
  const fail = e => toast(e.message || 'Something went wrong', 'err');
  const loader = el => { el.innerHTML = '<div class="loader"></div>'; };
  const empty = (el, emoji, title, sub, cta) => { el.innerHTML = `<div class="empty"><div class="em">${emoji}</div><h5 class="mt-2">${esc(title)}</h5><p>${sub || ''}</p>${cta || ''}</div>`; };
  const errorState = (el, e) => { el.innerHTML = `<div class="empty"><div class="em">⚠️</div><h5>We hit a snag</h5><p>${esc(e.message)}</p><button class="btn btn-navy" onclick="location.reload()">Try again</button></div>`; };

  function modal(title, body, { size = '', footer = '' } = {}) {
    const el = document.createElement('div'); el.className = 'modal fade'; el.tabIndex = -1;
    el.innerHTML = `<div class="modal-dialog modal-dialog-centered modal-dialog-scrollable ${size}"><div class="modal-content"><div class="modal-header"><h5 class="modal-title">${title}</h5><button class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body">${body}</div>${footer ? `<div class="modal-footer">${footer}</div>` : ''}</div></div>`;
    document.body.appendChild(el);
    const m = new bootstrap.Modal(el); el.addEventListener('hidden.bs.modal', () => { m.dispose(); el.remove(); }); m.show();
    return { el, m, close: () => m.hide() };
  }
  function confirmBox(msg, okText = 'Yes, continue') {
    return new Promise(res => {
      const mm = modal('Please confirm', `<p class="mb-0">${esc(msg)}</p>`, { footer: `<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-bb2" id="cfOk">${esc(okText)}</button>` });
      let done = false; $('#cfOk', mm.el).onclick = () => { done = true; mm.close(); res(true); };
      mm.el.addEventListener('hidden.bs.modal', () => { if (!done) res(false); });
    });
  }
  const spin = (btn, on, txt) => { if (!btn) return; if (on) { btn.dataset.t = btn.innerHTML; btn.disabled = true; btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>' + (txt || ''); } else { btn.disabled = false; btn.innerHTML = btn.dataset.t || btn.innerHTML; } };
  const qs = k => new URLSearchParams(location.search).get(k);

  async function download(url, filename) {
    try {
      const r = await api(url, { raw: true });
      if (!r.ok) { let m = 'Download failed'; try { m = (await r.json()).message || m; } catch { } throw new Error(m); }
      const b = await r.blob(); const a = document.createElement('a');
      a.href = URL.createObjectURL(b); a.download = filename; document.body.appendChild(a); a.click(); a.remove();
    } catch (e) { fail(e); }
  }

  // timeline of tracking events: [{status|title, eventTime, location, description}]
  function timeline(events, pendingLabels = []) {
    const done = events.map(e => `<li><div class="dot"><i class="bi bi-check-lg"></i></div><div class="t-title">${esc(String(e.status).replace(/_/g, ' '))}</div><div class="t-time">${dt(e.eventTime)}${e.location ? ' · ' + esc(e.location) : ''}</div><div class="t-desc">${esc(e.description || '')}</div></li>`).join('');
    const pend = pendingLabels.map(l => `<li class="pending"><div class="dot"><i class="bi bi-three-dots"></i></div><div class="t-title text-muted">${esc(l)}</div></li>`).join('');
    return `<ul class="timeline">${done}${pend}</ul>`;
  }

  function table(el, cols, rows, emptyMsg = 'Nothing here yet') {
    if (!rows.length) { empty(el, '📭', emptyMsg); return; }
    el.innerHTML = `<div class="table-responsive"><table class="table table-hover align-middle"><thead><tr>${cols.map(c => `<th>${c.h}</th>`).join('')}</tr></thead><tbody>${rows.map(r => `<tr>${cols.map(c => `<td>${c.f(r)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
  }

  // ---------------------------------------------------------------- product card / rows
  let wishSet = new Set();
  async function loadWish() { if (!token()) return; try { (await api('/api/wishlist')).forEach(p => wishSet.add(p.id)); } catch { } }

  function card(p) {
    const stock = p.stock === 0 ? '<span class="stock-out">Out of stock</span>' : p.stock <= 5 ? `<span class="stock-low">Only ${p.stock} left</span>` : '';
    return `<div class="pcard"><a href="/product-details.html?id=${p.id}" class="pimg">
      ${p.discountPercent > 0 ? `<span class="off-badge">-${p.discountPercent}%</span>` : ''}${p.onSale ? '<span class="sale-badge">SALE</span>' : ''}
      <img loading="lazy" src="${img(p)}" alt="${esc(p.name)}"></a>
      <button class="heart ${wishSet.has(p.id) ? 'on' : ''}" title="Wishlist" onclick="BB.toggleWish(${p.id},this)"><i class="bi bi-heart${wishSet.has(p.id) ? '-fill' : ''}"></i></button>
      <div class="pbody"><span class="pbrand">${esc(p.brand || '')}</span>
      <a class="pname" href="/product-details.html?id=${p.id}">${esc(p.name)}</a>
      ${stars(p.rating, p.reviewCount)}
      <div><span class="price"><small>₹</small>${Number(p.finalPrice).toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>${p.discountPercent > 0 ? `<span class="mrp">${money(p.price)}</span>` : ''}</div>
      <div class="free-del">${Number(p.finalPrice) >= 499 ? 'FREE delivery' : '+₹40 delivery'} · <span class="verified-tag"><i class="bi bi-patch-check-fill"></i> Verified</span></div>
      ${stock}
      <button class="btn btn-bb btn-sm mt-auto" ${p.stock === 0 ? 'disabled' : ''} onclick="BB.addCart(${p.id})">Add to Cart</button></div></div>`;
  }
  function row(el, list) {
    if (!list || !list.length) { el.innerHTML = '<div class="text-muted p-3">No products to show yet.</div>'; return; }
    el.innerHTML = `<div class="hscroll-wrap"><button class="hs-btn l"><i class="bi bi-chevron-left"></i></button><div class="hscroll">${list.map(card).join('')}</div><button class="hs-btn r"><i class="bi bi-chevron-right"></i></button></div>`;
    const sc = $('.hscroll', el);
    $('.hs-btn.l', el).onclick = () => sc.scrollBy({ left: -sc.clientWidth * .8 });
    $('.hs-btn.r', el).onclick = () => sc.scrollBy({ left: sc.clientWidth * .8 });
  }

  async function toggleWish(id, btn) {
    if (!token()) { location.href = loginUrl(); return; }
    try {
      if (wishSet.has(id)) { await api('/api/wishlist/' + id, { method: 'DELETE' }); wishSet.delete(id); toast('Removed from wishlist', 'info'); }
      else { await api('/api/wishlist/' + id, { method: 'POST' }); wishSet.add(id); toast('Added to wishlist'); }
      if (btn) { const on = wishSet.has(id); btn.classList.toggle('on', on); btn.innerHTML = `<i class="bi bi-heart${on ? '-fill' : ''}"></i>`; }
    } catch (e) { fail(e); }
  }
  async function addCart(id, qty = 1) {
    if (!token()) { location.href = loginUrl(); return; }
    if (user().role !== 'USER') { toast('Please use a customer account to shop', 'err'); return; }
    try { const c = await api('/api/cart/items', { method: 'POST', body: { productId: id, quantity: qty } }); toast('Added to cart'); setCount('#cartCount', c.count); }
    catch (e) { fail(e); }
  }
  const setCount = (sel, n) => { const el = $(sel); if (el) { el.textContent = n > 99 ? '99+' : n; el.style.display = n > 0 ? '' : 'none'; } };

  function viewed(id) { if (id == null) { try { return JSON.parse(localStorage.getItem('bb_viewed') || '[]'); } catch { return []; } } const l = viewed().filter(x => x !== id); l.unshift(id); localStorage.setItem('bb_viewed', JSON.stringify(l.slice(0, 12))); }

  // ---------------------------------------------------------------- header / footer
  const SUBNAV = [['/products.html?sort=discount', 'Today\'s Deals'], ['/index.html#festival', 'Festival Sale'], ['/products.html?sort=popular', 'Best Sellers'], ['/products.html?category=Electronics', 'Electronics'], ['/products.html?category=Clothing', 'Clothing'], ['/products.html?category=Home%20Decor', 'Home Decor'], ['/products.html?category=Furniture', 'Furniture'], ['/products.html?category=Beauty', 'Beauty'], ['/products.html?category=Sports', 'Sports'], ['/products.html?category=Toys', 'Toys']];

  async function header() {
    const u = user(); const el = $('#bb-header'); if (!el) return;
    const dash = u && u.role !== 'USER' ? `<a class="dropdown-item" href="${homeFor(u.role)}"><i class="bi bi-speedometer2 me-2"></i>${u.role === 'ADMIN' ? 'Admin' : 'Seller'} Dashboard</a>` : '';
    const acct = u ? `<div class="dropdown"><a class="bb-nav-item" data-bs-toggle="dropdown" role="button"><span class="small-top">Hello, ${esc(u.fullName.split(' ')[0])}</span><b>Account <i class="bi bi-caret-down-fill small"></i></b></a>
      <ul class="dropdown-menu dropdown-menu-end">${dash}
      <li><a class="dropdown-item" href="/account.html"><i class="bi bi-person me-2"></i>My Account</a></li>
      <li><a class="dropdown-item" href="/orders.html"><i class="bi bi-box-seam me-2"></i>My Orders</a></li>
      <li><a class="dropdown-item" href="/returns.html"><i class="bi bi-arrow-return-left me-2"></i>Returns</a></li>
      <li><a class="dropdown-item" href="/refunds.html"><i class="bi bi-cash-coin me-2"></i>Refunds</a></li>
      <li><a class="dropdown-item" href="/account.html#bank"><i class="bi bi-bank me-2"></i>Bank Accounts</a></li>
      <li><hr class="dropdown-divider"></li><li><a class="dropdown-item" href="#" onclick="BB.logout();return false"><i class="bi bi-box-arrow-right me-2"></i>Logout</a></li></ul></div>`
      : `<div class="dropdown"><a class="bb-nav-item" data-bs-toggle="dropdown" role="button"><span class="small-top">Hello, sign in</span><b>Account <i class="bi bi-caret-down-fill small"></i></b></a>
      <ul class="dropdown-menu dropdown-menu-end p-2" style="min-width:230px"><li><a class="btn btn-bb w-100 mb-2" href="${loginUrl()}">Sign in</a></li><li class="small text-center">New to ByteBazaar? <a href="/register.html">Create account</a></li></ul></div>`;
    el.innerHTML = `<header class="bb-top"><div class="row1">
      <button class="bb-menu-btn" data-bs-toggle="offcanvas" data-bs-target="#bbCats"><i class="bi bi-list"></i></button>
      <a class="logo" href="/index.html"><img src="/images/logo-light.svg" alt="ByteBazaar - Shop More. Live Better."></a>
      <div class="bb-deliver bb-hide-md"><span>Deliver to</span><br><b><i class="bi bi-geo-alt-fill"></i> ${esc(u && u.city ? u.city : 'India')}</b></div>
      <form class="bb-search" id="bbSearch"><select id="bbSearchCat"><option value="">All</option></select><input id="bbQ" type="search" placeholder="Search products, brands and categories..." autocomplete="off" value="${esc(qs('q') || '')}"><button aria-label="Search"><i class="bi bi-search"></i></button></form>
      ${acct}
      <a class="bb-nav-item bb-hide-md" href="/orders.html"><span class="small-top">Returns</span><b>& Orders</b></a>
      <a class="bb-nav-item" href="/wishlist.html" title="Wishlist"><i class="bi bi-heart fs-5"></i><span class="bb-badge" id="wishCount" style="display:none"></span></a>
      <a class="bb-nav-item" href="/notifications.html" title="Notifications"><i class="bi bi-bell fs-5"></i><span class="bb-badge" id="notifCount" style="display:none"></span></a>
      <a class="bb-nav-item" href="/cart.html" title="Cart"><i class="bi bi-cart3 fs-4"></i><span class="bb-badge" id="cartCount" style="display:none"></span></a>
    </div>
    <div class="bb-sub"><div class="inner"><a href="#" data-bs-toggle="offcanvas" data-bs-target="#bbCats"><i class="bi bi-list"></i> All</a>${SUBNAV.map(s => `<a href="${s[0]}">${s[1]}</a>`).join('')}<a class="cs" href="/support.html"><i class="bi bi-headset"></i> 24×7 Help & Support</a></div></div></header>
    <div class="offcanvas offcanvas-start" id="bbCats" tabindex="-1"><div class="offcanvas-header bg-dark text-white"><h5 class="offcanvas-title">Shop by Category</h5><button class="btn-close btn-close-white" data-bs-dismiss="offcanvas"></button></div><div class="offcanvas-body" id="bbCatList"></div></div>`;
    $('#bbSearch').onsubmit = e => { e.preventDefault(); const q = $('#bbQ').value.trim(), c = $('#bbSearchCat').value; location.href = '/products.html?' + new URLSearchParams({ ...(q && { q }), ...(c && { category: c }) }); };
    api('/api/categories').then(cats => {
      $('#bbSearchCat').innerHTML = '<option value="">All</option>' + cats.map(c => `<option>${esc(c.name)}</option>`).join('');
      $('#bbCatList').innerHTML = cats.map(c => `<div class="mb-3"><a class="fw-bold fs-6" href="/products.html?category=${encodeURIComponent(c.name)}">${EMOJI[c.name] || ''} ${esc(c.name)}</a><div class="ms-3">${c.children.map(k => `<a class="d-block py-1 text-secondary" href="/products.html?category=${encodeURIComponent(k.name)}">${esc(k.name)}</a>`).join('')}</div></div>`).join('');
    }).catch(() => { });
    if (u && token()) {
      api('/api/cart').then(c => setCount('#cartCount', c.count)).catch(() => { });
      api('/api/notifications').then(n => setCount('#notifCount', n.unread)).catch(() => { });
      api('/api/wishlist').then(w => { setCount('#wishCount', w.length); w.forEach(p => wishSet.add(p.id)); }).catch(() => { });
    }
  }

  function footer() {
    const el = $('#bb-footer'); if (!el) return;
    el.innerHTML = `<footer class="bb-foot"><a class="toTop" onclick="scrollTo({top:0,behavior:'smooth'})" role="button">Back to top <i class="bi bi-chevron-up"></i></a>
      <div class="cols"><div><img src="/images/logo-light.svg" height="46" alt="ByteBazaar"><p class="mt-3 small">ByteBazaar is a verified marketplace connecting trusted sellers with happy customers across India.</p></div>
      <div><h6>Shop</h6><a href="/products.html?category=Electronics">Electronics</a><a href="/products.html?category=Clothing">Clothing</a><a href="/products.html?category=Furniture">Furniture</a><a href="/products.html?category=Beauty">Beauty</a></div>
      <div><h6>Your Account</h6><a href="/account.html">My Account</a><a href="/orders.html">Orders</a><a href="/returns.html">Returns</a><a href="/wishlist.html">Wishlist</a></div>
      <div><h6>Help</h6><a href="/support.html">Help & Support</a><a href="/support-chat.html">Chat with Admin</a><a href="/contact.html">Contact Us</a><a href="/about.html">About ByteBazaar</a></div>
      <div><h6>Sell on ByteBazaar</h6><a href="/register.html?role=SELLER">Become a seller</a><a href="/login.html">Seller login</a></div></div>
      <div class="copy">© ${new Date().getFullYear()} ByteBazaar — Shop More. Live Better. · Demo project: payments &amp; bank details are simulated.</div></footer>`;
  }

  async function init({ auth, roles } = {}) {
    if (auth && !requireAuth(roles)) return false;
    await header(); footer(); return true;
  }

  // ---------------------------------------------------------------- dashboards (seller / admin)
  const MENUS = {
    SELLER: [['dashboard', 'bi-speedometer2', 'Dashboard'], ['analytics', 'bi-graph-up-arrow', 'Sales, Stock & Revenue'], ['profile', 'bi-person-badge', 'My Profile'], ['bank-account', 'bi-bank', 'Bank Account'], ['manage-products?add=1', 'bi-plus-square', 'Add Product'], ['manage-products', 'bi-boxes', 'Manage Products'], ['orders', 'bi-box-seam', 'Orders'], ['commission', 'bi-percent', 'Commission'], ['invoices', 'bi-receipt', 'Invoices'], ['notifications', 'bi-bell', 'Notifications'], ['/support.html', 'bi-headset', 'Help & Support']],
    ADMIN: [['dashboard', 'bi-speedometer2', 'Dashboard'], ['analytics', 'bi-graph-up-arrow', 'Sales, Stock & Revenue'], ['users', 'bi-people', 'Users'], ['sellers', 'bi-shop', 'Sellers'], ['products', 'bi-boxes', 'Products'], ['orders', 'bi-box-seam', 'Orders'], ['returns', 'bi-arrow-return-left', 'Returns'], ['refunds', 'bi-cash-coin', 'Refunds'], ['commissions', 'bi-percent', 'Commissions'], ['sales', 'bi-lightning-charge', 'Festival Sales'], ['brand-deals', 'bi-tags', 'Brand Deals'], ['banners', 'bi-images', 'Banners'], ['support', 'bi-chat-dots', 'Support'], ['bank-account', 'bi-bank', 'Demo Bank Account'], ['notifications', 'bi-bell', 'Notifications'], ['settings', 'bi-gear', 'Settings']]
  };
  function dash(role, active, title) {
    if (!requireAuth([role])) return false;
    const base = role === 'ADMIN' ? '/admin/' : '/seller/';
    $('#sidebar').innerHTML = `<div class="brand"><a href="/index.html"><img src="/images/logo-light.svg" alt="ByteBazaar"></a></div><div class="role-tag">${role} PANEL</div>` +
      MENUS[role].map(m => { const href = m[0].startsWith('/') ? m[0] : base + m[0].replace(/^([^?]+)/, '$1.html'); const key = m[0].split('?')[0]; return `<a class="${key === active && !m[0].includes('add=1') ? 'on' : ''}" href="${href}"><i class="bi ${m[1]}"></i>${m[2]}</a>`; }).join('') +
      `<hr class="border-secondary"><a href="/index.html"><i class="bi bi-house"></i>View Store</a><a href="#" onclick="BB.logout();return false"><i class="bi bi-box-arrow-right"></i>Logout</a>`;
    $('#dash-top').innerHTML = `<button class="sb-toggle" onclick="document.getElementById('sidebar').classList.toggle('open')"><i class="bi bi-list"></i></button><h1>${title}</h1><span class="text-muted small d-none d-md-inline">${esc(user().fullName)}</span>`;
    document.addEventListener('click', e => { const sb = $('#sidebar'); if (sb.classList.contains('open') && !sb.contains(e.target) && !e.target.closest('.sb-toggle')) sb.classList.remove('open'); });
    return true;
  }
  const stat = (icon, color, label, value) => `<div class="col-6 col-lg-3"><div class="stat"><div class="ic" style="background:${color}"><i class="bi ${icon}"></i></div><div><div class="v">${value}</div><div class="l">${label}</div></div></div></div>`;

  // notifications list (used by customer, seller and admin pages)
  async function notifList(el) {
    loader(el);
    try {
      const d = await api('/api/notifications');
      if (!d.items.length) return empty(el, '🔔', 'No notifications yet', 'Order, payment and delivery updates will show up here.');
      const ico = { ORDER: 'bi-bag-check', PAYMENT: 'bi-credit-card', SHIPPING: 'bi-truck', DELIVERY: 'bi-box2-heart', RETURN: 'bi-arrow-return-left', REFUND: 'bi-cash-coin', PROMOTION: 'bi-megaphone', ACCOUNT: 'bi-person', SUPPORT: 'bi-headset' };
      el.innerHTML = `<div class="d-flex justify-content-between mb-2"><span class="text-muted">${d.unread} unread</span><button class="btn btn-sm btn-navy" id="readAll">Mark all as read</button></div>` +
        d.items.map(n => `<div class="bb-card mb-2 d-flex gap-3 align-items-start ${n.seen ? '' : 'border-start border-4 border-warning'}"><i class="bi ${ico[n.type] || 'bi-bell'} fs-4 text-secondary"></i><div class="flex-grow-1"><div class="${n.seen ? '' : 'fw-bold'}">${esc(n.message)}</div><small class="text-muted">${n.type} · ${dt(n.createdAt)}</small></div>
        ${n.seen ? '' : `<button class="btn btn-sm btn-light" data-r="${n.id}">Mark read</button>`}<button class="btn btn-sm btn-outline-danger" data-d="${n.id}"><i class="bi bi-trash"></i></button></div>`).join('');
      $('#readAll', el).onclick = async () => { await api('/api/notifications/read-all', { method: 'PUT' }); notifList(el); setCount('#notifCount', 0); };
      $$('[data-r]', el).forEach(b => b.onclick = async () => { await api('/api/notifications/' + b.dataset.r + '/read', { method: 'PUT' }); notifList(el); });
      $$('[data-d]', el).forEach(b => b.onclick = async () => { await api('/api/notifications/' + b.dataset.d, { method: 'DELETE' }); notifList(el); });
    } catch (e) { errorState(el, e); }
  }

  // bank account manager (customers, sellers, admin)
  async function bankManager(el, { allowAdd = true } = {}) {
    loader(el);
    try {
      const list = await api('/api/bank-accounts');
      el.innerHTML = `<div class="d-flex justify-content-between align-items-center mb-3"><span class="text-muted">Account numbers are always masked. Never share your full banking details.</span>${allowAdd ? '<button class="btn btn-bb btn-sm" id="addBank"><i class="bi bi-plus-lg"></i> Add bank account</button>' : ''}</div>` +
        (list.length ? list.map(b => `<div class="bb-card mb-2"><div class="d-flex flex-wrap justify-content-between gap-2"><div><b>${esc(b.bankName)}</b> ${b.primary ? '<span class="badge bg-success">PRIMARY</span>' : ''} ${b.active ? '' : '<span class="badge bg-secondary">INACTIVE</span>'}<div>${esc(b.holderName)} · <code>${esc(b.maskedAccount)}</code></div><small class="text-muted">IFSC ${esc(b.ifsc)}${b.upiId ? ' · UPI ' + esc(b.upiId) : ''}</small>${b.demo ? '<div class="mt-1"><span class="demo-ribbon">DEMO ACCOUNT — NOT A REAL BANK ACCOUNT</span></div>' : ''}</div>
          <div class="d-flex gap-2 align-items-start flex-wrap">${b.primary ? '' : `<button class="btn btn-sm btn-outline-success" data-p="${b.id}">Set primary</button>`}<button class="btn btn-sm btn-outline-secondary" data-a="${b.id}" data-v="${!b.active}">${b.active ? 'Deactivate' : 'Activate'}</button><button class="btn btn-sm btn-outline-primary" data-e="${b.id}">Edit</button><button class="btn btn-sm btn-outline-danger" data-x="${b.id}">Delete</button></div></div></div>`).join('') : '<div class="empty"><div class="em">🏦</div><p>No bank account added yet. Add one to receive refunds or sale earnings.</p></div>');
      const form = (b) => `<form id="bankForm" class="row g-2"><div class="col-12"><label class="form-label">Account holder name</label><input class="form-control" name="holderName" required value="${esc(b?.holderName)}"></div>
        <div class="col-12"><label class="form-label">Bank name</label><input class="form-control" name="bankName" required value="${esc(b?.bankName)}"></div>
        <div class="col-md-6"><label class="form-label">Account number</label><input class="form-control" name="accountNumber" inputmode="numeric" required placeholder="9-18 digits"></div>
        <div class="col-md-6"><label class="form-label">Confirm account number</label><input class="form-control" name="confirmAccountNumber" inputmode="numeric" required></div>
        <div class="col-md-6"><label class="form-label">IFSC code</label><input class="form-control" name="ifsc" required placeholder="HDFC0001234" style="text-transform:uppercase" value="${esc(b?.ifsc)}"></div>
        <div class="col-md-6"><label class="form-label">UPI ID (optional)</label><input class="form-control" name="upiId" placeholder="name@bank" value="${esc(b?.upiId)}"></div>
        <div class="col-12 form-check ms-2"><input class="form-check-input" type="checkbox" name="primary" id="bkPrim" ${b?.primary ? 'checked' : ''}><label class="form-check-label" for="bkPrim">Set as primary account</label></div>
        <div class="alert alert-warning small mt-2 mb-0">${b ? 'For security, re-enter the full account number to save changes. ' : ''}Do not use real credentials in this demo.</div></form>`;
      const open = (b) => {
        const mm = modal(b ? 'Edit bank account' : 'Add bank account', form(b), { footer: '<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-bb" id="bkSave">Save</button>' });
        $('#bkSave', mm.el).onclick = async e => {
          const f = $('#bankForm', mm.el); if (!f.reportValidity()) return; const fd = new FormData(f);
          const body = { holderName: fd.get('holderName'), bankName: fd.get('bankName'), accountNumber: fd.get('accountNumber'), confirmAccountNumber: fd.get('confirmAccountNumber'), ifsc: fd.get('ifsc').toUpperCase(), upiId: fd.get('upiId') || '', primary: !!fd.get('primary') };
          spin(e.target, true); try { await api(b ? '/api/bank-accounts/' + b.id : '/api/bank-accounts', { method: b ? 'PUT' : 'POST', body }); mm.close(); toast('Bank account saved'); bankManager(el, { allowAdd }); } catch (x) { fail(x); spin(e.target, false); }
        };
      };
      if (allowAdd) $('#addBank', el).onclick = () => open(null);
      $$('[data-e]', el).forEach(b => b.onclick = () => open(list.find(x => x.id == b.dataset.e)));
      $$('[data-p]', el).forEach(b => b.onclick = async () => { try { await api('/api/bank-accounts/' + b.dataset.p + '/primary', { method: 'PUT' }); bankManager(el, { allowAdd }); } catch (x) { fail(x); } });
      $$('[data-a]', el).forEach(b => b.onclick = async () => { try { await api('/api/bank-accounts/' + b.dataset.a + '/active?value=' + b.dataset.v, { method: 'PUT' }); bankManager(el, { allowAdd }); } catch (x) { fail(x); } });
      $$('[data-x]', el).forEach(b => b.onclick = async () => { if (await confirmBox('Remove this bank account?')) { try { await api('/api/bank-accounts/' + b.dataset.x, { method: 'DELETE' }); bankManager(el, { allowAdd }); } catch (x) { fail(x); } } });
    } catch (e) { errorState(el, e); }
  }

  // profile form shared by account / seller profile
  function profileForm(el, u, isSeller) {
    el.innerHTML = `<form id="pf" class="row g-3"><div class="col-md-6"><label class="form-label">Full name</label><input class="form-control" name="fullName" required value="${esc(u.fullName)}"></div>
      <div class="col-md-6"><label class="form-label">Email</label><input class="form-control" type="email" name="email" required value="${esc(u.email)}"></div>
      <div class="col-md-6"><label class="form-label">Phone</label><input class="form-control" name="phone" required pattern="[6-9][0-9]{9}" value="${esc(u.phone)}"></div>
      ${isSeller ? `<div class="col-md-6"><label class="form-label">Store name</label><input class="form-control" name="storeName" value="${esc(u.storeName)}"></div>` : '<div class="col-md-6"><label class="form-label">Account status</label><div><span class="badge-st st-' + u.status + '">' + u.status + '</span></div></div>'}
      <div class="col-12"><label class="form-label">Address</label><input class="form-control" name="address" value="${esc(u.address)}"></div>
      <div class="col-md-4"><label class="form-label">City</label><input class="form-control" name="city" value="${esc(u.city)}"></div>
      <div class="col-md-4"><label class="form-label">State</label><input class="form-control" name="state" value="${esc(u.state)}"></div>
      <div class="col-md-4"><label class="form-label">PIN code</label><input class="form-control" name="pin" pattern="[0-9]{6}" value="${esc(u.pin)}"></div>
      <div class="col-12"><button class="btn btn-bb" type="submit">Save changes</button></div></form>
      <hr><h6>Change password</h6><form id="pw" class="row g-2"><div class="col-md-4"><input class="form-control" type="password" name="c" placeholder="Current password" required></div><div class="col-md-4"><input class="form-control" type="password" name="n" placeholder="New password" required></div><div class="col-md-4"><button class="btn btn-navy">Update password</button></div><div class="form-text">8+ characters with upper & lower case, a digit and a special character.</div></form>`;
    $('#pf', el).onsubmit = async e => { e.preventDefault(); const fd = Object.fromEntries(new FormData(e.target)); const b = e.target.querySelector('button'); spin(b, true); try { const nu = await api('/api/user/profile', { method: 'PUT', body: fd }); localStorage.setItem('bb_user', JSON.stringify(nu)); toast('Profile updated'); } catch (x) { fail(x); } spin(b, false); };
    $('#pw', el).onsubmit = async e => { e.preventDefault(); const f = e.target; try { await api('/api/user/password', { method: 'PUT', body: { currentPassword: f.c.value, newPassword: f.n.value } }); toast('Password changed'); f.reset(); } catch (x) { fail(x); } };
  }

  return { $, $$, esc, token, user, setAuth, clearAuth, homeFor, logout, loginUrl, requireAuth, api, money, dateOnly, timeOnly, dt, stars, badge, img, src, ph, toast, fail, loader, empty, errorState, modal, confirmBox, spin, qs, download, timeline, table, card, row, toggleWish, addCart, loadWish, setCount, viewed, init, dash, stat, notifList, bankManager, profileForm };
})();
