const API = 'https://vxd.mobi/aequilibrium/api/v1';
const euro = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
const $ = (id) => document.getElementById(id);

const state = {
  city: null,
  categories: [],
  services: [],
  debounce: null,
};

function setStatus(message, error = false) {
  $('status').textContent = message || '';
  $('status').style.color = error ? '#ff8a8a' : '';
}

async function api(path, params = {}) {
  const url = new URL(`${API}/${path}`);
  Object.entries(params).forEach(([k, v]) => {
    if (v !== '' && v != null) url.searchParams.set(k, v);
  });
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  let body = null;
  try { body = await response.json(); } catch {}
  if (!response.ok || !body?.ok) throw new Error(body?.error || `HTTP ${response.status}`);
  return body;
}

function updateButton() {
  $('estimateButton').disabled = !($('service').value && state.city?.slug);
}

async function loadCategories() {
  try {
    const result = await api('categorie.php');
    state.categories = result.data;
    $('category').innerHTML = '<option value="">Seleziona categoria</option>' + result.data.map(c => `<option value="${escapeHtml(c.slug)}">${escapeHtml(c.nome)} (${c.servizi})</option>`).join('');
  } catch (error) {
    $('category').innerHTML = '<option value="">Errore caricamento</option>';
    setStatus('API non raggiungibile. Carica prima la patch server.', true);
  }
}

async function loadServices(category) {
  $('service').disabled = true;
  $('service').innerHTML = '<option value="">Caricamento…</option>';
  try {
    const result = await api('servizi.php', { categoria: category, limit: 300 });
    state.services = result.data;
    $('service').innerHTML = '<option value="">Seleziona servizio</option>' + result.data.map(s => `<option value="${escapeHtml(s.slug)}">${escapeHtml(s.nome)}</option>`).join('');
    $('service').disabled = false;
  } catch {
    $('service').innerHTML = '<option value="">Errore caricamento</option>';
    setStatus('Non riesco a caricare i servizi.', true);
  }
  updateButton();
}

async function searchCities(query) {
  try {
    const result = await api('comuni.php', { q: query, limit: 20 });
    renderCities(result.data);
  } catch {
    $('cityResults').hidden = true;
  }
}

function renderCities(cities) {
  const box = $('cityResults');
  if (!cities?.length) { box.hidden = true; return; }
  box.innerHTML = cities.map((c, i) => `<div class="result-item" data-index="${i}"><strong>${escapeHtml(c.nome)}</strong><span>${escapeHtml(c.provincia)} · ${escapeHtml(c.regione)}</span></div>`).join('');
  box.hidden = false;
  box.querySelectorAll('.result-item').forEach((item) => {
    item.addEventListener('click', () => selectCity(cities[Number(item.dataset.index)]));
  });
}

function selectCity(city) {
  state.city = city;
  $('city').value = city.nome;
  $('cityResults').hidden = true;
  $('selectedCity').textContent = `${city.nome} (${city.provincia}) · ${city.regione}`;
  $('selectedCity').hidden = false;
  localStorage.setItem('aeq_city', JSON.stringify(city));
  updateButton();
}

async function loadEstimate() {
  const serviceSlug = $('service').value;
  if (!serviceSlug || !state.city?.slug) return;
  setStatus('Calcolo della stima…');
  $('estimateButton').disabled = true;
  const cacheKey = `aeq_estimate:${serviceSlug}:${state.city.slug}`;
  try {
    const result = await api('stima.php', { servizio: serviceSlug, comune: state.city.slug });
    localStorage.setItem(cacheKey, JSON.stringify({ savedAt: Date.now(), data: result.data }));
    renderEstimate(result.data, false);
    saveRecent(result.data);
    setStatus('');
  } catch (error) {
    const cached = safeParse(localStorage.getItem(cacheKey));
    if (cached?.data) {
      renderEstimate(cached.data, true);
      setStatus('Connessione assente: mostro l’ultima stima salvata sul dispositivo.');
    } else {
      setStatus(error.message === 'estimate_not_found' ? 'Stima non disponibile per questa combinazione.' : 'Errore di collegamento al motore Aequilibrium.', true);
    }
  } finally {
    updateButton();
  }
}

