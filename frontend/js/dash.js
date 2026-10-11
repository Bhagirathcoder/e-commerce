/* Shared seller/admin widgets: product form modal. Requires app.js (BB). */
BB.productForm = async function (p, { admin = false, onSaved } = {}) {
  const $ = BB.$;
  let cats; try { cats = await BB.api('/api/categories'); } catch (e) { return BB.fail(e); }
  let images = p ? [...p.images] : [];
  const opt = cats.map(c => `<optgroup label="${BB.esc(c.name)}">${(c.children.length ? c.children : [c]).map(k => `<option value="${k.id}" ${p && p.categoryId === k.id ? 'selected' : ''}>${BB.esc(k.name)}</option>`).join('')}</optgroup>`).join('');
  const v = (k, d = '') => BB.esc(p ? (p[k] ?? d) : d);
  const m = BB.modal(p ? 'Edit product' : 'Add product', `<form id="pf" class="row g-2">
    <div class="col-12"><label class="form-label">Product name *</label><input class="form-control" name="name" required maxlength="200" value="${v('name')}"></div>
    <div class="col-md-6"><label class="form-label">Category / Subcategory *</label><select class="form-select" name="categoryId" required>${opt}</select></div>
    <div class="col-md-6"><label class="form-label">Brand</label><input class="form-control" name="brand" value="${v('brand')}"></div>
    <div class="col-12"><label class="form-label">Description</label><textarea class="form-control" name="description" rows="3">${v('description')}</textarea></div>
    <div class="col-12"><label class="form-label">Specifications <span class="text-muted small">(one per line, e.g. “RAM: 16GB”)</span></label><textarea class="form-control" name="specifications" rows="3">${v('specifications')}</textarea></div>
    <div class="col-md-6"><label class="form-label">Search tags <span class="text-muted small">(comma separated)</span></label><input class="form-control" name="tags" value="${v('tags')}" placeholder="laptop, notebook"></div>
    <div class="col-md-6"><label class="form-label">SKU</label><input class="form-control" name="sku" value="${v('sku')}" placeholder="auto-generated if empty"></div>
    <div class="col-md-3"><label class="form-label">Price (MRP ₹) *</label><input class="form-control" type="number" min="1" step="0.01" name="price" required value="${v('price')}"></div>
    <div class="col-md-3"><label class="form-label">Discount % </label><input class="form-control" type="number" min="0" max="90" name="discountPercent" value="${v('discountPercent', 0)}"></div>
    <div class="col-md-3"><label class="form-label">Stock *</label><input class="form-control" type="number" min="0" name="stock" required value="${v('stock', 0)}"></div>
    <div class="col-md-3"><label class="form-label">Warranty (months)</label><input class="form-control" type="number" min="0" name="warrantyMonths" value="${v('warrantyMonths', 0)}"></div>
    <div class="col-md-6 form-check ms-2 mt-3"><input class="form-check-input" type="checkbox" name="returnable" id="pfr" ${!p || p.returnable ? 'checked' : ''}><label class="form-check-label" for="pfr">Returnable (7-day return)</label></div>
    <div class="col-md-5 form-check mt-3"><input class="form-check-input" type="checkbox" name="active" id="pfa" ${!p || p.active ? 'checked' : ''}><label class="form-check-label" for="pfa">Active</label></div>
    <div class="col-12"><label class="form-label">Product images (JPG, PNG, WEBP · up to 8)</label><input class="form-control" type="file" id="pfi" accept=".jpg,.jpeg,.png,.webp" multiple><div class="d-flex flex-wrap gap-2 mt-2" id="pfth"></div></div>
    ${admin ? '' : '<div class="col-12"><div class="alert alert-info small mb-0">New products and changes to name, description, category or brand go to <b>admin verification</b> before they appear to customers.</div></div>'}</form>`,
    { size: 'modal-lg', footer: '<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-bb" id="pfs">Save product</button>' });
  const thumbs = () => { $('#pfth', m.el).innerHTML = images.map((u, i) => `<div class="position-relative"><img class="timg" style="width:70px;height:70px" src="${BB.esc(u)}"><button type="button" class="btn btn-sm btn-danger position-absolute top-0 end-0 p-0 px-1" data-rm="${i}">×</button></div>`).join(''); BB.$$('[data-rm]', m.el).forEach(b => b.onclick = () => { images.splice(+b.dataset.rm, 1); thumbs(); }); };
  thumbs();
  $('#pfi', m.el).onchange = async e => {
    for (const f of e.target.files) { if (images.length >= 8) break; const fd = new FormData(); fd.append('file', f); try { const r = await BB.api('/api/uploads', { method: 'POST', form: fd }); images.push(r.url); } catch (x) { BB.fail(x); } }
    e.target.value = ''; thumbs();
  };
  $('#pfs', m.el).onclick = async e => {
    const f = $('#pf', m.el); if (!f.reportValidity()) return; const fd = new FormData(f);
    const body = { name: fd.get('name'), categoryId: +fd.get('categoryId'), brand: fd.get('brand'), description: fd.get('description'), specifications: fd.get('specifications'), tags: fd.get('tags'), sku: fd.get('sku'), price: +fd.get('price'), discountPercent: +(fd.get('discountPercent') || 0), stock: +fd.get('stock'), images, warrantyMonths: +(fd.get('warrantyMonths') || 0), returnable: !!fd.get('returnable'), active: !!fd.get('active') };
    const base = admin ? '/api/admin/products' : '/api/seller/products';
    BB.spin(e.target, true);
    try { await BB.api(p ? base + '/' + p.id : base, { method: p ? 'PUT' : 'POST', body }); m.close(); BB.toast(p ? 'Product updated' : (admin ? 'Product added' : 'Product submitted for verification')); onSaved && onSaved(); }
    catch (x) { BB.fail(x); BB.spin(e.target, false); }
  };
};

