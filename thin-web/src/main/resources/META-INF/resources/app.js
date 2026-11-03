const state = {
  trips: [],
  selectedTrip: null,
  airlines: [],
  airports: new Map(),
};

const $ = (selector) => document.querySelector(selector);

const statusBox = $('#status');
const results = $('#results');
const bookingDialog = $('#booking-dialog');

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function showStatus(message, kind = 'info') {
  statusBox.textContent = message;
  statusBox.className = `status ${kind}`;
}

function hideStatus() {
  statusBox.className = 'status hidden';
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(data?.message || `请求失败 (${response.status})`);
  }
  return data;
}

async function loadRegistry() {
  try {
    const [airlines, airports] = await Promise.all([
      api('/api/airlines'),
      api('/api/airports'),
    ]);
    state.airlines = airlines;
    state.airports = new Map(airports.map((airport) => [airport.code, airport]));
    const container = $('#airline-options');
    container.innerHTML = state.airlines.map((airline) => `
      <label class="airline-chip">
        <input type="checkbox" value="${escapeHtml(airline.code)}" checked>
        ${escapeHtml(airline.name)} (${escapeHtml(airline.code)})
      </label>
    `).join('');
  } catch (error) {
    showStatus(`航司配置加载失败：${error.message}`, 'error');
  }
}

function timeText(value) {
  return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
}

function dateTimeText(value) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  });
}