function renderEstimate(data, cached) {
  $('estimateService').textContent = data.servizio.nome;
  $('estimateCity').textContent = `${data.comune.nome} (${data.comune.provincia})`;
  $('priceAvg').textContent = euro.format(data.prezzi.medio);
  $('priceMin').textContent = euro.format(data.prezzi.min);
  $('priceMax').textContent = euro.format(data.prezzi.max);
  $('reliability').textContent = `Affidabilità ${data.affidabilita.label}${cached ? ' · offline' : ''}`;
  $('updatedAt').textContent = data.updated_at ? `Agg. ${formatDate(data.updated_at)}` : '';
  $('webLink').href = data.web_url;
  $('estimate').hidden = false;
  $('estimate').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function saveRecent(data) {
  const key = 'aeq_recents';
  let list = safeParse(localStorage.getItem(key)) || [];
  const id = `${data.servizio.slug}:${data.comune.slug}`;
  list = list.filter(x => x.id !== id);
  list.unshift({ id, data, at: Date.now() });
  list = list.slice(0, 8);
  localStorage.setItem(key, JSON.stringify(list));
  renderRecents();
}

function renderRecents() {
  const list = safeParse(localStorage.getItem('aeq_recents')) || [];
  $('recentsPanel').hidden = list.length === 0;
  $('recents').innerHTML = list.map((item, i) => `<button class="recent-item" data-index="${i}"><strong>${escapeHtml(item.data.servizio.nome)} · ${escapeHtml(item.data.comune.nome)}</strong><span>${euro.format(item.data.prezzi.medio)} · ${formatDate(item.at)}</span></button>`).join('');
  $('recents').querySelectorAll('.recent-item').forEach(btn => btn.addEventListener('click', () => renderEstimate(list[Number(btn.dataset.index)].data, true)));
}

async function shareCurrent() {
  if ($('estimate').hidden) return;
  const text = `${$('estimateService').textContent} a ${$('estimateCity').textContent}: ${$('priceAvg').textContent} di media su Aequilibrium.`;
  const url = $('webLink').href;
  try {
    if (window.Android?.share) { Android.share(text, url); return; }
    if (navigator.share) await navigator.share({ title: 'Aequilibrium', text, url });
    else await navigator.clipboard.writeText(`${text} ${url}`);
  } catch {}
}

function safeParse(value) { try { return JSON.parse(value); } catch { return null; } }
function formatDate(value) {
  const d = typeof value === 'number' ? new Date(value) : new Date(String(value).replace(' ', 'T'));
  return Number.isNaN(d.getTime()) ? '' : new Intl.DateTimeFormat('it-IT', { day:'2-digit', month:'2-digit', year:'numeric' }).format(d);
}
function escapeHtml(value) { return String(value ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c])); }

$('category').addEventListener('change', (e) => loadServices(e.target.value));
$('service').addEventListener('change', updateButton);
$('city').addEventListener('input', (e) => {
  state.city = null;
  $('selectedCity').hidden = true;
  updateButton();
  clearTimeout(state.debounce);
  state.debounce = setTimeout(() => searchCities(e.target.value.trim()), 180);
});
$('city').addEventListener('focus', () => { if ($('city').value.trim().length < 2) searchCities(''); });
$('estimateButton').addEventListener('click', loadEstimate);
$('shareButton').addEventListener('click', shareCurrent);
$('clearRecents').addEventListener('click', () => { localStorage.removeItem('aeq_recents'); renderRecents(); });
document.addEventListener('click', (e) => { if (!e.target.closest('.autocomplete')) $('cityResults').hidden = true; });

const savedCity = safeParse(localStorage.getItem('aeq_city'));
if (savedCity?.slug) selectCity(savedCity);
renderRecents();
loadCategories();

$('webLink').addEventListener('click', (e) => {
  if (window.Android?.openExternal) { e.preventDefault(); Android.openExternal($('webLink').href); }
});