/** Order status updater modal (seller & admin). */
BB.statusModal = function (o, allowed, apiBase, onSaved) {
  const m = BB.modal('Update order #' + o.id, `<div class="mb-2">Current status: ${BB.badge(o.status)}</div><form id="sf"><label class="form-label">New status</label><select class="form-select mb-2" name="status">${allowed.map(s => `<option>${s}</option>`).join('')}</select><label class="form-label">Location</label><input class="form-control mb-2" name="location" placeholder="e.g. Cuttack Hub"><label class="form-label">Description (optional)</label><input class="form-control" name="description"></form><div class="small text-muted mt-2">The customer is notified automatically.</div>`, { footer: '<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-bb" id="ss">Update</button>' });
  BB.$('#ss', m.el).onclick = async e => { const f = BB.$('#sf', m.el); BB.spin(e.target, true); try { await BB.api(apiBase + '/orders/' + o.id + '/status', { method: 'PUT', body: Object.fromEntries(new FormData(f)) }); m.close(); BB.toast('Status updated'); onSaved(); } catch (x) { BB.fail(x); BB.spin(e.target, false); } };
};

/** Sales / revenue / stock analytics. base = '/api/seller' or '/api/admin'. Needs Chart.js loaded on the page. */
BB.analyticsView = async function (el, base, { days = 30, compact = false } = {}) {
  const $ = BB.$, s = BB.stat;
  BB.loader(el);
  let d;
  try { d = await BB.api(base + '/analytics?days=' + days); } catch (e) { return BB.errorState(el, e); }
  const sm = d.summary, st = d.stock, isAdmin = base.includes('admin');
  const stLbl = { IN_STOCK: 'In stock', LOW_STOCK: 'Low stock', OUT_OF_STOCK: 'Out of stock' };
  const stCls = { IN_STOCK: 'st-DELIVERED', LOW_STOCK: 'st-PLACED', OUT_OF_STOCK: 'st-CANCELLED' };
  const csv = (name, rows) => { const t = rows.map(r => r.map(c => '"' + String(c ?? '').replace(/"/g, '""') + '"').join(',')).join('\n'); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([t], { type: 'text/csv' })); a.download = name; a.click(); };

  el.innerHTML = `
  ${compact ? '' : `<div class="d-flex flex-wrap gap-2 justify-content-between align-items-center mb-3"><div class="btn-group" id="rg">${[7, 30, 90, 365].map(n => `<button class="btn btn-sm ${n === d.days ? 'btn-navy' : 'btn-outline-secondary'}" data-d="${n}">${n === 365 ? '1 year' : 'Last ' + n + ' days'}</button>`).join('')}</div>
    <div class="d-flex gap-2"><button class="btn btn-sm btn-outline-secondary" id="csvSales"><i class="bi bi-download"></i> Sales CSV</button><button class="btn btn-sm btn-outline-secondary" id="csvStock"><i class="bi bi-download"></i> Stock CSV</button></div></div>`}
  <h6 class="text-muted text-uppercase small">Sales & revenue — last ${d.days} days</h6>
  <div class="row g-3 mb-3">${s('bi-currency-rupee', '#0a7ea4', 'Total Revenue', BB.money(sm.revenue))}${s('bi-bag-check', '#173a63', 'Orders', sm.orders)}${s('bi-box2', '#fb8500', 'Units Sold', sm.unitsSold)}${s('bi-graph-up-arrow', '#067d62', 'Avg Order Value', BB.money(sm.avgOrderValue))}
  ${compact ? '' : s('bi-check2-circle', '#067d62', 'Delivered Revenue', BB.money(sm.deliveredRevenue)) + s('bi-hourglass-split', '#b45309', 'Pending Revenue', BB.money(sm.pendingRevenue)) + s('bi-percent', '#7a1fa2', isAdmin ? 'Commission Earned' : 'Commission Paid to ByteBazaar', BB.money(sm.commission)) + (isAdmin ? s('bi-arrow-return-left', '#c7222a', 'Refunded', BB.money(sm.refundedAmount)) : s('bi-wallet2', '#067d62', 'Net Earnings', BB.money(sm.netEarnings)))}</div>
  <div class="row g-3 mb-3"><div class="col-lg-${compact ? 12 : 8}"><div class="table-card"><h6>Revenue trend</h6><div style="height:260px"><canvas id="chRev"></canvas></div></div></div>
  ${compact ? '' : `<div class="col-lg-4"><div class="table-card"><h6>Revenue by category</h6><div style="height:260px"><canvas id="chCat"></canvas></div></div></div>
  <div class="col-lg-8"><div class="table-card"><h6>Orders & units per day</h6><div style="height:240px"><canvas id="chUnits"></canvas></div></div></div>
  <div class="col-lg-4"><div class="table-card"><h6>${isAdmin ? 'Revenue by seller' : 'Revenue by payment method'}</h6><div style="height:240px"><canvas id="chPay"></canvas></div></div></div>`}</div>
  <h6 class="text-muted text-uppercase small mt-4">Stock</h6>
  <div class="row g-3 mb-3">${s('bi-boxes', '#0f2742', 'Products', st.products)}${s('bi-stack', '#173a63', 'Units in Stock', Number(st.totalUnits).toLocaleString('en-IN'))}${s('bi-cash-stack', '#067d62', 'Stock Value', BB.money(st.stockValue))}${s('bi-exclamation-triangle', '#b45309', 'Low Stock (≤5)', st.lowStock)}${s('bi-x-octagon', '#c7222a', 'Out of Stock', st.outOfStock)}</div>
  ${st.lowStock + st.outOfStock > 0 ? `<div class="alert alert-warning"><i class="bi bi-exclamation-triangle-fill"></i> <b>${st.outOfStock}</b> product(s) are out of stock and <b>${st.lowStock}</b> are running low — restock soon to avoid lost sales.</div>` : ''}
  ${compact ? '' : `<div class="row g-3"><div class="col-lg-5"><div class="table-card"><h6>Top products by revenue</h6><div id="top"></div></div></div><div class="col-lg-7"><div class="table-card"><div class="d-flex justify-content-between align-items-center mb-2"><h6 class="mb-0">Stock levels</h6><select class="form-select form-select-sm w-auto" id="sf"><option value="">All</option><option value="OUT_OF_STOCK">Out of stock</option><option value="LOW_STOCK">Low stock</option><option value="IN_STOCK">In stock</option></select></div><div id="stk" style="max-height:420px;overflow:auto"></div></div></div></div>`}`;

  const Ch = window.Chart; if (!Ch) return;
  if (BB._charts) BB._charts.forEach(c => c.destroy()); BB._charts = [];
  const mk = (id, cfg) => { const c = $('#' + id, el); if (c) BB._charts.push(new Ch(c, cfg)); };
  const lbl = d.daily.map(x => new Date(x.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }));
  const COL = ['#0f2742', '#fb8500', '#0a7ea4', '#067d62', '#7a1fa2', '#c7222a', '#ffb703', '#5b6b7f'];
  mk('chRev', { type: 'line', data: { labels: lbl, datasets: [{ label: 'Revenue (₹)', data: d.daily.map(x => x.revenue), borderColor: '#fb8500', backgroundColor: 'rgba(251,133,0,.15)', fill: true, tension: .3 }] }, options: { maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } } });
  if (compact) return;
  mk('chCat', { type: 'doughnut', data: { labels: Object.keys(d.revenueByCategory), datasets: [{ data: Object.values(d.revenueByCategory), backgroundColor: COL }] }, options: { maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } } });
  mk('chUnits', { type: 'bar', data: { labels: lbl, datasets: [{ label: 'Units sold', data: d.daily.map(x => x.units), backgroundColor: '#0f2742' }, { label: 'Orders', data: d.daily.map(x => x.orders), backgroundColor: '#ffb703' }] }, options: { maintainAspectRatio: false, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } } });
  const pay = isAdmin ? d.revenueBySeller : d.revenueByPayment;
  mk('chPay', isAdmin ? { type: 'bar', data: { labels: Object.keys(pay), datasets: [{ label: 'Revenue (₹)', data: Object.values(pay), backgroundColor: '#0a7ea4' }] }, options: { indexAxis: 'y', maintainAspectRatio: false, plugins: { legend: { display: false } } } }
    : { type: 'pie', data: { labels: Object.keys(pay), datasets: [{ data: Object.values(pay), backgroundColor: COL }] }, options: { maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } } });

  BB.table($('#top', el), [{ h: 'Product', f: p => `<a href="/product-details.html?id=${p.productId}">${BB.esc(p.name)}</a>` }, { h: 'Units', f: p => p.units }, { h: 'Revenue', f: p => '<b>' + BB.money(p.revenue) + '</b>' }], d.topProducts, 'No sales in this period yet.');
  const drawStock = () => { const f = $('#sf', el).value; BB.table($('#stk', el), [{ h: 'Product', f: p => `<div class="fw-semibold">${BB.esc(p.name)}</div><small class="text-muted">${BB.esc(p.category)} · ${BB.esc(p.sku || '')}</small>` }, { h: 'Stock', f: p => `<b>${p.stock}</b>` }, { h: 'Sold', f: p => p.sold }, { h: 'Value', f: p => BB.money(p.stockValue) }, { h: 'Status', f: p => `<span class="badge-st ${stCls[p.status]}">${stLbl[p.status]}</span>` }], st.items.filter(p => !f || p.status === f), 'No products match.'); };
  $('#sf', el).onchange = drawStock; drawStock();
  BB.$$('#rg button', el).forEach(b => b.onclick = () => BB.analyticsView(el, base, { days: +b.dataset.d }));
  $('#csvSales', el).onclick = () => csv('bytebazaar-sales-' + d.days + 'd.csv', [['Date', 'Revenue', 'Orders', 'Units']].concat(d.daily.map(x => [x.date, x.revenue, x.orders, x.units])));
  $('#csvStock', el).onclick = () => csv('bytebazaar-stock.csv', [['ID', 'Product', 'SKU', 'Category', 'Stock', 'Sold', 'Unit price', 'Stock value', 'Status']].concat(st.items.map(p => [p.id, p.name, p.sku, p.category, p.stock, p.sold, p.unitPrice, p.stockValue, p.status])));
};

