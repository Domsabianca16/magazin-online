import { useEffect, useMemo, useState } from 'react'
import './App.css'

const API = import.meta.env.VITE_API_URL || 'http://localhost:8081/api'
const money = value => new Intl.NumberFormat('ro-RO', { style: 'currency', currency: 'RON', maximumFractionDigits: 0 }).format(value || 0)
const read = key => { try { return JSON.parse(localStorage.getItem(key)) || [] } catch { return [] } }
const getOrderKey = () => { let key = localStorage.getItem('atelier-pending-order-key'); if (!key) { key = crypto.randomUUID(); localStorage.setItem('atelier-pending-order-key', key) } return key }
const basicHeader = (username, password) => `Basic ${btoa(String.fromCharCode(...new TextEncoder().encode(`${username}:${password}`)))}`

function App() {
  const [products, setProducts] = useState([])
  const [cart, setCart] = useState(() => read('boutique-cart'))
  const [favorites, setFavorites] = useState(() => read('boutique-favorites'))
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState('featured')
  const [category, setCategory] = useState('All pieces')
  const [page, setPage] = useState(() => window.location.pathname.replace(/\/+$/, '') === '/admin' ? 'admin' : 'shop')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [apiError, setApiError] = useState(false)
  const [mobileMenu, setMobileMenu] = useState(false)
  const [checkout, setCheckout] = useState(false)
  const [submittingOrder, setSubmittingOrder] = useState(false)
  const [orderKey, setOrderKey] = useState(getOrderKey)
  const [orderConfirmation, setOrderConfirmation] = useState(null)
  const [form, setForm] = useState({ name: '', email: '', phone: '', address: '', city: '', postal: '' })
  const [adminAuth, setAdminAuth] = useState(null)
  const [adminCredentials, setAdminCredentials] = useState({ username: '', password: '' })
  const [adminBusy, setAdminBusy] = useState(false)
  const [adminDraft, setAdminDraft] = useState({ name: '', description: '', price: '', stock: '', imageUrl: '', sku: '', category: '', brand: '', material: '', active: true })
  const [editingProductId, setEditingProductId] = useState(null)
  const [adminTab, setAdminTab] = useState('products')
  const [adminOrders, setAdminOrders] = useState([])
  const [adminCustomers, setAdminCustomers] = useState([])

  useEffect(() => {
    fetch(`${API}/products`).then(r => { if (!r.ok) throw Error(); return r.json() })
      .then(data => { setProducts(Array.isArray(data) ? data : []); setApiError(false) })
      .catch(() => setApiError(true)).finally(() => setLoading(false))
  }, [])
  useEffect(() => {
    const isAdminPath = window.location.pathname.replace(/\/+$/, '') === '/admin'
    if (page === 'admin' && !isAdminPath) window.history.pushState({}, '', '/admin')
    else if (page !== 'admin' && isAdminPath) window.history.pushState({}, '', '/')
  }, [page])
  useEffect(() => { const syncRoute = () => setPage(window.location.pathname.replace(/\/+$/, '') === '/admin' ? 'admin' : 'shop'); window.addEventListener('popstate', syncRoute); return () => window.removeEventListener('popstate', syncRoute) }, [])
  useEffect(() => { localStorage.setItem('boutique-cart', JSON.stringify(cart)) }, [cart])
  useEffect(() => { localStorage.setItem('boutique-favorites', JSON.stringify(favorites)) }, [favorites])

  const categories = useMemo(() => ['All pieces', ...new Set(products.map(p => p.category?.name || p.category).filter(Boolean))], [products])
  const shown = useMemo(() => {
    const result = products.filter(p => p.active !== false && (category === 'All pieces' || (p.category?.name || p.category) === category) && `${p.name} ${p.description}`.toLowerCase().includes(query.toLowerCase()))
    if (sort === 'low') result.sort((a,b) => a.price - b.price)
    if (sort === 'high') result.sort((a,b) => b.price - a.price)
    if (sort === 'name') result.sort((a,b) => a.name.localeCompare(b.name))
    return result
  }, [products, category, query, sort])
  const count = cart.reduce((sum, item) => sum + item.quantity, 0)
  const subtotal = cart.reduce((sum, item) => sum + item.price * item.quantity, 0)
  const shipping = subtotal === 0 || subtotal >= 300 ? 0 : 19
  const toast = message => { setNotice(message); setTimeout(() => setNotice(''), 2600) }
  const add = p => { if ((p.stock ?? 99) < 1) return toast('This piece is currently out of stock.'); setCart(prev => { const exists = prev.find(i => i.id === p.id); if (exists && exists.quantity >= (p.stock ?? 99)) { toast('You’ve reached the available stock.'); return prev } return exists ? prev.map(i => i.id === p.id ? { ...i, quantity: i.quantity + 1 } : i) : [...prev, { ...p, quantity: 1 }] }); toast('Added to your bag.') }
  const changeQty = (id, delta) => setCart(prev => prev.map(i => i.id === id ? { ...i, quantity: Math.max(1, Math.min(i.stock ?? 99, i.quantity + delta)) } : i))
  const toggleFavorite = p => setFavorites(prev => prev.some(i => i.id === p.id) ? prev.filter(i => i.id !== p.id) : [...prev, p])

  const adminLogin = async e => {
    e.preventDefault(); setAdminBusy(true)
    const authorization = basicHeader(adminCredentials.username.trim(), adminCredentials.password)
    try {
      const response = await fetch(`${API}/admin/session`, { headers: { Authorization: authorization } })
      if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? 'Datele de admin sunt incorecte sau nu sunt configurate în backend.' : 'Nu am putut verifica autentificarea.')
      const [productResponse, orderResponse, customerResponse] = await Promise.all([
        fetch(`${API}/admin/products`, { headers: { Authorization: authorization } }),
        fetch(`${API}/admin/orders`, { headers: { Authorization: authorization } }),
        fetch(`${API}/admin/customers`, { headers: { Authorization: authorization } }),
      ])
      if (!productResponse.ok || !orderResponse.ok || !customerResponse.ok) throw new Error('Nu am putut încărca datele de administrare.')
      setProducts(await productResponse.json()); setAdminOrders(await orderResponse.json()); setAdminCustomers(await customerResponse.json()); setAdminAuth({ username: adminCredentials.username.trim(), authorization }); setAdminCredentials({ username: '', password: '' }); toast('Autentificare reușită.')
    } catch (error) { toast(error.message || 'Backendul nu poate fi contactat.') } finally { setAdminBusy(false) }
  }

  const adminJson = async (path, options = {}) => {
    const response = await fetch(`${API}/admin/${path}`, { ...options, headers: { Authorization: adminAuth?.authorization, ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers } })
    const body = await response.json().catch(() => null)
    if (!response.ok) throw new Error(body?.message || body?.detail || 'Operația nu a putut fi finalizată.')
    return body
  }
  const changeOrderStatus = async (id, status) => { try { const updated = await adminJson(`orders/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }); setAdminOrders(prev => prev.map(order => order.id === id ? updated : order)); toast('Statusul comenzii a fost actualizat.') } catch (error) { toast(error.message) } }
  const markOrderPaid = async id => { try { const updated = await adminJson(`orders/${id}/payment`, { method: 'PATCH' }); setAdminOrders(prev => prev.map(order => order.id === id ? updated : order)); toast('Încasarea a fost înregistrată.') } catch (error) { toast(error.message) } }
  const refundOrder = async order => { if (!window.confirm(`Confirmi că ai returnat manual ${money(order.totalCents / 100)}? Acțiunea va marca plata ca rambursată.`)) return; const note = window.prompt('Notă despre transferul/rambursarea efectuată (opțional):'); if(note===null)return; try { const updated = await adminJson(`orders/${order.id}/refund`, { method: 'POST', body: JSON.stringify({ note }) }); setAdminOrders(prev => prev.map(item => item.id === order.id ? updated : item)); toast('Rambursarea manuală a fost înregistrată.') } catch (error) { toast(error.message) } }
  const reloadAdminData = async () => { if (!adminAuth) return; setAdminBusy(true); try { const [p,o,c]=await Promise.all([adminJson('products'),adminJson('orders'),adminJson('customers')]);setProducts(p);setAdminOrders(o);setAdminCustomers(c) } catch(error) { toast(error.message) } finally { setAdminBusy(false) } }

  const saveAdminProduct = async e => {
    e.preventDefault(); if (!adminAuth || adminBusy) return; setAdminBusy(true)
    const payload = { ...adminDraft, price: Number(adminDraft.price), stock: Number(adminDraft.stock) }
    try {
      const response = await fetch(`${API}/products${editingProductId ? `/${editingProductId}` : ''}`, { method: editingProductId ? 'PUT' : 'POST', headers: { Authorization: adminAuth.authorization, 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
      const result = response.status === 204 ? null : await response.json().catch(() => null)
      if (!response.ok) throw new Error(result?.message || result?.detail || 'Produsul nu a putut fi salvat.')
      if (editingProductId) setProducts(prev => prev.map(p => p.id === editingProductId ? result : p)); else setProducts(prev => [...prev, result])
      setAdminDraft({ name: '', description: '', price: '', stock: '', imageUrl: '', sku: '', category: '', brand: '', material: '', active: true }); setEditingProductId(null); toast(editingProductId ? 'Produs actualizat.' : 'Produs adăugat.')
    } catch (error) { toast(error.message || 'Backendul nu poate fi contactat.') } finally { setAdminBusy(false) }
  }
  const deleteAdminProduct = async product => {
    if (!adminAuth || !window.confirm(`Ștergi produsul „${product.name}”?`)) return
    setAdminBusy(true)
    try { const response = await fetch(`${API}/products/${product.id}`, { method: 'DELETE', headers: { Authorization: adminAuth.authorization } }); if (!response.ok) throw new Error('Produsul nu a putut fi șters.'); setProducts(prev => prev.filter(p => p.id !== product.id)); if (editingProductId === product.id) { setEditingProductId(null); setAdminDraft({ name: '', description: '', price: '', stock: '', imageUrl: '' }) } toast('Produs șters.') } catch (error) { toast(error.message || 'Backendul nu poate fi contactat.') } finally { setAdminBusy(false) }
  }
  const startEditingProduct = product => { setEditingProductId(product.id); setAdminDraft({ name: product.name || '', description: product.description || '', price: String(product.price ?? ''), stock: String(product.stock ?? 0), imageUrl: product.imageUrl || '', sku: product.sku || '', category: product.category || '', brand: product.brand || '', material: product.material || '', active: product.active !== false }); window.scrollTo({ top: 0, behavior: 'smooth' }) }

  const submitOrder = async e => {
    e.preventDefault()
    if (submittingOrder || cart.length === 0) return
    setSubmittingOrder(true)
    try {
      const response = await fetch(`${API}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': orderKey },
        body: JSON.stringify({ ...form, paymentMethod: 'CASH_ON_DELIVERY', items: cart.map(item => ({ productId: item.id, quantity: item.quantity })) }),
      })
      const result = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(result.message || result.detail || result.error || (response.status === 409 ? 'Some items are no longer available in the requested quantity.' : `The server returned ${response.status}. Check the backend is running and restart it after updating.`))
      setOrderConfirmation(result)
      setCart([])
      setCheckout(false)
      localStorage.removeItem('atelier-pending-order-key')
      const nextKey = crypto.randomUUID()
      localStorage.setItem('atelier-pending-order-key', nextKey)
      setOrderKey(nextKey)
    } catch (error) {
      toast(error.message || 'The store could not be reached. Your bag is saved; please try again.')
    } finally { setSubmittingOrder(false) }
  }

  return <div className="site-shell">
    <div className="announcement">Complimentary delivery on orders over 300 lei <span>·</span> Thoughtfully selected for everyday living</div>
    <header className="header">
      <button className="menu-toggle" onClick={() => setMobileMenu(!mobileMenu)} aria-label="Toggle navigation">☰</button>
      <a className="wordmark" href="#home" onClick={() => setPage('home')}>ATELIER<span>.</span></a>
      <nav className={mobileMenu ? 'nav open' : 'nav'}><button onClick={() => {setPage('shop');setCategory('All pieces');setMobileMenu(false)}}>Shop</button><button onClick={() => {setPage('shop');setCategory(categories[1] || 'All pieces');setMobileMenu(false)}}>Collections</button><button onClick={() => {setPage('about');setMobileMenu(false)}}>Our story</button></nav>
      <div className="header-actions"><label className="search"><span>⌕</span><input aria-label="Search products" placeholder="Search pieces" value={query} onChange={e => {setQuery(e.target.value);setPage('shop')}} />{query && <button onClick={() => setQuery('')} aria-label="Clear search">×</button>}</label><button className="icon-action" title="Favorites" onClick={() => setPage('favorites')}>♡ <span className="desktop-label">Saved</span><i>{favorites.length}</i></button><button className="icon-action bag-action" onClick={() => setPage('bag')}>Bag <i>{count}</i></button></div>
    </header>
    {page === 'home' && <section className="hero"><div className="hero-image"/><div className="hero-copy"><span className="eyebrow">THE EVERYDAY EDIT · 2025</span><h1>Made for the<br/><em>moments in between.</em></h1><p>Considered objects, natural textures, and little luxuries for a slower kind of living.</p><button className="dark-button" onClick={() => setPage('shop')}>Explore the collection <span>↗</span></button></div><div className="hero-caption">01 — 04 &nbsp; / &nbsp; OBJECTS FOR LIVING</div></section>}
    {page === 'about' && <section className="editorial"><span className="eyebrow">A NOTE FROM THE STUDIO</span><h1>Less, but <em>better.</em></h1><p>Atelier is a small collection of useful, beautiful things for everyday life. We look for honest materials, thoughtful details, and pieces made to stay with you.</p><button className="dark-button" onClick={() => setPage('shop')}>Discover the edit ↗</button></section>}
    <main className="main-content">
      {page === 'admin' ? <section className="admin-page"><div className="page-heading"><div><span className="eyebrow">ATELIER · BACK OFFICE</span><h1>Administrare</h1></div>{adminAuth && <button className="text-link" onClick={() => {setAdminAuth(null);setAdminOrders([]);setAdminCustomers([]);setEditingProductId(null);setAdminTab('products');setAdminDraft({name:'',description:'',price:'',stock:'',imageUrl:'',sku:'',category:'',brand:'',material:'',active:true});toast('Ai ieșit din contul de admin.')}}>Ieșire ↗</button>}</div>{!adminAuth ? <form className="admin-login" onSubmit={adminLogin}><span className="eyebrow">ACCES ADMINISTRATOR</span><h2>Autentificare</h2><p>Folosește ADMIN_USERNAME și ADMIN_PASSWORD configurate pentru backend.</p><label>Nume utilizator<input autoComplete="username" required value={adminCredentials.username} onChange={e=>setAdminCredentials({...adminCredentials,username:e.target.value})}/></label><label>Parolă<input type="password" autoComplete="current-password" required value={adminCredentials.password} onChange={e=>setAdminCredentials({...adminCredentials,password:e.target.value})}/></label><button className="dark-button" disabled={adminBusy}>{adminBusy?'Se verifică…':'Intră în administrare ↗'}</button></form> : <><div className="admin-overview"><div><span>Produse active</span><strong>{products.filter(p=>p.active!==false).length}</strong></div><div><span>Comenzi</span><strong>{adminOrders.length}</strong></div><div><span>Cumpărători</span><strong>{adminCustomers.length}</strong></div><div><span>Cont conectat</span><strong>{adminAuth.username}</strong></div></div><div className="admin-tabs" role="tablist"><button className={adminTab==='products'?'active':''} onClick={()=>setAdminTab('products')}>Produse</button><button className={adminTab==='orders'?'active':''} onClick={()=>setAdminTab('orders')}>Comenzi ({adminOrders.length})</button><button className={adminTab==='customers'?'active':''} onClick={()=>setAdminTab('customers')}>Cumpărători ({adminCustomers.length})</button><button className="admin-refresh" onClick={reloadAdminData} disabled={adminBusy}>Reîncarcă ↻</button></div>{adminTab==='products' ? <div className="admin-layout"><form className="admin-product-form" onSubmit={saveAdminProduct}><span className="eyebrow">{editingProductId?'EDITEAZĂ PRODUS':'CATALOG'}</span><h2>{editingProductId?'Actualizează produsul':'Adaugă un produs'}</h2><label>Nume produs<input required maxLength={160} value={adminDraft.name} onChange={e=>setAdminDraft({...adminDraft,name:e.target.value})}/></label><div className="admin-fields"><label>SKU<input maxLength={80} value={adminDraft.sku} onChange={e=>setAdminDraft({...adminDraft,sku:e.target.value})}/></label><label>Categorie<input maxLength={100} value={adminDraft.category} onChange={e=>setAdminDraft({...adminDraft,category:e.target.value})}/></label></div><div className="admin-fields"><label>Brand<input maxLength={120} value={adminDraft.brand} onChange={e=>setAdminDraft({...adminDraft,brand:e.target.value})}/></label><label>Material<input maxLength={120} value={adminDraft.material} onChange={e=>setAdminDraft({...adminDraft,material:e.target.value})}/></label></div><label>Descriere<textarea rows={4} maxLength={2000} value={adminDraft.description} onChange={e=>setAdminDraft({...adminDraft,description:e.target.value})}/></label><div className="admin-fields"><label>Preț (lei)<input type="number" min="0" step="0.01" required value={adminDraft.price} onChange={e=>setAdminDraft({...adminDraft,price:e.target.value})}/></label><label>Stoc<input type="number" min="0" step="1" required value={adminDraft.stock} onChange={e=>setAdminDraft({...adminDraft,stock:e.target.value})}/></label></div><label>URL imagine<input type="url" placeholder="https://…" value={adminDraft.imageUrl} onChange={e=>setAdminDraft({...adminDraft,imageUrl:e.target.value})}/></label><label className="admin-active-toggle"><input type="checkbox" checked={adminDraft.active} onChange={e=>setAdminDraft({...adminDraft,active:e.target.checked})}/> Produs vizibil în magazin</label><div className="admin-form-actions"><button className="dark-button" disabled={adminBusy}>{adminBusy?'Se salvează…':editingProductId?'Salvează modificările':'Adaugă produsul'}</button>{editingProductId&&<button type="button" className="text-link" onClick={()=>{setEditingProductId(null);setAdminDraft({name:'',description:'',price:'',stock:'',imageUrl:'',sku:'',category:'',brand:'',material:'',active:true})}}>Anulează</button>}</div></form><div className="admin-inventory"><div className="admin-inventory-head"><div><span className="eyebrow">INVENTAR</span><h2>Produse</h2></div></div>{products.length===0?<p className="admin-empty">Nu există produse încă. Adaugă primul produs.</p>:<div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Produs</th><th>SKU / categorie</th><th>Preț</th><th>Stoc</th><th>Vizibil</th><th>Acțiuni</th></tr></thead><tbody>{products.map(product=><tr key={product.id}><td><div className="admin-product-cell">{product.imageUrl&&<img src={product.imageUrl} alt=""/>}<div><strong>{product.name}</strong><small>{product.brand||product.description||'—'}</small></div></div></td><td>{product.sku||'—'}<small className="admin-secondary">{product.category||'Fără categorie'}</small></td><td>{money(product.price)}</td><td><span className={Number(product.stock)>0?'stock-pill':'stock-pill empty-stock'}>{product.stock??0}</span></td><td>{product.active===false?'Nu':'Da'}</td><td><div className="admin-row-actions"><button onClick={()=>startEditingProduct(product)} disabled={adminBusy}>Editează</button><button onClick={()=>deleteAdminProduct(product)} disabled={adminBusy}>Șterge</button></div></td></tr>)}</tbody></table></div>}</div></div> : adminTab==='orders' ? <div className="admin-wide-panel"><div className="admin-inventory-head"><div><span className="eyebrow">VÂNZĂRI</span><h2>Comenzi</h2></div><p className="admin-helper">Plata este ramburs. Rambursarea se înregistrează după ce transferi banii clientului.</p></div>{adminOrders.length===0?<p className="admin-empty">Nu există comenzi încă.</p>:<div className="admin-table-wrap"><table className="admin-table order-table"><thead><tr><th>Comandă / dată</th><th>Cumpărător</th><th>Produse</th><th>Total</th><th>Status comandă</th><th>Plată</th></tr></thead><tbody>{adminOrders.map(order=><tr key={order.id}><td><strong>#{order.id}</strong><small className="admin-secondary">{new Date(order.createdAt).toLocaleString('ro-RO')}</small></td><td><strong>{order.customerName}</strong><small className="admin-secondary">{order.email}<br/>{order.phone}<br/>{order.address}, {order.city} {order.postalCode}</small></td><td>{order.items.map(item=><div className="order-line" key={item.productId}>{item.name} × {item.quantity}<small>{money(item.unitPriceCents/100)} / buc.</small></div>)}</td><td><strong>{money(order.totalCents/100)}</strong></td><td><select aria-label={'Status comandă '+order.id} value={order.status} onChange={e=>changeOrderStatus(order.id,e.target.value)}>{['PENDING','PROCESSING','SHIPPED','DELIVERED','CANCELLED'].map(status=><option key={status} value={status}>{({PENDING:'Nouă',PROCESSING:'În pregătire',SHIPPED:'Expediată',DELIVERED:'Livrată',CANCELLED:'Anulată'})[status]}</option>)}</select></td><td><div className="payment-cell"><strong>{order.paymentStatus==='REFUNDED'?'Rambursată':order.paymentStatus==='PAID'?'Încasată':'Neridicată'}</strong>{order.paymentStatus==='UNPAID'&&order.status==='DELIVERED'&&<button onClick={()=>markOrderPaid(order.id)}>Marchează încasată</button>}{order.paymentStatus==='PAID'&&<button onClick={()=>refundOrder(order)}>Înregistrează rambursarea</button>}{order.refundNote&&<small>{order.refundNote}</small>}</div></td></tr>)}</tbody></table></div>}</div> : <div className="admin-wide-panel"><div className="admin-inventory-head"><div><span className="eyebrow">CLIENȚI</span><h2>Cumpărători</h2></div><p className="admin-helper">Grupați după email din comenzile plasate. Magazinul nu are conturi de client.</p></div>{adminCustomers.length===0?<p className="admin-empty">Nu există cumpărători înregistrați până acum.</p>:<div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Nume</th><th>Email / telefon</th><th>Comenzi</th><th>Valoare comandată</th><th>Ultima comandă</th></tr></thead><tbody>{adminCustomers.map(customer=><tr key={customer.email}><td><strong>{customer.name}</strong></td><td>{customer.email}<small className="admin-secondary">{customer.phone}</small></td><td>{customer.orderCount}</td><td>{money(customer.orderValueCents/100)}</td><td>{new Date(customer.lastOrderAt).toLocaleDateString('ro-RO')}</td></tr>)}</tbody></table></div>}</div>}</>}</section>
      : page === 'bag' ? <section className="bag-page"><div className="page-heading"><div><span className="eyebrow">YOUR SELECTION</span><h1>Your bag <span>({count})</span></h1></div><button className="text-link" onClick={() => setPage('shop')}>Continue shopping ↗</button></div>{orderConfirmation && <div className="order-confirmation"><span className="eyebrow">ORDER RECEIVED</span><h2>Thank you, {form.name}.</h2><p>Your order <strong>#{orderConfirmation.id}</strong> is saved. Pay {money(orderConfirmation.totalCents / 100)} in cash to the courier when it arrives.</p><button className="dark-button" onClick={() => {setOrderConfirmation(null);setPage('shop')}}>Continue shopping</button></div>}{cart.length === 0 && !orderConfirmation ? <div className="empty"><div className="empty-icon">○</div><h2>Your bag is taking a little breather.</h2><p>Find a piece you’ll love having around.</p><button className="dark-button" onClick={() => setPage('shop')}>Explore the collection</button></div> : cart.length > 0 && <div className="bag-layout"><div className="bag-items">{cart.map(item => <article className="bag-item" key={item.id}><img src={item.imageUrl || 'https://images.unsplash.com/photo-1490312278390-ab64016e0aa9?auto=format&fit=crop&w=400&q=80'} alt={item.name}/><div className="bag-info"><h3>{item.name}</h3><p>{money(item.price)}</p><div className="quantity"><button onClick={() => changeQty(item.id,-1)} aria-label="Decrease quantity">−</button><span>{item.quantity}</span><button onClick={() => changeQty(item.id,1)} aria-label="Increase quantity">+</button></div><button className="remove" onClick={() => setCart(c => c.filter(i => i.id !== item.id))}>Remove</button></div><strong>{money(item.price * item.quantity)}</strong></article>)}</div><aside className="summary"><h2>Order summary</h2><div><span>Subtotal</span><span>{money(subtotal)}</span></div><div><span>Delivery</span><span>{shipping ? money(shipping) : 'Complimentary'}</span></div><p className="shipping-note">{subtotal >= 300 ? 'Your order ships free.' : `Spend ${money(Math.max(0,300-subtotal))} more for complimentary delivery.`}</p><div className="summary-total"><span>Total</span><strong>{money(subtotal + shipping)}</strong></div><button className="dark-button full" onClick={() => setCheckout(!checkout)}>Continue to checkout ↗</button><small>Cash on delivery · No advance payment</small></aside></div>}
        {checkout && cart.length > 0 && <form className="checkout-box" onSubmit={submitOrder}><span className="eyebrow">DELIVERY DETAILS</span><h2>Where should we send it?</h2><div className="checkout-grid">{[['name','Full name'],['email','Email address'],['phone','Phone number'],['address','Street address'],['city','City'],['postal','Postal code']].map(([key,label])=><label key={key}>{label}<input required maxLength={key==='address'?240:120} type={key==='email'?'email':'text'} value={form[key]} onChange={e=>setForm({...form,[key]:e.target.value})}/></label>)}</div><p className="dev-note">Cash on delivery: pay the courier when your order arrives. No online payment is taken.</p><button className="dark-button" type="submit" disabled={submittingOrder}>{submittingOrder?'Placing your order…':'Place order · Cash on delivery'}</button></form>}</section>
      : page === 'favorites' ? <section><div className="page-heading"><div><span className="eyebrow">KEPT CLOSE</span><h1>Your saved pieces <span>({favorites.length})</span></h1></div><button className="text-link" onClick={() => setPage('shop')}>Back to the shop ↗</button></div>{favorites.length ? <div className="product-grid">{favorites.map(p => <ProductCard key={p.id} p={p} favorite add={add} toggle={toggleFavorite}/>)}</div> : <div className="empty"><h2>Keep the pieces you love close.</h2><p>Tap the heart on anything that catches your eye.</p><button className="dark-button" onClick={() => setPage('shop')}>Explore the collection</button></div>}</section>
      : <section className="shop-section"><div className="section-top"><div><span className="eyebrow">OBJECTS WITH INTENTION</span><h2>{query ? `Search results for “${query}”` : 'The considered collection'}</h2><p>Everyday essentials, chosen to be kept.</p></div><div className="sort-control"><label htmlFor="sort">Sort by</label><select id="sort" value={sort} onChange={e => setSort(e.target.value)}><option value="featured">Featured</option><option value="low">Price: low to high</option><option value="high">Price: high to low</option><option value="name">Name</option></select></div></div><div className="category-row">{categories.map(c => <button key={c} className={category===c?'active':''} onClick={() => setCategory(c)}>{c}</button>)}</div>{apiError && <div className="api-notice"><strong>Store connection unavailable</strong><span>Couldn’t reach {API}/products. Start the backend and database to load the live catalogue.</span><button onClick={() => {setLoading(true);fetch(`${API}/products`).then(r=>r.json()).then(setProducts).catch(()=>setApiError(true)).finally(()=>setLoading(false))}}>Retry</button></div>}{loading ? <div className="loading-grid">{[1,2,3,4].map(i=><div className="skeleton" key={i}/>)}</div> : shown.length ? <div className="product-grid">{shown.map(p=><ProductCard key={p.id} p={p} favorite={favorites.some(i=>i.id===p.id)} add={add} toggle={toggleFavorite}/>)}</div> : !apiError && <div className="empty"><h2>{query ? 'No pieces found.' : 'The collection is on its way.'}</h2><p>{query ? 'Try a different search.' : 'Products will appear here as soon as the catalogue is connected.'}</p></div>}</section>}
    </main>
    <section className="service-strip"><div><span>01</span><strong>Thoughtful by nature</strong><p>Better materials, considered details.</p></div><div><span>02</span><strong>Made to be kept</strong><p>Pieces that earn their place at home.</p></div><div><span>03</span><strong>Here when you need us</strong><p>Real people, ready to help.</p></div></section>
    <footer><div className="footer-main"><div><a className="wordmark" href="#home" onClick={() => setPage('home')}>ATELIER<span>.</span></a><p>A little more considered.<br/>A little more everyday.</p></div><div><strong>Explore</strong><button onClick={() => setPage('shop')}>All pieces</button><button onClick={() => setPage('about')}>Our story</button><button onClick={() => toast('Contact: hello@atelier.example')}>Contact</button></div><div><strong>We’re here to help</strong><button onClick={() => toast('Shipping is complimentary over 300 lei. Returns are accepted within 30 days.')}>Shipping & returns</button><button onClick={() => toast('Questions? Write to hello@atelier.example')}>FAQs</button><button onClick={() => toast('Privacy details will be published before launch.')}>Privacy</button></div><div className="newsletter"><strong>Notes from the studio</strong><p>Occasional updates, never noise.</p><form onSubmit={e=>{e.preventDefault();toast('Thanks for joining our list.');e.target.reset()}}><input type="email" placeholder="Your email address" required/><button aria-label="Subscribe">↗</button></form></div></div><div className="footer-bottom"><span>© 2025 Atelier Studio</span><span>Designed for everyday living · Romania</span><span>Instagram &nbsp; Pinterest</span></div></footer>
    {notice && <div role="status" className="toast">{notice}</div>}
  </div>
}

function ProductCard({p,favorite,add,toggle}) { const image=p.imageUrl || 'https://images.unsplash.com/photo-1490312278390-ab64016e0aa9?auto=format&fit=crop&w=900&q=85'; return <article className="product-card"><div className="product-image"><img src={image} alt={p.name} loading="lazy"/><button className={favorite?'heart active':'heart'} onClick={()=>toggle(p)} aria-label={favorite?'Remove from saved pieces':'Save piece'}>{favorite?'♥':'♡'}</button>{p.stock === 0 && <span className="sold-out">Sold out</span>}</div><div className="product-meta"><div><h3>{p.name}</h3><p>{p.category?.name || p.category || 'THE EVERYDAY EDIT'}</p></div><strong>{money(p.price)}</strong></div><button className="quick-add" onClick={()=>add(p)} disabled={p.stock===0}>{p.stock===0?'Currently unavailable':'Add to bag'} <span>+</span></button></article> }

export default App
