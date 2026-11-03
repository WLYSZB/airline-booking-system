const state = {
  trips: [],
  selectedTrip: null,
  airlines: [],
  airports: new Map(),
  lastCriteria: null,
  searchSequence: 0,
  bookingKey: null,
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

function currentCriteria() {
  return {
    origin: $('#origin').value,
    destination: $('#destination').value,
    date: $('#date').value,
    passengers: Number($('#passengers').value),
    airlineCodes: [...document.querySelectorAll('#airline-options input:checked')].map((item) => item.value),
    sortMode: $('#sort-mode').value,
  };
}

function sameCriteria(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function showBookingError(message) {
  const errorBox = $('#booking-error');
  errorBox.textContent = message;
  errorBox.classList.remove('hidden');
}

function hideBookingError() {
  const errorBox = $('#booking-error');
  errorBox.textContent = '';
  errorBox.classList.add('hidden');
}

async function api(path, options = {}) {
  const { timeout = 15000, ...fetchOptions } = options;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  let response;
  try {
    response = await fetch(path, {
      ...fetchOptions,
      headers: { 'Content-Type': 'application/json', ...(fetchOptions.headers || {}) },
      signal: controller.signal,
    });
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('请求超时，请稍后重试');
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`服务返回了无法解析的响应 (${response.status})`);
  }
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
  const criteria = currentCriteria();
  if (!criteria.airlineCodes.length) {
    showStatus('请至少选择一家航空公司。', 'warn');
    return;
  }
  const button = event.submitter || $('#search-form button[type="submit"]');
  button.disabled = true;
  button.textContent = '查询中…';
  const sequence = ++state.searchSequence;
  state.lastCriteria = criteria;
  try {
    const payload = await api('/api/search', { method: 'POST', body: JSON.stringify(criteria) });
    if (sequence !== state.searchSequence) return;
    renderTrips(payload);
  } catch (error) {
    if (sequence !== state.searchSequence) return;
    results.className = 'results-empty';
    results.innerHTML = `<div class="empty-mark">!</div><p>查询失败，请检查服务状态。</p>`;
    showStatus(error.message, 'error');
  } finally {
    if (sequence === state.searchSequence) {
      button.disabled = false;
      button.textContent = '查询行程';
    }
  }
}

function openBooking(index) {
  if (!state.lastCriteria || !sameCriteria(currentCriteria(), state.lastCriteria)) {
    showStatus('查询条件已改变，请重新查询后再预订。', 'warn');
    return;
  }
  const trip = state.trips[index];
  const passengerCount = currentCriteria().passengers;
  if (trip.passengers !== passengerCount) {
    showStatus('乘客人数与当前查询结果不一致，请重新查询。', 'warn');
    return;
  }
  state.selectedTrip = trip;
  state.bookingKey = null;
  hideBookingError();
  $('#selected-trip').textContent =
    `${tripRoute(trip)} · ${tripAirlines(trip)} · 单人 ${moneyText(trip.pricePerPassenger)} × ${trip.passengers} 人`
    + ` = ${moneyText(trip.totalPrice)} · ${trip.pricingNote}`;
  $('#passenger-fields').innerHTML = Array.from({ length: passengerCount }, (_, index) => `
    <div class="passenger-row">
      <span class="passenger-index">乘客 ${index + 1}</span>
      <label for="passenger-${index}-name">姓名
        <input id="passenger-${index}-name" name="passengerName" class="passenger-name"
               data-index="${index}" autocomplete="name" placeholder="与证件一致" required>
      </label>
      <label for="passenger-${index}-passport">护照号
        <input id="passenger-${index}-passport" name="passportNumber" class="passenger-passport"
               data-index="${index}" autocomplete="off" placeholder="E12345678" required>
      </label>
      <label for="passenger-${index}-seat">座位偏好
        <select id="passenger-${index}-seat" name="seatPreference" class="passenger-seat">
          <option value="WINDOW">靠窗 Window</option>
          <option value="AISLE">靠过道 Aisle</option>
          <option value="MIDDLE">中间 Middle</option>
        </select>
      </label>
    </div>
  `).join('');
  bookingDialog.showModal();
}

async function refreshTrips() {
  if (!state.lastCriteria) return;
  const sequence = state.searchSequence;
  try {
    const payload = await api('/api/search', {
      method: 'POST',
      body: JSON.stringify(state.lastCriteria),
    });
    if (sequence === state.searchSequence) {
      renderTrips(payload);
    }
  } catch {
    // 刷新失败不覆盖刚生成的收据，下一次查询会重新同步。
  }
}

async function submitBooking(event) {
  event.preventDefault();
  if (!state.selectedTrip) return;
  hideBookingError();
  const passengers = [...document.querySelectorAll('.passenger-row')].map((row) => ({
    name: row.querySelector('.passenger-name').value.trim(),
    passportNumber: row.querySelector('.passenger-passport').value.trim(),
    seatPreference: row.querySelector('.passenger-seat').value,
  }));
  if (passengers.some((p) => !p.name || !p.passportNumber || !p.seatPreference)) {
    showBookingError('请完整填写每位乘客的姓名、护照号和座位偏好。');
    return;
  }
  const submit = $('#submit-booking');
  submit.disabled = true;
  submit.textContent = '提交中…';
  state.bookingKey = state.bookingKey || crypto.randomUUID();
  try {
    const receipt = await api('/api/bookings', {
      method: 'POST',
      body: JSON.stringify({
        trip: state.selectedTrip,
        passengers,
        idempotencyKey: state.bookingKey,
      }),
    });
    state.bookingKey = null;
    bookingDialog.close();
    renderReceipt(receipt);
    $('#receipt-reference').value = receipt.bookingReference;
    await refreshTrips();
    showStatus(`预订成功：${receipt.bookingReference}`, 'info');
    document.querySelector('.receipt-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) {
    showBookingError(`预订失败：${error.message}`);
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
  const button = $('#lookup-button');
  button.disabled = true;
  button.textContent = '查询中…';
  try {
    renderReceipt(await api(`/api/bookings/${encodeURIComponent(reference)}`));
    hideStatus();
  } catch (error) {
    showStatus(error.message, 'error');
  } finally {
    button.disabled = false;
    button.textContent = '查询收据';
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
  button.textContent = '取消中…';
  try {
    renderReceipt(await api(`/api/bookings/${encodeURIComponent(reference)}/cancel`, { method: 'POST' }));
    await refreshTrips();
    showStatus('预订已取消，库存已回补。', 'info');
  } catch (error) {
    showStatus(`取消失败：${error.message}`, 'error');
  } finally {
    button.disabled = false;
    button.textContent = '取消预订';
  }
}

function closeBookingDialog() {
  state.bookingKey = null;
  hideBookingError();
  bookingDialog.close();
}

$('#search-form').addEventListener('submit', onSearch);
$('#booking-form').addEventListener('submit', submitBooking);
$('#close-dialog').addEventListener('click', closeBookingDialog);
$('#cancel-dialog').addEventListener('click', closeBookingDialog);
bookingDialog.addEventListener('close', () => {
  state.bookingKey = null;
  hideBookingError();
});
$('#lookup-button').addEventListener('click', lookupReceipt);
$('#cancel-button').addEventListener('click', cancelBooking);
loadRegistry();