/** Festival sales / brand deals manager (admin). type = 'FESTIVAL' | 'BRAND' */
BB.promoPage = async function (el, type) {
  const $ = BB.$; const brand = type === 'BRAND';
  BB.loader(el);
  let list, prods;
  try { [list, prods] = await Promise.all([BB.api('/api/admin/promotions'), BB.api('/api/admin/products')]); } catch (e) { return BB.errorState(el, e); }
  list = list.filter(p => p.type === type); prods = prods.filter(p => p.verification === 'VERIFIED');
  el.innerHTML = `<div class="d-flex justify-content-between mb-3"><span class="text-muted">${brand ? 'Brand deals' : 'Festival sales'} switch on and off automatically between their start and end dates. Live deals add an extra discount to selected products.</span><button class="btn btn-bb" id="np"><i class="bi bi-plus-lg"></i> New ${brand ? 'brand deal' : 'festival sale'}</button></div><div class="table-card" id="pt"></div>`;
  BB.table($('#pt', el), [{ h: 'Name', f: p => `<b>${BB.esc(p.name)}</b>${p.brandName ? '<div class="small text-muted">Brand: ' + BB.esc(p.brandName) + '</div>' : ''}<div class="small text-muted">${BB.esc(p.description || '')}</div>` }, { h: 'Discount', f: p => p.discountPercent + '%' }, { h: 'Start', f: p => BB.dt(p.startDate) }, { h: 'End', f: p => BB.dt(p.endDate) }, { h: 'Products', f: p => p.productIds.length }, { h: 'Status', f: p => p.live ? BB.badge('ACTIVE') : (p.enabled ? '<span class="badge-st">Scheduled / Ended</span>' : BB.badge('BLOCKED').replace('BLOCKED', 'DISABLED')) }, { h: '', f: p => `<button class="btn btn-sm btn-outline-primary" data-e="${p.id}">Edit</button> <button class="btn btn-sm btn-outline-danger" data-x="${p.id}">Delete</button>` }], list, 'Nothing created yet.');
  const loc = d => d ? String(d).slice(0, 16) : '';
  const open = p => {
    const sel = new Set(p ? p.productIds : []);
    const m = BB.modal(p ? 'Edit' : 'Create', `<form id="pfm" class="row g-2"><div class="col-12"><label class="form-label">${brand ? 'Deal name' : 'Sale name'} *</label><input class="form-control" name="name" required value="${BB.esc(p?.name)}" placeholder="${brand ? 'Electronics Deals' : 'Diwali Sale'}"></div>
      ${brand ? `<div class="col-12"><label class="form-label">Brand name</label><input class="form-control" name="brandName" value="${BB.esc(p?.brandName)}"></div>` : ''}
      <div class="col-12"><label class="form-label">Description</label><input class="form-control" name="description" value="${BB.esc(p?.description)}"></div>
      <div class="col-md-4"><label class="form-label">Discount % *</label><input class="form-control" type="number" min="0" max="90" name="discountPercent" required value="${p ? p.discountPercent : 20}"></div>
      <div class="col-md-4"><label class="form-label">Start *</label><input class="form-control" type="datetime-local" name="startDate" required value="${loc(p?.startDate)}"></div>
      <div class="col-md-4"><label class="form-label">End *</label><input class="form-control" type="datetime-local" name="endDate" required value="${loc(p?.endDate)}"></div>
      <div class="col-12"><label class="form-label">Banner image URL (optional)</label><input class="form-control" name="bannerUrl" value="${BB.esc(p?.bannerUrl)}"></div>
      <div class="col-12 form-check ms-2"><input class="form-check-input" type="checkbox" name="enabled" id="pen" ${!p || p.enabled ? 'checked' : ''}><label class="form-check-label" for="pen">Enabled</label></div>
      <div class="col-12"><label class="form-label">Products in this ${brand ? 'deal' : 'sale'}</label><div style="max-height:200px;overflow:auto;border:1px solid #dfe5ec;border-radius:8px;padding:8px">${prods.map(x => `<div class="form-check"><input class="form-check-input" type="checkbox" value="${x.id}" id="pp${x.id}" ${sel.has(x.id) ? 'checked' : ''}><label class="form-check-label small" for="pp${x.id}">${BB.esc(x.name)} <span class="text-muted">(${BB.esc(x.brand || '')})</span></label></div>`).join('')}</div></div></form>`,
      { size: 'modal-lg', footer: '<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button class="btn btn-bb" id="pfs">Save</button>' });
    $('#pfs', m.el).onclick = async e => {
      const f = $('#pfm', m.el); if (!f.reportValidity()) return; const fd = new FormData(f);
      const body = { type, name: fd.get('name'), brandName: fd.get('brandName') || '', description: fd.get('description'), bannerUrl: fd.get('bannerUrl'), discountPercent: +fd.get('discountPercent'), startDate: fd.get('startDate'), endDate: fd.get('endDate'), enabled: !!fd.get('enabled'), productIds: BB.$$('input[type=checkbox][value]', f).filter(c => c.checked).map(c => +c.value) };
      BB.spin(e.target, true); try { await BB.api(p ? '/api/admin/promotions/' + p.id : '/api/admin/promotions', { method: p ? 'PUT' : 'POST', body }); m.close(); BB.toast('Saved'); BB.promoPage(el, type); } catch (x) { BB.fail(x); BB.spin(e.target, false); }
    };
  };
  $('#np', el).onclick = () => open(null);
  BB.$$('[data-e]', el).forEach(b => b.onclick = () => open(list.find(x => x.id == b.dataset.e)));
  BB.$$('[data-x]', el).forEach(b => b.onclick = async () => { if (await BB.confirmBox('Delete this?', 'Delete')) { try { await BB.api('/api/admin/promotions/' + b.dataset.x, { method: 'DELETE' }); BB.promoPage(el, type); } catch (x) { BB.fail(x); } } });
};