function moneyText(value) {
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function airportLabel(code) {
  const airport = state.airports.get(code);
  return airport ? `${airport.city} ${code}` : code;
}

function tripRoute(trip) {
  const first = trip.legs[0];
  const last = trip.legs[trip.legs.length - 1];
  return `${airportLabel(first.origin)} → ${airportLabel(last.destination)}`;
}

function tripAirlines(trip) {
  return [...new Set(trip.legs.map((leg) => leg.airlineCode))].join('/');
}

function tripLegs(trip) {
  return trip.legs.map((leg) => `
    <div class="leg">
      <strong>${escapeHtml(leg.airlineCode)} ${escapeHtml(leg.flightNumber)}</strong>
      <span>${escapeHtml(airportLabel(leg.origin))} ${timeText(leg.departureTime)} → ${escapeHtml(airportLabel(leg.destination))} ${timeText(leg.arrivalTime)}</span>
    </div>
  `).join('');
}

function renderTrips(payload) {
  state.trips = payload.trips || [];
  $('#result-summary').textContent = state.trips.length
    ? `${state.trips.length} 个行程${payload.partial ? ' · 存在航司查询警告' : ''}`
    : '没有符合条件的行程';

  if (payload.warnings?.length) {
    showStatus(`部分航司不可用：${payload.warnings.join('；')}`, 'warn');
  } else {
    hideStatus();
  }

  if (!state.trips.length) {
    results.className = 'results-empty';
    results.innerHTML = `<div class="empty-mark">○</div><p>没有满足余座和中转时间要求的行程，请调整条件。</p>`;
    return;
  }

  results.className = 'trip-list';
  results.innerHTML = state.trips.map((trip, index) => `
    <article class="trip-card">
      <div class="trip-main">
        <div class="trip-head">
          <span class="airline-tag">${escapeHtml(tripAirlines(trip))}</span>
          <span class="route-line">${escapeHtml(tripRoute(trip))}</span>
          <span class="trip-time">${trip.legs.length === 1 ? '直达' : `${trip.legs.length - 1} 次中转`} · ${dateTimeText(trip.legs[0].departureTime)}</span>
        </div>
        <div class="leg-list">${tripLegs(trip)}</div>
        <div class="pricing-note ${trip.sameAirlineDiscount ? '' : 'mixed'}">${escapeHtml(trip.pricingNote)}</div>
      </div>
      <div class="trip-side">
        <div class="price-formula">单人 ${moneyText(trip.pricePerPassenger)} × ${trip.passengers} 人</div>
        <div class="price">总价 ${moneyText(trip.totalPrice)}</div>
        <div class="duration">总时长 ${Math.floor(trip.totalDurationMinutes / 60)}h ${trip.totalDurationMinutes % 60}m</div>
        <button class="book-button" type="button" data-trip-index="${index}">选择此行程</button>
      </div>
    </article>
  `).join('');

  results.querySelectorAll('.book-button').forEach((button) => {
    button.addEventListener('click', () => openBooking(Number(button.dataset.tripIndex)));
  });
}

async function onSearch(event) {
  event.preventDefault();
  hideStatus();
  const button = event.submitter || $('#search-form button[type="submit"]');
  button.disabled = true;
  button.textContent = '查询中…';
  try {
    const criteria = {
      origin: $('#origin').value,
      destination: $('#destination').value,
      date: $('#date').value,
      passengers: Number($('#passengers').value),
      airlineCodes: [...document.querySelectorAll('#airline-options input:checked')].map((item) => item.value),
      sortMode: $('#sort-mode').value,
    };
    const payload = await api('/api/search', { method: 'POST', body: JSON.stringify(criteria) });
    renderTrips(payload);
  } catch (error) {
    results.className = 'results-empty';
    results.innerHTML = `<div class="empty-mark">!</div><p>查询失败，请检查服务状态。</p>`;
    showStatus(error.message, 'error');
  } finally {
    button.disabled = false;
    button.textContent = '查询行程';
  }
}

function openBooking(index) {
  state.selectedTrip = state.trips[index];
  const trip = state.selectedTrip;
  const passengerCount = Number($('#passengers').value);
  $('#selected-trip').textContent =
    `${tripRoute(trip)} · ${tripAirlines(trip)} · 单人 ${moneyText(trip.pricePerPassenger)} × ${trip.passengers} 人`
    + ` = ${moneyText(trip.totalPrice)} · ${trip.pricingNote}`;
  $('#passenger-fields').innerHTML = Array.from({ length: passengerCount }, (_, index) => `
    <div class="passenger-row">
      <span class="passenger-index">乘客 ${index + 1}</span>
      <label>姓名<input class="passenger-name" data-index="${index}" placeholder="与证件一致" required></label>
      <label>护照号<input class="passenger-passport" data-index="${index}" placeholder="E12345678" required></label>
      <label>座位偏好
        <select class="passenger-seat">
          <option value="WINDOW">靠窗 Window</option>
          <option value="AISLE">靠过道 Aisle</option>
          <option value="MIDDLE">中间 Middle</option>
        </select>
      </label>
    </div>
  `).join('');
  bookingDialog.showModal();
}

async function submitBooking(event) {
  event.preventDefault();
  if (!state.selectedTrip) return;
  const passengers = [...document.querySelectorAll('.passenger-row')].map((row) => ({
    name: row.querySelector('.passenger-name').value.trim(),
    passportNumber: row.querySelector('.passenger-passport').value.trim(),
    seatPreference: row.querySelector('.passenger-seat').value,
  }));
  if (passengers.some((p) => !p.name || !p.passportNumber || !p.seatPreference)) {
    showStatus('请完整填写每位乘客的姓名、护照号和座位偏好。', 'error');
    return;
  }
  const submit = $('#submit-booking');
  submit.disabled = true;
  submit.textContent = '提交中…';
  try {
    const receipt = await api('/api/bookings', {
      method: 'POST',
      body: JSON.stringify({
        trip: state.selectedTrip,
        passengers,
        idempotencyKey: crypto.randomUUID(),
      }),
    });
    bookingDialog.close();
    renderReceipt(receipt);
    $('#receipt-reference').value = receipt.bookingReference;
    showStatus(`预订成功：${receipt.bookingReference}`, 'info');
    document.querySelector('.receipt-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) {
    showStatus(`预订失败：${error.message}`, 'error');
  } finally {
    submit.disabled = false;
    submit.textContent = '确认预订';
  }
}

function seatPreferenceText(value) {
  return {
    WINDOW: '靠窗 Window',
    AISLE: '靠过道 Aisle',
    MIDDLE: '中间 Middle',
    MIXED: '混合 Mixed',
  }[value] || value;
}

function renderReceipt(receipt) {
  const box = $('#receipt');
  const passengerCount = receipt.passengers.length;
  const pricePerPassenger = Number(receipt.totalPrice) / passengerCount;
  box.classList.remove('hidden');
  box.innerHTML = `
    <h4>${escapeHtml(receipt.bookingReference)} · ${escapeHtml(receipt.status)}</h4>
    <p>${escapeHtml(receipt.message || '')}</p>
    <div class="receipt-grid">
      <div><span>乘客</span><strong>${passengerCount} 人</strong></div>
      <div><span>计价</span><strong>${moneyText(pricePerPassenger)} × ${passengerCount} 人</strong></div>
      <div><span>总价</span><strong>${moneyText(receipt.totalPrice)}</strong></div>
    </div>
    <div class="receipt-passengers">
      ${receipt.passengers.map((passenger, index) => `
        <div class="leg">
          <strong>乘客 ${index + 1} ${escapeHtml(passenger.name)}</strong>
          <span>${escapeHtml(passenger.passportNumber)} · ${escapeHtml(seatPreferenceText(passenger.seatPreference))}</span>
        </div>
      `).join('')}
    </div>
    <div class="receipt-legs">
      ${receipt.legs.map((leg) => `
        <div class="leg">
          <strong>${escapeHtml(leg.airlineCode)} ${escapeHtml(leg.flightNumber)}</strong>
          <span>${escapeHtml(airportLabel(leg.origin))} ${dateTimeText(leg.departureTime)} → ${escapeHtml(airportLabel(leg.destination))} ${dateTimeText(leg.arrivalTime)} · ${escapeHtml(leg.status)}</span>
        </div>
      `).join('')}
    </div>
  `;
}

async function lookupReceipt() {
  const reference = $('#receipt-reference').value.trim();
  if (!reference) {
    showStatus('请输入预订编号。', 'warn');
    return;
  }
  try {
    renderReceipt(await api(`/api/bookings/${encodeURIComponent(reference)}`));
    hideStatus();
  } catch (error) {
    showStatus(error.message, 'error');
  }
}

async function cancelBooking() {
  const reference = $('#receipt-reference').value.trim();
  if (!reference) {
    showStatus('请输入要取消的预订编号。', 'warn');
    return;
  }
  if (!window.confirm(`确认取消 ${reference}？`)) return;
  const button = $('#cancel-button');
  button.disabled = true;
  try {
    renderReceipt(await api(`/api/bookings/${encodeURIComponent(reference)}/cancel`, { method: 'POST' }));
    showStatus('预订已取消，库存已回补。', 'info');
  } catch (error) {
    showStatus(`取消失败：${error.message}`, 'error');
  } finally {
    button.disabled = false;
  }
}

$('#search-form').addEventListener('submit', onSearch);
$('#booking-form').addEventListener('submit', submitBooking);
$('#close-dialog').addEventListener('click', () => bookingDialog.close());
$('#cancel-dialog').addEventListener('click', () => bookingDialog.close());
$('#lookup-button').addEventListener('click', lookupReceipt);
$('#cancel-button').addEventListener('click', cancelBooking);
loadRegistry();
