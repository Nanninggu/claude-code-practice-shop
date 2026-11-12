// 공통 스크립트

var MEMBERS = [
    { id: 1, name: '김민준', grade: 'BRONZE' },
    { id: 2, name: '이서연', grade: 'SILVER' },
    { id: 3, name: '박지호', grade: 'GOLD' },
    { id: 4, name: '최수아', grade: 'VIP' },
    { id: 5, name: '정도윤', grade: 'SILVER' }
];

var STATUS_LABEL = {
    CREATED: '결제대기',
    PAID: '결제완료',
    PREPARING: '배송준비',
    SHIPPED: '배송중',
    DELIVERED: '배송완료',
    CONFIRMED: '구매확정',
    CANCELED: '취소'
};

function getMemberId() {
    try {
        return Number(localStorage.getItem('memberId')) || 4;
    } catch (e) {
        return 4;
    }
}

function setMemberId(id) {
    try {
        localStorage.setItem('memberId', id);
    } catch (e) {}
}

function renderHeader(active) {
    var memberId = getMemberId();
    var options = MEMBERS.map(function (m) {
        return '<option value="' + m.id + '"' + (m.id === memberId ? ' selected' : '') + '>'
            + m.name + ' (' + m.grade + ')</option>';
    }).join('');
    var menus = [
        { key: 'shop', href: '/index.html', label: '상품' },
        { key: 'orders', href: '/orders.html', label: '내 주문' },
        { key: 'admin', href: '/admin.html', label: '관리자' }
    ].map(function (m) {
        return '<a href="' + m.href + '"' + (m.key === active ? ' class="active"' : '') + '>' + m.label + '</a>';
    }).join('');

    document.querySelector('header').innerHTML =
        '<div class="logo">ShopNow</div>' +
        '<nav>' + menus + '</nav>' +
        '<div class="member">로그인 회원 <select id="memberSelect">' + options + '</select>' +
        '<span id="memberPoint"></span></div>';

    document.getElementById('memberSelect').addEventListener('change', function (e) {
        setMemberId(e.target.value);
        location.reload();
    });
    loadMemberPoint();
}

function loadMemberPoint() {
    api('GET', '/api/members/' + getMemberId()).then(function (res) {
        if (res.ok) {
            document.getElementById('memberPoint').textContent = '· 포인트 ' + formatNumber(res.data.point) + 'P';
        }
    });
}

// API 호출
function api(method, url, body, extraHeaders) {
    var headers = { 'X-MEMBER-ID': String(getMemberId()) };
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    if (extraHeaders) {
        for (var k in extraHeaders) headers[k] = extraHeaders[k];
    }
    return fetch(url, {
        method: method,
        headers: headers,
        body: body !== undefined ? JSON.stringify(body) : undefined
    }).then(function (res) {
        return res.text().then(function (text) {
            var data = null;
            try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
            return { ok: res.ok, status: res.status, data: data };
        });
    });
}

function errorMessage(res) {
    if (res.data && res.data.message) return res.data.message;
    if (res.data && res.data.error) return '서버 오류 (' + res.status + ' ' + res.data.error + ')';
    return '요청 실패 (' + res.status + ')';
}

function formatNumber(n) {
    return Number(n || 0).toLocaleString('ko-KR');
}

function formatPrice(n) {
    return formatNumber(n) + '원';
}

function formatDate(s) {
    if (!s) return '';
    return s.replace('T', ' ').substring(0, 16);
}

function escapeHtml(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function showMsg(el, ok, text) {
    el.className = 'msg ' + (ok ? 'ok' : 'err');
    el.textContent = text;
}
