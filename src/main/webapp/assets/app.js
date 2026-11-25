'use strict';

// Map client for the REST API under api/. No framework and no build step; Leaflet draws the map.
// Every piece of data from the server is inserted as text through el(), never as HTML.

const MODES = { car: 'Araç', bicycle: 'Bisiklet', foot: 'Yürüyüş', transit: 'Toplu taşıma' };
const TRAVEL_BY = { car: 'araçla', bicycle: 'bisikletle', foot: 'yürüyerek', transit: 'toplu taşımayla' };
/** How each kind of public transport looks on the map and in the journey: its colour and its icon. */
const TRANSIT_STYLE = {
    WALK: { color: '#7c8594', icon: 'foot' },
    FERRY: { color: '#0284c7', icon: 'ferry' },
    METRO: { color: '#db2777', icon: 'train' },
    RAIL: { color: '#7c3aed', icon: 'train' },
    TRAM: { color: '#ea580c', icon: 'train' },
    FUNICULAR: { color: '#a16207', icon: 'train' },
    CABLE_CAR: { color: '#a16207', icon: 'train' },
    BUS: { color: '#16a34a', icon: 'bus' },
    METROBUS: { color: '#dc2626', icon: 'bus' },
    MINIBUS: { color: '#ca8a04', icon: 'bus' },
    DOLMUS: { color: '#0d9488', icon: 'bus' }
};
const INCIDENT_COLORS = { ACCIDENT: '#dc2626', ROADWORK: '#ea580c', CONGESTION: '#d97706', CLOSURE: '#7c3aed' };
const FIELDS = {
    from: 'Başlangıç', to: 'Varış', mode: 'Ulaşım şekli', type: 'Olay türü',
    description: 'Açıklama', durationMinutes: 'Süre', lat: 'Enlem', lon: 'Boylam', name: 'Ad', category: 'Kategori',
    email: 'E-posta', password: 'Şifre', label: 'Yer adı', detail: 'Ayrıntı', places: 'Kayıtlı yerler'
};
/** The signed-in account: its token and how to greet it. */
const SESSION_KEY = 'city-navigator.session';
/** Set while this device has changes to the saved places that the account has not received yet. */
const PLACES_DIRTY_KEY = 'city-navigator.places-dirty';
/** Answers that mean the server is unreachable or still waking up (free hosting sleeps when nobody uses it). */
const WAKING_STATUSES = [0, 502, 503, 504];
/** During start-up a 500 may mean the database is still waking too; it is retried a few times there only. */
const STARTUP_RETRY_STATUSES = [0, 500, 502, 503, 504];
/** How long to wait for a sleeping server to wake up (ms): a free server with a sliver of a processor is slow. */
const WAKE_TIMEOUT_MS = 300000;
/** Distance from the route (m) that counts as having left it, per mode. */
const OFF_ROUTE_METERS = { car: 40, bicycle: 30, foot: 25 };
/** Distances (m) before a manoeuvre at which it is announced; the last one says "şimdi". */
const ANNOUNCE_AT = { car: [500, 200, 40], bicycle: [200, 60, 20], foot: [80, 25, 10] };
const ARRIVAL_METERS = { car: 25, bicycle: 20, foot: 12 };
/** How much faster than real time the simulation drives; slow enough for every announcement to be heard. */
const SIMULATION_SPEEDUP = { car: 2, bicycle: 3, foot: 4 };
const TOKEN_KEY = 'city-navigator.admin-token';
const BUSINESS_KEY = 'city-navigator.businesses';
/** Businesses appear from this zoom level on, like shop icons on paper street maps. */
const BUSINESS_MIN_ZOOM = 16;
/** The largest view the server lists businesses for (degrees); bigger screens ask for their centre part. */
const BUSINESS_MAX_SPAN = { lat: 0.028, lon: 0.04 };
const BUSINESS_STYLE = {
    FOOD: ['#ea580c', 'food'], GROCERY: ['#16a34a', 'basket'], SHOP: ['#2563eb', 'bag'], MONEY: ['#0f766e', 'money'],
    HEALTH: ['#dc2626', 'health'], FUEL: ['#7c3aed', 'fuel'], HOTEL: ['#9333ea', 'bed'], SERVICE: ['#475569', 'wrench'],
    SHOPPING: ['#1d4ed8', 'bag'], CULTURE: ['#c026d3', 'ticket'], PARK: ['#15803d', 'ticket']
};
const OPENING_DAYS = { Mo: 'Pzt', Tu: 'Sal', We: 'Çar', Th: 'Per', Fr: 'Cum', Sa: 'Cmt', Su: 'Paz', PH: 'resmî tatil' };
const VOICE_KEY = 'city-navigator.voice';
const TRAFFIC_KEY = 'city-navigator.traffic';
/** Traffic colours appear from this zoom level on; the server draws views up to about 50 km across. */
const TRAFFIC_MIN_ZOOM = 11;
const TRAFFIC_MAX_SPAN = { lat: 0.45, lon: 0.62 };
/** Share of the free-flow speed traffic leaves (from) and the colour for it, slowest last. */
const TRAFFIC_COLORS = [[0.8, '#16a34a'], [0.6, '#eab308'], [0.4, '#f97316'], [0, '#dc2626']];
/** Ordinal words for reading "3. çıkışa girin" aloud as "üçüncü çıkışa girin". */
const SPOKEN_ORDINALS = ['birinci', 'ikinci', 'üçüncü', 'dördüncü', 'beşinci', 'altıncı', 'yedinci', 'sekizinci',
    'dokuzuncu', 'onuncu'];
/** While following, the map looks this far along the route to know which way is ahead (m), per mode. */
const LOOK_AHEAD_METERS = { car: 150, bicycle: 80, foot: 40 };
/** How far behind the middle of the free map area the arrow sits, as a share of that area's smaller side. */
const LOOK_AHEAD_SHARE = 0.25;
const TRAFFIC_LEVELS = { 'akıcı': 'free', normal: 'normal', 'yoğun': 'busy', 'çok yoğun': 'jammed' };
const INTRO_KEY = 'city-navigator.intro';
const RECENT_KEY = 'city-navigator.recent';
/** Home, work and saved places, kept on this device: { home, work, places }. */
const FAVORITES_KEY = 'city-navigator.favorites';
const MAX_FAVORITES = 20;
/** Two points this close are the same saved place (m). */
const SAME_PLACE_METERS = 40;
const SHORTCUTS = { home: { label: 'Ev', icon: 'home' }, work: { label: 'İş', icon: 'briefcase' } };
const MAX_RECENT = 6;
/** Vector basemaps of OpenFreeMap (no key, no limits; attribution required), light and dark. */
const BASEMAP_STYLES = {
    light: 'https://tiles.openfreemap.org/styles/positron',
    dark: 'https://tiles.openfreemap.org/styles/dark'
};
const BASEMAP_ATTRIBUTION = '<a href="https://openfreemap.org" target="_blank" rel="noopener">OpenFreeMap</a> '
    + '<a href="https://www.openmaptiles.org/" target="_blank" rel="noopener">© OpenMapTiles</a> '
    + 'Veri: <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">© OpenStreetMap</a> katkıda bulunanlar';
/** Places one tap away: the heart of Kadıköy, two Istanbul classics and a long drive to show the national engine. */
const QUICK_DESTINATIONS = [
    { label: 'Kadıköy İskelesi', kind: 'İskele', detail: 'Kadıköy', lat: 40.99162, lon: 29.02168, icon: 'ferry' },
    { label: 'Moda Sahili', kind: 'Park', detail: 'Caferağa, Kadıköy', lat: 40.9802, lon: 29.02922, icon: 'tree' },
    { label: 'Bağdat Caddesi', kind: 'Cadde', detail: 'Kadıköy', lat: 40.98386, lon: 29.03994, icon: 'bag' },
    { label: 'Fenerbahçe Parkı', kind: 'Park', detail: 'Fenerbahçe, Kadıköy', lat: 40.96781, lon: 29.03455, icon: 'tree' },
    { label: 'Haydarpaşa Garı', kind: 'İstasyon', detail: 'Kadıköy', lat: 40.9962, lon: 29.01884, icon: 'train' },
    { label: 'Taksim Meydanı', kind: 'Meydan', detail: 'Beyoğlu', lat: 41.03783, lon: 28.98502, icon: 'landmark' },
    { label: 'Sultanahmet', kind: 'Meydan', detail: 'Fatih', lat: 41.00652, lon: 28.97598, icon: 'landmark' },
    { label: 'Ankara, Kızılay', kind: 'Meydan', detail: 'Çankaya, Ankara', lat: 39.9208, lon: 32.8541, icon: 'city' }
];
const ENGINE_LABELS = {
    astar: 'Kendi motorumuz · ayrıntılı', national: 'Kendi motorumuz · Türkiye', osrm: 'OSRM', mixed: 'Birden fazla motor'
};
/** At most this many stops between the start and the destination (as on the server). */
const MAX_STOPS = 3;
/**
 * What can be looked for along a route: its chip, what "nothing found" calls it, the icon and colour of its markers,
 * and who looks for it.
 */
const ALONG_NEEDS = {
    fuel: { label: 'Benzinlik', noun: 'benzinlik', icon: 'fuel', color: '#7c3aed', modes: ['car'] },
    charging: { label: 'Şarj', noun: 'şarj istasyonu', icon: 'bolt', color: '#0891b2', modes: ['car'] },
    pharmacy: { label: 'Eczane', noun: 'eczane', icon: 'health', color: '#dc2626', modes: ['car', 'bicycle', 'foot'] },
    market: { label: 'Market', noun: 'market', icon: 'basket', color: '#16a34a', modes: ['car', 'bicycle', 'foot'] },
    money: { label: 'ATM / Banka', noun: 'ATM ya da banka', icon: 'money', color: '#0f766e',
        modes: ['car', 'bicycle', 'foot'] }
};
/** The route goes to the search along it in a link, with at most this many points; longer routes are simplified. */
const ALONG_MAX_POINTS = 300;
/** The other routes are drawn faded under the chosen one: casing, core and the casing under the pointer. */
const ALTERNATIVE_STYLE = {
    light: { casing: '#a3a9b3', core: '#e3f5b4', hover: '#6b7280' },
    dark: { casing: '#4f5560', core: '#66743a', hover: '#8a919d' }
};
/** How wide the invisible line is that catches taps on a route, in pixels. */
const ROUTE_TAP_WIDTH = 28;
const CATEGORY_ICONS = {
    SETTLEMENT: 'city', NEIGHBOURHOOD: 'city', TRANSIT: 'train', STREET: 'road', CULTURE: 'ticket', PARK: 'tree',
    EDUCATION: 'school', SHOPPING: 'bag', PUBLIC_SERVICE: 'landmark', HEALTH: 'health', FOOD: 'food', GROCERY: 'basket',
    SHOP: 'bag', MONEY: 'money', SERVICE: 'wrench', WORSHIP: 'landmark', HOTEL: 'bed', FUEL: 'fuel'
};
const phoneLayout = window.matchMedia('(max-width: 720px)');
const darkScheme = window.matchMedia('(prefers-color-scheme: dark)');
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const SVG_NS = 'http://www.w3.org/2000/svg';

const state = {
    area: null,
    mode: 'car',
    depart: 'now',
    from: null,
    to: null,
    /** Stops between the start and the destination, in order; an entry is null while its field is still empty. */
    stops: [],
    /** The chosen route; routes holds it with the other offered ones, the fastest first. */
    route: null,
    routes: [],
    /** The kind of place looked for along the route (the chip that is on), or null. */
    alongNeed: null,
    incidents: [],
    picking: false,
    nav: null
};

/** The device's real position, drawn as a blue dot once the user has allowed location access. */
const here = { position: null, watchId: null, marker: null, circle: null, pendingLocate: false, resumeOnShow: false };

const layers = {};
let map;

/* ---------- icons ---------- */

const ICONS = {
    car: [['path', { d: 'M4 16v-4.5L6 7h12l2 4.5V16Z' }], ['circle', { cx: 8, cy: 16.5, r: 1.8 }],
        ['circle', { cx: 16, cy: 16.5, r: 1.8 }]],
    bicycle: [['circle', { cx: 6, cy: 16, r: 3.5 }], ['circle', { cx: 18, cy: 16, r: 3.5 }],
        ['path', { d: 'M6 16l4-7h6l2 7M10 9l3 7H6M9 6h3' }]],
    foot: [['circle', { cx: 13, cy: 4.5, r: 1.8 }],
        ['path', { d: 'M9 21l2.5-6.5L15 17v4M7.5 11.5 10 8l4 1 2 3.5 2.5.5M11 8.5l-1.5 6' }]],
    locate: [['circle', { cx: 12, cy: 12, r: 7 }], ['path', { d: 'M12 2v3M12 19v3M2 12h3M19 12h3' }],
        ['circle', { cx: 12, cy: 12, r: 2.5, fill: 'currentColor' }]],
    swap: [['path', { d: 'M7 4v16M3 8l4-4 4 4M17 20V4M13 16l4 4 4-4' }]],
    key: [['circle', { cx: 8, cy: 15, r: 4 }], ['path', { d: 'M11 12 20 3M16.5 6.5l3 3M14 9l2.5 2.5' }]],
    user: [['circle', { cx: 12, cy: 8, r: 4 }], ['path', { d: 'M4 21c0-4 3.6-7 8-7s8 3 8 7' }]],
    volume: [['path', { d: 'M4 9.5v5h3.5L12 18V6L7.5 9.5Z' }], ['path', { d: 'M16 9.5a3.5 3.5 0 0 1 0 5M18.5 7a7 7 0 0 1 0 10' }]],
    volumeOff: [['path', { d: 'M4 9.5v5h3.5L12 18V6L7.5 9.5Z' }], ['path', { d: 'M16 10l5 4M21 10l-5 4' }]],
    play: [['path', { d: 'M7 5v14l11-7Z', fill: 'currentColor' }]],
    pause: [['path', { d: 'M8 5v14M16 5v14' }]],
    navigate: [['path', { d: 'M12 3 19 20 12 16 5 20Z' }]],
    warning: [['path', { d: 'M12 4 21.5 20h-19Z' }], ['path', { d: 'M12 10v4M12 17h.01' }]],
    close: [['path', { d: 'M6 6l12 12M18 6 6 18' }]],
    straight: [['path', { d: 'M12 20V5M6.5 10.5 12 5l5.5 5.5' }]],
    right: [['path', { d: 'M7 20v-7a4 4 0 0 1 4-4h8M15 5l4 4-4 4' }]],
    left: [['path', { d: 'M17 20v-7a4 4 0 0 0-4-4H5M9 5 5 9l4 4' }]],
    slightRight: [['path', { d: 'M9 20v-6l7.5-7.5M11 6.5h5.5V12' }]],
    slightLeft: [['path', { d: 'M15 20v-6L7.5 6.5M13 6.5H7.5V12' }]],
    sharpRight: [['path', { d: 'M8 20V6l9 11M12.5 17H17v-4.5' }]],
    sharpLeft: [['path', { d: 'M16 20V6L7 17M11.5 17H7v-4.5' }]],
    uTurn: [['path', { d: 'M8 20V10a4 4 0 0 1 8 0v8M12.5 14.5 16 18l3.5-3.5' }]],
    roundabout: [['circle', { cx: 12, cy: 9, r: 4 }], ['path', { d: 'M12 21v-8M15 6l4-3M15.5 3H19v3.5' }]],
    depart: [['path', { d: 'M12 21s-6-5.6-6-10.5a6 6 0 0 1 12 0C18 15.4 12 21 12 21Z' }],
        ['circle', { cx: 12, cy: 10.5, r: 2 }]],
    arrive: [['path', { d: 'M6 21V4M6 4h11l-2.5 4L17 12H6' }]],
    store: [['path', { d: 'M4 10v10h16V10M3 10l2-6h14l2 6Z' }], ['path', { d: 'M9 20v-6h6v6' }]],
    food: [['path', { d: 'M7 3v7a2 2 0 0 0 4 0V3M9 10v11M17 21V3c-1.7 0-3 2.2-3 5v5h3' }]],
    basket: [['path', { d: 'M4 10h16l-1.6 9H5.6Z' }], ['path', { d: 'M8 10l3-6M16 10l-3-6' }]],
    bag: [['path', { d: 'M6 8h12l-1 12H7Z' }], ['path', { d: 'M9 8V6a3 3 0 0 1 6 0v2' }]],
    money: [['path', { d: 'M3 7h18v10H3Z' }], ['circle', { cx: 12, cy: 12, r: 2.5 }]],
    health: [['path', { d: 'M10 4h4v6h6v4h-6v6h-4v-6H4v-4h6Z' }]],
    fuel: [['path', { d: 'M5 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h14M15 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3M8 7h4' }]],
    bed: [['path', { d: 'M3 18V7M3 14h18v4M21 14v-2a3 3 0 0 0-3-3h-7v5' }], ['circle', { cx: 7, cy: 11, r: 1.6 }]],
    wrench: [['path', { d: 'M14.5 6.5a4 4 0 0 0-5 5L4 17l3 3 5.5-5.5a4 4 0 0 0 5-5l-2.5 2.5-2.5-.5-.5-2.5Z' }]],
    ticket: [['path', { d: 'M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4Z' }]],
    traffic: [['path', { d: 'M8 3h8v18H8Z' }], ['circle', { cx: 12, cy: 7, r: 1.6 }], ['circle', { cx: 12, cy: 12, r: 1.6 }],
        ['circle', { cx: 12, cy: 17, r: 1.6 }]],
    download: [['path', { d: 'M12 4v11M7 10.5l5 5 5-5M5 20h14' }]],
    info: [['circle', { cx: 12, cy: 12, r: 9 }], ['path', { d: 'M12 11v5M12 8h.01' }]],
    map: [['path', { d: 'M9 4 3.5 6v14L9 18l6 2 5.5-2V4L15 6Z' }], ['path', { d: 'M9 4v14M15 6v14' }]],
    route: [['circle', { cx: 6, cy: 18, r: 2.2 }], ['circle', { cx: 18, cy: 6, r: 2.2 }],
        ['path', { d: 'M8.2 18H14a3.5 3.5 0 0 0 0-7h-4a3.5 3.5 0 0 1 0-7h5.8' }]],
    clock: [['circle', { cx: 12, cy: 12, r: 9 }], ['path', { d: 'M12 7v5l3 2' }]],
    history: [['path', { d: 'M3.5 12a8.5 8.5 0 1 0 2.6-6.1L3.5 8.5' }], ['path', { d: 'M3.5 4v4.5H8M12 8v4l3 2' }]],
    pin: [['path', { d: 'M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0C18.5 15.4 12 21 12 21Z' }],
        ['circle', { cx: 12, cy: 10, r: 2.3 }]],
    ferry: [['path', { d: 'M4 15.5 5.5 19h13l1.5-3.5L12 13Z' }], ['path', { d: 'M7 14V9h10v5M12 9V5M3 21c1.5-1 3-1 4.5 0s3 1 4.5 0 3-1 4.5 0 3 1 4.5 0' }]],
    tree: [['path', { d: 'M12 21v-5M12 3 6.5 11h3L6 16h12l-3.5-5h3Z' }]],
    train: [['rect', { x: 6, y: 3, width: 12, height: 14, rx: 3 }], ['path', { d: 'M6 11h12M9 21l1.5-4M15 21l-1.5-4' }],
        ['circle', { cx: 9.5, cy: 14, r: 0.6, fill: 'currentColor' }], ['circle', { cx: 14.5, cy: 14, r: 0.6, fill: 'currentColor' }]],
    landmark: [['path', { d: 'M4 21h16M5 21v-2h14v2M7 19V10M12 19V10M17 19V10M3.5 10h17L12 4Z' }]],
    city: [['path', { d: 'M3 21h18M5 21V9l5-3v15M10 21V4h9v17' }], ['path', { d: 'M13 8h3M13 12h3M13 16h3' }]],
    road: [['path', { d: 'M6 21 9 3M18 21 15 3M12 5v2M12 11v2M12 17v2' }]],
    school: [['path', { d: 'M2.5 9.5 12 5l9.5 4.5L12 14Z' }], ['path', { d: 'M6.5 11.5V16c3.5 2.5 7.5 2.5 11 0v-4.5M21.5 9.5V15' }]],
    expand: [['path', { d: 'm6 15 6-6 6 6' }]],
    share: [['path', { d: 'M12 15V3.5M7.5 8 12 3.5 16.5 8M5 12.5V20h14v-7.5' }]],
    plus: [['path', { d: 'M12 5v14M5 12h14' }]],
    bolt: [['path', { d: 'M13 3 5 14h6l-1 7 8-11h-6Z' }]],
    link: [['path', { d: 'M9 15 15 9M10.5 6.5 13 4a4 4 0 0 1 6 6l-2.5 2.5M13.5 17.5 11 20a4 4 0 0 1-6-6l2.5-2.5' }]],
    share2: [['path', { d: 'M12 15V3.5M8 7l4-4 4 4M5 12v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7' }]],
    bus: [['path', { d: 'M6 17V6a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v11ZM6 11h12M8 20v-3M16 20v-3' }]],
    star: [['path', { d: 'M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9Z' }]],
    home: [['path', { d: 'M4 11.5 12 4.5l8 7V20h-5.5v-5.5h-5V20H4Z' }]],
    briefcase: [['rect', { x: 3, y: 7, width: 18, height: 13, rx: 2 }],
        ['path', { d: 'M8.5 7V5.5A1.5 1.5 0 0 1 10 4h4a1.5 1.5 0 0 1 1.5 1.5V7M3 12.5h18' }]]
};

const MANEUVER_ICONS = {
    DEPART: 'depart', CONTINUE: 'straight', SLIGHT_LEFT: 'slightLeft', LEFT: 'left', SHARP_LEFT: 'sharpLeft',
    SLIGHT_RIGHT: 'slightRight', RIGHT: 'right', SHARP_RIGHT: 'sharpRight', U_TURN: 'uTurn',
    ROUNDABOUT: 'roundabout', ARRIVE: 'arrive', WAYPOINT: 'pin'
};

function icon(name) {
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('class', 'icon');
    svg.setAttribute('aria-hidden', 'true');
    for (const [tag, attributes] of ICONS[name] ?? []) {
        const shape = document.createElementNS(SVG_NS, tag);
        for (const [key, value] of Object.entries(attributes)) shape.setAttribute(key, value);
        svg.append(shape);
    }
    return svg;
}

/* ---------- helpers ---------- */

function el(tag, attributes = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attributes)) {
        if (value === null || value === undefined || value === false) continue;
        if (key === 'class') node.className = value;
        else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
        else node.setAttribute(key, value === true ? '' : value);
    }
    for (const child of children.flat()) {
        if (child === null || child === undefined || child === false) continue;
        node.append(child instanceof Node ? child : String(child));
    }
    return node;
}

const $ = (selector) => document.querySelector(selector);
const numberFormat = new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 1 });
const clockFormat = new Intl.DateTimeFormat('tr-TR', { hour: '2-digit', minute: '2-digit' });
const dayFormat = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

function formatDistance(meters) {
    if (meters >= 1000) return `${numberFormat.format(meters / 1000)} km`;
    const step = meters < 100 ? 10 : 50;
    return `${Math.max(step, Math.round(meters / step) * step)} m`;
}

function spokenDistance(meters) {
    if (meters >= 1000) return `${numberFormat.format(Math.round(meters / 100) / 10)} kilometre`;
    const step = meters < 100 ? 10 : 50;
    return `${Math.max(step, Math.round(meters / step) * step)} metre`;
}

function formatDuration(seconds) {
    const minutes = Math.max(1, Math.round(seconds / 60));
    if (minutes < 60) return `${minutes} dk`;
    return `${Math.floor(minutes / 60)} sa ${minutes % 60} dk`;
}

const arrivalTime = (seconds, from = Date.now()) => clockFormat.format(new Date(from + seconds * 1000));
const lowerFirst = (text) => text.charAt(0).toLocaleLowerCase('tr-TR') + text.slice(1);
const coordinate = (point) => `${point.lat.toFixed(6)},${point.lon.toFixed(6)}`;

let toastTimer;
function toast(message, isError = false, duration = isError ? 6000 : 3500) {
    const box = $('#toast');
    box.textContent = message;
    box.classList.toggle('error', isError);
    box.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => box.classList.remove('show'), duration);
}

function stored(key, storage = localStorage) {
    try {
        return storage.getItem(key);
    } catch {
        return null;
    }
}

function store(key, value, storage = localStorage) {
    try {
        if (value === null) storage.removeItem(key);
        else storage.setItem(key, value);
    } catch {
        // storage blocked (private mode): the setting is simply not remembered
    }
}

/* ---------- geometry ---------- */

const EARTH_RADIUS = 6371008.8;
const METERS_PER_DEGREE = EARTH_RADIUS * Math.PI / 180;
const toRadians = (degrees) => degrees * Math.PI / 180;

function distanceMeters(a, b) {
    const dPhi = toRadians(b.lat - a.lat);
    const dLambda = toRadians(b.lon - a.lon);
    const h = Math.sin(dPhi / 2) ** 2
        + Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLambda / 2) ** 2;
    return 2 * EARTH_RADIUS * Math.asin(Math.min(1, Math.sqrt(h)));
}

function bearing(a, b) {
    const y = Math.sin(toRadians(b.lon - a.lon)) * Math.cos(toRadians(b.lat));
    const x = Math.cos(toRadians(a.lat)) * Math.sin(toRadians(b.lat))
        - Math.sin(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.cos(toRadians(b.lon - a.lon));
    return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
}

/** Where p falls on segment a-b: fraction t along it and the distance in metres. */
function project(p, a, b) {
    const metersPerDegreeLon = METERS_PER_DEGREE * Math.cos(toRadians(p.lat));
    const ax = (a.lon - p.lon) * metersPerDegreeLon;
    const ay = (a.lat - p.lat) * METERS_PER_DEGREE;
    const dx = (b.lon - a.lon) * metersPerDegreeLon;
    const dy = (b.lat - a.lat) * METERS_PER_DEGREE;
    const lengthSquared = dx * dx + dy * dy;
    const t = lengthSquared === 0 ? 0 : Math.min(1, Math.max(0, -(ax * dx + ay * dy) / lengthSquared));
    return { t, distance: Math.hypot(ax + t * dx, ay + t * dy) };
}

/** Where routes and search work: all of Turkey when the nationwide services are on, else the detailed area. */
function reachable(point) {
    if (!state.area?.nationwide) return insideArea(point);
    const [minLat, minLon, maxLat, maxLon] = state.area.nationwideBounds;
    return point.lat >= minLat && point.lat <= maxLat && point.lon >= minLon && point.lon <= maxLon;
}

const reachName = () => (state.area?.nationwide ? 'Türkiye' : state.area?.name ?? 'harita alanı');

/** The detailed area: own routing engine, incidents, businesses on the map. */
function insideArea(point, marginMeters = 1000) {
    if (!state.area) return true;
    const [minLat, minLon, maxLat, maxLon] = state.area.bounds;
    const latMargin = marginMeters / METERS_PER_DEGREE;
    const lonMargin = latMargin / Math.cos(toRadians(point.lat));
    return point.lat >= minLat - latMargin && point.lat <= maxLat + latMargin
        && point.lon >= minLon - lonMargin && point.lon <= maxLon + lonMargin;
}

/* ---------- API ---------- */

class ApiError extends Error {
    constructor(status, problem) {
        const fieldErrors = (problem?.errors ?? [])
            .map((error) => `${FIELDS[error.field] ?? error.field}: ${error.message}`);
        const fallback = WAKING_STATUSES.includes(status)
            ? 'Sunucu şu an yanıt vermiyor; uyanıyor olabilir, birkaç saniye sonra tekrar deneyin'
            : `İstek başarısız (HTTP ${status})`;
        super([problem?.detail ?? fallback, ...fieldErrors].join(' · '));
        this.status = status;
    }
}

async function request(method, path, body, retried = false) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    // account requests carry the account's token; the rest the administrator's, when one was given
    const accountRequest = path.startsWith('account');
    const token = accountRequest ? null : stored(TOKEN_KEY, sessionStorage);
    if (token && method !== 'GET') headers['X-Admin-Token'] = token;
    const signedIn = accountRequest ? session() : null;
    if (signedIn) headers.Authorization = `Bearer ${signedIn.token}`;

    let response;
    try {
        response = await fetch('api/' + path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    } catch {
        throw new ApiError(0, { detail: 'Sunucuya ulaşılamadı; bağlantınızı kontrol edin' });
    }
    if (response.status === 401 && !retried && !accountRequest) {
        store(TOKEN_KEY, null, sessionStorage);
        updateAdminButton();
        if (askToken()) return request(method, path, body, true);
    }
    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new ApiError(response.status, data);
    return data;
}

const api = {
    get: (path) => request('GET', path),
    post: (path, body) => request('POST', path, body ?? {})
};

function askToken() {
    const token = prompt('Bu sürümde olay bildirmek için yönetici anahtarı gerekiyor:');
    if (token) {
        store(TOKEN_KEY, token.trim(), sessionStorage);
        updateAdminButton();
    }
    return Boolean(token);
}

function updateAdminButton() {
    const button = $('#admin-button');
    const unlocked = Boolean(stored(TOKEN_KEY, sessionStorage));
    button.classList.toggle('unlocked', unlocked);
    button.title = unlocked ? 'Yönetici anahtarı girildi (silmek için tıklayın)' : 'Yönetici anahtarı';
}

/* ---------- map ---------- */

function setUpMap() {
    map = L.map('map', { zoomControl: false, zoomSnap: 0.5 }).setView([40.9876, 29.029], 14);
    map.attributionControl.setPrefix('<a href="https://leafletjs.com">Leaflet</a>');
    L.control.zoom({ position: 'bottomright' }).addTo(map);
    addBasemap();
    // traffic colours lie under incidents and the route
    map.createPane('traffic').style.zIndex = 350;
    layers.traffic = L.layerGroup().addTo(map);
    layers.area = L.layerGroup().addTo(map);
    layers.incidents = L.layerGroup().addTo(map);
    layers.route = L.layerGroup().addTo(map);
    layers.businesses = L.layerGroup().addTo(map);
    layers.along = L.layerGroup().addTo(map);
    layers.preview = L.layerGroup().addTo(map);
    layers.here = L.layerGroup().addTo(map);
    layers.markers = L.layerGroup().addTo(map);
    map.on('click', onMapClick);
    // during navigation a drag means the traveller wants to look around
    map.on('dragstart', stopFollowing);
    let businessTimer;
    map.on('moveend', () => {
        clearTimeout(businessTimer);
        businessTimer = setTimeout(() => {
            refreshBusinesses();
            refreshTraffic();
        }, 300);
    });
}

/**
 * The calm vector basemap of OpenFreeMap, drawn by MapLibre under Leaflet's layers, dark when the device is. Without
 * WebGL, or if the style cannot be loaded, the standard OpenStreetMap tiles are used instead.
 */
function addBasemap() {
    const styleUrl = () => (darkScheme.matches ? BASEMAP_STYLES.dark : BASEMAP_STYLES.light);
    const osmTiles = () => L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> katkıda bulunanlar'
    }).addTo(map);
    if (!L.maplibreGL || !webGlAvailable()) {
        osmTiles();
        return;
    }
    const vector = L.maplibreGL({ style: styleUrl(), attribution: BASEMAP_ATTRIBUTION, maxZoom: 19 }).addTo(map);
    const gl = vector.getMaplibreMap();
    let loaded = false;
    gl.once('load', () => {
        loaded = true;
    });
    gl.on('error', () => {
        // a basemap that never loads is replaced; later tile errors are left to MapLibre's own retries
        if (loaded || !map.hasLayer(vector)) return;
        map.removeLayer(vector);
        osmTiles();
    });
    // a few icons of the dark style are missing from its sprite; an empty image keeps MapLibre quiet
    gl.on('styleimagemissing', (event) => {
        if (!gl.hasImage(event.id)) gl.addImage(event.id, { width: 1, height: 1, data: new Uint8Array(4) });
    });
    darkScheme.addEventListener?.('change', () => gl.setStyle(styleUrl()));
}

function webGlAvailable() {
    try {
        const canvas = document.createElement('canvas');
        return Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl'));
    } catch {
        return false;
    }
}

function showArea(area) {
    state.area = area;
    $('#area-name').textContent = !area.nationwide ? area.name
        : area.trafficCells > 0 ? 'Türkiye geneli · İstanbul trafiği' : 'Türkiye geneli navigasyon';
    const [minLat, minLon, maxLat, maxLon] = area.bounds;
    const bounds = L.latLngBounds([minLat, minLon], [maxLat, maxLon]);
    L.rectangle(bounds, { color: '#2e3038', weight: 1.2, opacity: 0.5, dashArray: '4 8', fill: false, interactive: false })
        .addTo(layers.area);
    if (!area.nationwide) {
        $('#hint').textContent = 'Başlangıç ve varış için yer arayın ya da kesikli çerçevenin içinde haritaya dokunun.';
    } else if (area.nationalGraph) {
        $('#hint').textContent = 'Türkiye\'nin her yerinde yer arayın ya da haritaya dokunun. Araç rotalarını kendi '
            + 'motorumuz hesaplar, İstanbul\'da trafiği de hesaba katar. Kesikli çerçevenin içinde '
            + `(${area.name}) yürüyüş, bisiklet ve yol olayları da vardır.`;
    }
    map.setMinZoom(6);
    if (!state.from && !state.to) map.setView(area.center, 14);
}

function onMapClick(event) {
    if (state.nav) return;
    const point = { lat: event.latlng.lat, lon: event.latlng.lng };
    if (state.picking) {
        stopPicking();
        if (insideArea(point)) openIncidentDialog(point);
        else toast(`Olay bildirimi şimdilik yalnızca ayrıntılı bölgede (${state.area?.name ?? 'Kadıköy'}) yapılabiliyor`, true);
        return;
    }
    if (!reachable(point)) {
        toast(`Bu nokta ${reachName()} sınırlarının dışında`, true);
        return;
    }
    const street = el('div', { class: 'muted small' }, 'Yol adı aranıyor…');
    const goHere = el('button', { class: 'button small primary', type: 'button' }, 'Buraya git');
    const startHere = el('button', { class: `button small${state.from ? '' : ' primary'}`, type: 'button' },
        'Buradan başla');
    const report = insideArea(point) ? el('button', { class: 'button small ghost', type: 'button' }, 'Olay bildir') : null;
    const canStop = state.from && state.to && state.stops.length < MAX_STOPS && state.mode !== 'transit';
    const stopHere = canStop ? el('button', { class: 'button small', type: 'button' }, 'Durak ekle') : null;
    const popup = L.popup().setLatLng(event.latlng)
        .setContent(el('div', { class: 'popup' }, el('div', { class: 'popup-title' }, 'Seçilen nokta'), street,
            el('div', { class: 'popup-actions' }, startHere, goHere, stopHere, report)))
        .openOn(map);
    stopHere?.addEventListener('click', () => {
        map.closePopup(popup);
        addStop(point);
    });
    startHere.addEventListener('click', () => {
        map.closePopup(popup);
        setStop('from', point);
    });
    goHere.addEventListener('click', () => {
        map.closePopup(popup);
        setStop('to', point);
    });
    report?.addEventListener('click', () => {
        map.closePopup(popup);
        openIncidentDialog(point);
    });
    streetName(point).then((name) => {
        point.label = name ?? 'Seçilen nokta';
        street.textContent = name ?? 'İsimsiz yol';
    });
}

async function streetName(point) {
    try {
        const street = await api.get(`places/street?lat=${point.lat.toFixed(6)}&lon=${point.lon.toFixed(6)}`);
        return street.street ?? null;
    } catch {
        return null;
    }
}

/* ---------- start and destination ---------- */

/** 'from', 'to' or 'stop0'…'stop2': the stops of a route as the search boxes and markers name them. */
function stopIndex(which) {
    return which.startsWith('stop') ? Number(which.slice(4)) : -1;
}

function getStop(which) {
    const index = stopIndex(which);
    return index >= 0 ? state.stops[index] ?? null : state[which];
}

function putStop(which, point) {
    const index = stopIndex(which);
    if (index >= 0) state.stops[index] = point;
    else state[which] = point;
}

function markerFor(which, point) {
    const index = stopIndex(which);
    const html = index >= 0 ? el('div', { class: 'marker-stop' }, String(index + 1))
        : el('div', { class: which === 'from' ? 'marker-start' : 'marker-end' });
    const size = index >= 0 ? 26 : which === 'from' ? 22 : 30;
    const marker = L.marker([point.lat, point.lon], {
        draggable: true,
        keyboard: false,
        title: index >= 0 ? `${index + 1}. ara durak (sürükleyebilirsiniz)`
            : which === 'from' ? 'Başlangıç (sürükleyebilirsiniz)' : 'Varış (sürükleyebilirsiniz)',
        icon: L.divIcon({
            className: '',
            html,
            iconSize: [size, size],
            iconAnchor: which === 'to' ? [15, 30] : [size / 2, size / 2]
        })
    });
    marker.on('dragend', () => {
        const { lat, lng } = marker.getLatLng();
        setStop(which, { lat, lon: lng });
    });
    return marker;
}

async function setStop(which, point, { plan = true } = {}) {
    putStop(which, point);
    const input = $(`#${which}-input`);
    input.value = point.label ?? 'Seçilen nokta';
    drawStops();
    if (which === 'to') {
        if (pendingShortcut) saveShortcut(pendingShortcut, point);
        updateSaveButton();
    }
    if (!point.label) {
        streetName(point).then((name) => {
            if (getStop(which) !== point) return;
            point.label = name ?? 'Seçilen nokta';
            input.value = point.label;
        });
    }
    if (plan) await planRoute();
}

function clearStop(which) {
    putStop(which, null);
    drawStops();
    // an emptied stop leaves the route; an emptied start or destination ends it
    if (stopIndex(which) >= 0) {
        planRoute();
        return;
    }
    if (which === 'to') updateSaveButton();
    clearRoute();
    writeHash();
}

function drawStops() {
    layers.markers.clearLayers();
    const stops = state.mode === 'transit' ? [] : state.stops.map((_, index) => `stop${index}`);
    for (const which of ['from', ...stops, 'to']) {
        const point = getStop(which);
        if (point) markerFor(which, point).addTo(layers.markers);
    }
}

/** The fields of the stops between the start and the destination, and the dots of the rail beside them. */
function renderStopFields() {
    $('#stop-list').replaceChildren(...state.stops.map((point, index) => {
        const input = el('input', {
            id: `stop${index}-input`,
            type: 'search',
            placeholder: state.stops.length > 1 ? `${index + 1}. ara durak` : 'Ara durak',
            enterkeyhint: 'search',
            'aria-label': `${index + 1}. ara durak`,
            'aria-autocomplete': 'list',
            'aria-controls': 'suggestions'
        });
        input.value = point?.label ?? '';
        attachSearch(input, `stop${index}`);
        return el('div', { class: 'field stop-field' }, input, el('button', {
            class: 'field-action',
            type: 'button',
            title: 'Durağı kaldır',
            'aria-label': `${index + 1}. ara durağı kaldır`,
            onclick: () => removeStop(index)
        }, icon('close')));
    }));
    $('#stop-rail').replaceChildren(el('span', { class: 'rail-dot from' }),
        ...state.stops.flatMap(() => [el('span', { class: 'rail-line' }), el('span', { class: 'rail-dot stop' })]),
        el('span', { class: 'rail-line' }), el('span', { class: 'rail-dot to' }));
    $('#add-stop').hidden = state.stops.length >= MAX_STOPS;
}

/** Adds a stop before the destination: a point (from the map or a search along the route), or an empty field. */
function addStop(point = null) {
    if (state.stops.length >= MAX_STOPS) {
        toast(`En fazla ${MAX_STOPS} ara durak eklenebilir`, true);
        return;
    }
    state.stops.push(null);
    renderStopFields();
    const which = `stop${state.stops.length - 1}`;
    if (point) setStop(which, point);
    else $(`#${which}-input`).focus();
}

function removeStop(index) {
    const hadPoint = Boolean(state.stops[index]);
    state.stops.splice(index, 1);
    renderStopFields();
    drawStops();
    if (hadPoint) planRoute();
}

function swapStops() {
    [state.from, state.to] = [state.to, state.from];
    state.stops.reverse();
    renderStopFields();
    $('#from-input').value = state.from?.label ?? '';
    $('#to-input').value = state.to?.label ?? '';
    drawStops();
    planRoute();
}

/* ---------- the device's real position ---------- */

const LOCATION_ERRORS = {
    1: 'Konum izni verilmedi. Adres çubuğundaki konum simgesinden izin verin; Mac\'te ayrıca Sistem Ayarları › '
        + 'Gizlilik ve Güvenlik › Konum Servisleri bölümünde tarayıcınızı açın.',
    2: 'Konum belirlenemedi. Wi-Fi\'ı açıp birazdan tekrar deneyin.',
    3: 'Konum zamanında alınamadı; tekrar deneyin.'
};

/** Why location cannot work on this page at all, or null. */
function locationProblem() {
    if (!window.isSecureContext) {
        return 'Tarayıcılar konumu yalnızca https:// ya da localhost adresinden açılan sayfalara verir. '
            + 'Telefonda denemek için uygulamanın internetteki (https) adresini kullanın.';
    }
    return navigator.geolocation ? null : 'Tarayıcınız konum paylaşmayı desteklemiyor.';
}

function locateMe() {
    const problem = locationProblem();
    if (problem) {
        toast(problem, true, 9000);
        return;
    }
    if (here.position && here.watchId !== null) {
        actOnLocation(here.position);
        return;
    }
    here.pendingLocate = true;
    toast('Konumunuz alınıyor…');
    startWatchingLocation();
}

function startWatchingLocation() {
    if (here.watchId !== null || locationProblem()) return;
    here.watchId = navigator.geolocation.watchPosition(onLocation, onLocationError,
        { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 });
    $('#locate').classList.add('active');
}

function stopWatchingLocation() {
    if (here.watchId === null) return;
    navigator.geolocation.clearWatch(here.watchId);
    here.watchId = null;
    $('#locate').classList.remove('active');
}

function onLocation(position) {
    here.position = {
        lat: position.coords.latitude,
        lon: position.coords.longitude,
        accuracy: position.coords.accuracy
    };
    drawMyLocation();
    if (here.pendingLocate) {
        here.pendingLocate = false;
        actOnLocation(here.position);
    }
}

function onLocationError(error) {
    // later updates of a running watch fail quietly; only an answer to a tap (or a refusal) is reported
    if (!here.pendingLocate && error.code !== 1) return;
    here.pendingLocate = false;
    toast(LOCATION_ERRORS[error.code] ?? 'Konum alınamadı.', true, 9000);
    if (error.code === 1) stopWatchingLocation();
}

/** Inside the area the position becomes the start; outside it the map shows both and says how far it is. */
function actOnLocation(point) {
    if (reachable(point)) {
        setStop('from', { lat: point.lat, lon: point.lon, label: 'Konumum' });
        map.setView([point.lat, point.lon], 16);
        return;
    }
    const corners = state.area ? [[state.area.bounds[0], state.area.bounds[1]], [state.area.bounds[2], state.area.bounds[3]]]
        : [];
    map.fitBounds(L.latLngBounds([...corners, [point.lat, point.lon]]), panelPadding());
    toast(state.area?.nationwide
        ? 'Konumunuz haritada mavi nokta olarak görünüyor; Türkiye dışında olduğunuz için başlangıç yapılmadı.'
        : `Konumunuz haritada mavi nokta olarak görünüyor; ${state.area?.name ?? 'harita alanı'} alanına yaklaşık `
            + `${formatDistance(distanceToArea(point))} uzakta. Rota şimdilik yalnızca kesikli çerçevenin içinde `
            + 'hesaplanabiliyor.', false, 10000);
}

function drawMyLocation() {
    const point = here.position;
    if (!point) return;
    const latLng = [point.lat, point.lon];
    if (here.marker) {
        here.marker.setLatLng(latLng);
        here.circle.setLatLng(latLng).setRadius(point.accuracy ?? 0);
        return;
    }
    here.circle = L.circle(latLng, {
        radius: point.accuracy ?? 0,
        color: '#2563eb',
        weight: 1,
        opacity: 0.45,
        fillColor: '#3b82f6',
        fillOpacity: 0.12,
        interactive: false
    }).addTo(layers.here);
    here.marker = L.marker(latLng, {
        keyboard: false,
        title: 'Gerçek konumunuz',
        zIndexOffset: 500,
        icon: L.divIcon({ className: '', html: el('div', { class: 'marker-here' }), iconSize: [20, 20],
            iconAnchor: [10, 10] })
    }).addTo(layers.here);
}

/** While navigating with live GPS the arrow already marks the position; the dot would only sit under it. */
function refreshHereVisibility() {
    const hide = state.nav?.source === 'gps';
    if (hide && map.hasLayer(layers.here)) map.removeLayer(layers.here);
    if (!hide && !map.hasLayer(layers.here)) map.addLayer(layers.here);
}

function distanceToArea(point) {
    if (!state.area) return 0;
    const [minLat, minLon, maxLat, maxLon] = state.area.bounds;
    return distanceMeters(point, {
        lat: Math.min(Math.max(point.lat, minLat), maxLat),
        lon: Math.min(Math.max(point.lon, minLon), maxLon)
    });
}

/* ---------- place search ---------- */

const search = { input: null, results: [], active: -1, request: 0, timer: null };

function setUpSearch() {
    for (const which of ['from', 'to']) attachSearch($(`#${which}-input`), which);
}

function attachSearch(input, which) {
    input.addEventListener('input', () => {
        if (!input.value.trim()) {
            hideSuggestions();
            if (getStop(which)) clearStop(which);
            return;
        }
        clearTimeout(search.timer);
        search.timer = setTimeout(() => findPlaces(input, which), 180);
    });
    input.addEventListener('keydown', (event) => onSearchKey(event, which));
    input.addEventListener('focus', () => {
        input.select();
        const recent = recentPlaces();
        if (!input.value.trim() && recent.length) {
            const near = here.position ?? { lat: map.getCenter().lat, lon: map.getCenter().lng };
            showSuggestions(input, which, recent.map((place) => ({ ...place, name: place.label, recent: true })),
                'Son aramalar', near);
        }
    });
    input.addEventListener('blur', () => setTimeout(hideSuggestions, 150));
}

async function findPlaces(input, which) {
    const query = input.value.trim();
    if (query.length < 2) {
        hideSuggestions();
        return;
    }
    const requestId = ++search.request;
    try {
        const near = here.position ?? { lat: map.getCenter().lat, lon: map.getCenter().lng };
        const places = await api.get(`places?q=${encodeURIComponent(query)}&limit=8`
            + `&lat=${near.lat.toFixed(4)}&lon=${near.lon.toFixed(4)}`);
        if (requestId !== search.request || document.activeElement !== input) return;
        showSuggestions(input, which, places, null, near);
    } catch (error) {
        toast(error.message, true);
    }
}

/**
 * The suggestions under a search box. Places that share a name (the branches of a chain) are told apart by their
 * kind, street and neighbourhood, their distance from where the user looks, and a numbered pin on the map.
 *
 * @param near where distances are measured from, or null
 */
function showSuggestions(input, which, places, title = null, near = null) {
    const list = $('#suggestions');
    search.input = input;
    search.results = places;
    search.active = places.length ? 0 : -1;
    const items = places.map((place, index) => el('li', {
        role: 'option',
        'aria-selected': index === 0 ? 'true' : 'false',
        onmousedown: (event) => {
            event.preventDefault();
            choosePlace(which, place);
        },
        onmouseenter: () => setActiveSuggestion(index, { pan: true })
    },
    el('span', { class: 'suggestion-icon' },
        icon(place.recent ? 'history' : CATEGORY_ICONS[place.category] ?? 'pin'),
        el('span', { class: 'suggestion-number', 'aria-hidden': 'true' }, String(index + 1))),
    el('span', { class: 'suggestion-text' }, el('span', {}, place.name),
        placeDescription(place) ? el('span', { class: 'suggestion-detail' }, placeDescription(place)) : null),
    near ? el('span', { class: 'suggestion-distance' }, formatDistance(distanceMeters(near, place))) : null));
    list.replaceChildren(...(title ? [el('li', { class: 'label', role: 'presentation' }, title)] : []),
        ...(places.length ? items : [el('li', { class: 'empty' }, 'Bu adla bir yer bulunamadı')]));
    const field = input.closest('.field') ?? input;
    list.style.top = `${field.offsetTop + field.offsetHeight + 4}px`;
    list.hidden = false;
    showResultMarkers(places);
}

/** "Bahariye Caddesi, Osmanağa · Süpermarket": where first, since that is what tells branches apart, then what. */
function placeDescription(place) {
    const kind = place.recent ? place.kind : place.kind ?? place.categoryLabel;
    return [place.detail, kind].filter(Boolean).join(' · ');
}

/** "Migros, Bahariye Caddesi": the name with its street, so a chosen branch stays recognisable in the box. */
function placeLabel(place) {
    const street = place.detail?.split(',')[0]?.trim();
    return street && street !== place.name && !place.name.includes(street) ? `${place.name}, ${street}` : place.name;
}

/** Numbered pins for the suggestions, so places with the same name can be found on the map. */
function showResultMarkers(places) {
    layers.preview.clearLayers();
    search.markers = places.map((place, index) => L.marker([place.lat, place.lon], {
        keyboard: false,
        interactive: false,
        zIndexOffset: index === search.active ? 1200 : 800,
        icon: L.divIcon({
            className: '',
            html: el('div', { class: `result-marker${index === search.active ? ' active' : ''}` },
                el('span', {}, String(index + 1))),
            iconSize: [28, 28],
            iconAnchor: [14, 34]
        })
    }).addTo(layers.preview));
}

function setActiveSuggestion(index, { pan = false } = {}) {
    search.active = index;
    const options = [...$('#suggestions').querySelectorAll('[role="option"]')];
    options.forEach((item, i) => item.setAttribute('aria-selected', i === index ? 'true' : 'false'));
    options[index]?.scrollIntoView({ block: 'nearest' });
    search.markers?.forEach((marker, i) => {
        marker.getElement()?.querySelector('.result-marker')?.classList.toggle('active', i === index);
        marker.setZIndexOffset(i === index ? 1200 : 800);
    });
    const place = search.results[index];
    if (pan && place && !map.getBounds().pad(-0.1).contains([place.lat, place.lon])) {
        map.panTo([place.lat, place.lon], { animate: !reducedMotion.matches });
    }
}

function hideSuggestions() {
    $('#suggestions').hidden = true;
    search.results = [];
    search.active = -1;
    search.markers = [];
    layers.preview?.clearLayers();
}

function onSearchKey(event, which) {
    const list = $('#suggestions');
    if (list.hidden || !search.results.length) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        const step = event.key === 'ArrowDown' ? 1 : -1;
        setActiveSuggestion((search.active + step + search.results.length) % search.results.length, { pan: true });
    } else if (event.key === 'Enter') {
        event.preventDefault();
        choosePlace(which, search.results[Math.max(0, search.active)]);
    } else if (event.key === 'Escape') {
        hideSuggestions();
    }
}

function choosePlace(which, place) {
    hideSuggestions();
    $(`#${which}-input`).blur();
    const kind = place.recent ? place.kind : place.kind ?? place.categoryLabel;
    rememberPlace({ label: place.name, kind, detail: place.detail, lat: place.lat, lon: place.lon });
    map.setView([place.lat, place.lon], Math.max(map.getZoom(), 15));
    setStop(which, { lat: place.lat, lon: place.lon, label: placeLabel(place) });
}

/* ---------- quick destinations and recent searches ---------- */

function renderQuickDestinations() {
    $('#quick-destinations').replaceChildren(...QUICK_DESTINATIONS.map((place) => el('button', {
        class: 'quick-chip',
        type: 'button',
        title: `${place.kind} · ${place.detail}`,
        onclick: () => goTo(place)
    }, el('span', { class: 'chip-icon' }, icon(place.icon)), place.label)));
}

/** Makes the place the destination; without a start, the own position (if known) or the user's choice is next. */
function goTo(place) {
    const point = { lat: place.lat, lon: place.lon, label: place.icon ? place.label : placeLabel({ ...place, name: place.label }) };
    rememberPlace(place);
    if (!state.from && here.position && reachable(here.position)) {
        setStop('from', { lat: here.position.lat, lon: here.position.lon, label: 'Konumum' }, { plan: false });
    }
    setStop('to', point);
    if (!state.from) {
        map.setView([point.lat, point.lon], 15);
        toast('Şimdi başlangıcı seçin: arayın, haritaya dokunun ya da konumunuzu kullanın');
        $('#from-input').focus();
    }
}

function recentPlaces() {
    try {
        const list = JSON.parse(stored(RECENT_KEY) ?? '[]');
        return Array.isArray(list) ? list.filter((place) => typeof place?.label === 'string'
            && Number.isFinite(place.lat) && Number.isFinite(place.lon)) : [];
    } catch {
        return [];
    }
}

function rememberPlace(place) {
    if (!place.label || ['Konumum', 'Seçilen nokta'].includes(place.label)) return;
    const entry = { label: place.label, kind: place.kind ?? null, detail: place.detail ?? null, lat: place.lat,
        lon: place.lon };
    // two branches of a chain share a name but not a place
    const same = (other) => other.label === entry.label && distanceMeters(other, entry) < 50;
    const list = [entry, ...recentPlaces().filter((other) => !same(other))].slice(0, MAX_RECENT);
    store(RECENT_KEY, JSON.stringify(list));
    renderRecent();
}

function renderRecent() {
    const list = recentPlaces();
    $('#recent-block').hidden = list.length === 0;
    $('#recent-list').replaceChildren(...list.map((place) => el('li', {
        tabindex: '0',
        role: 'button',
        onclick: () => goTo(place),
        onkeydown: (event) => {
            if (event.key === 'Enter') goTo(place);
        }
    }, el('span', { class: 'place-icon' }, icon('history')),
    el('span', { class: 'place-text' }, el('strong', {}, place.label),
        placeDescription({ ...place, recent: true }) ? el('span', {}, placeDescription({ ...place, recent: true }))
            : null))));
}

/* ---------- home, work and saved places ---------- */

/** Ev or İş while the user picks its place: the next destination chosen is saved as it. */
let pendingShortcut = null;

function favorites() {
    const valid = (place) => place && typeof place.label === 'string' && Number.isFinite(place.lat)
        && Number.isFinite(place.lon);
    try {
        const saved = JSON.parse(stored(FAVORITES_KEY) ?? '{}') ?? {};
        return {
            home: valid(saved.home) ? saved.home : null,
            work: valid(saved.work) ? saved.work : null,
            places: Array.isArray(saved.places) ? saved.places.filter(valid) : []
        };
    } catch {
        return { home: null, work: null, places: [] };
    }
}

/** Keeps the saved places on this device and shows them; signed in, the account gets them too. */
function storeFavorites(next) {
    keepFavorites(next);
    if (session()) {
        store(PLACES_DIRTY_KEY, '1');
        clearTimeout(pushTimer);
        pushTimer = setTimeout(pushPlaces, 600);
    }
}

function keepFavorites(next) {
    store(FAVORITES_KEY, JSON.stringify(next));
    renderShortcuts();
    renderFavorites();
    updateSaveButton();
}

/* ---------- accounts: the same places on every device ---------- */

let accountMode = 'login';
let pushTimer = null;

/** The signed-in account, while its token is valid. */
function session() {
    try {
        const current = JSON.parse(stored(SESSION_KEY) ?? 'null');
        return current?.token && Date.parse(current.expiresAt) > Date.now() ? current : null;
    } catch {
        return null;
    }
}

function updateAccountButton() {
    const current = session();
    const button = $('#account-button');
    button.classList.toggle('unlocked', Boolean(current));
    button.title = current ? `Hesap: ${current.name ?? current.email}`
        : 'Giriş yapın: Ev, İş ve kayıtlı yerleriniz her cihazda';
}

function openAccount() {
    const current = session();
    $('#account-signed-out').hidden = Boolean(current);
    $('#account-signed-in').hidden = !current;
    $('#account-error').hidden = true;
    resetDelete();
    if (current) {
        $('#account-name').textContent = current.name ?? 'Hesabınız';
        $('#account-email').textContent = current.email;
    } else {
        setAccountMode(accountMode);
    }
    $('#account-dialog').showModal();
}

function setAccountMode(mode) {
    accountMode = mode;
    const register = mode === 'register';
    $('#account-tab-login').setAttribute('aria-selected', String(!register));
    $('#account-tab-register').setAttribute('aria-selected', String(register));
    $('#account-name-field').hidden = !register;
    $('#account-submit').textContent = register ? 'Hesap aç' : 'Giriş yap';
    $('#account-form').elements.password.autocomplete = register ? 'new-password' : 'current-password';
    $('#account-error').hidden = true;
}

async function submitAccount() {
    const form = $('#account-form');
    const error = $('#account-error');
    const submit = $('#account-submit');
    const register = accountMode === 'register';
    const body = { email: form.elements.email.value.trim(), password: form.elements.password.value };
    if (register) body.name = form.elements.name.value.trim() || null;
    error.hidden = true;
    submit.disabled = true;
    try {
        const started = await request('POST', register ? 'account/register' : 'account/login', body);
        store(SESSION_KEY, JSON.stringify({
            token: started.token,
            expiresAt: started.expiresAt,
            email: started.account.email,
            name: started.account.name ?? null
        }));
        form.elements.password.value = '';
        updateAccountButton();
        await mergePlaces();
        $('#account-dialog').close();
        toast(register ? 'Hesabınız açıldı; kayıtlı yerleriniz artık her cihazda'
            : `Hoş geldiniz${started.account.name ? `, ${started.account.name}` : ''}`);
    } catch (problem) {
        error.textContent = problem.message;
        error.hidden = false;
    } finally {
        submit.disabled = false;
    }
}

/**
 * After signing in, what the account keeps and what this device kept come together: the account's home and
 * workplace win, saved places from both are kept once.
 */
async function mergePlaces() {
    const remote = accountPlaces(await api.get('account/places'));
    const local = favorites();
    const merged = { home: remote.home ?? local.home, work: remote.work ?? local.work, places: [...remote.places] };
    for (const place of local.places) {
        if (merged.places.length >= MAX_FAVORITES) break;
        if (!merged.places.some((kept) => distanceMeters(kept, place) < SAME_PLACE_METERS)) merged.places.push(place);
    }
    keepFavorites(merged);
    if (JSON.stringify(placesBody(merged)) !== JSON.stringify(placesBody(remote))) await pushPlaces();
}

/** On start: the account's places replace this device's copy, unless this device has changes the account missed. */
async function refreshAccount() {
    updateAccountButton();
    if (!session()) return;
    try {
        if (stored(PLACES_DIRTY_KEY)) await pushPlaces();
        else keepFavorites(accountPlaces(await api.get('account/places')));
    } catch (error) {
        if (error.status === 401) signOut({ keepPlaces: true, message: 'Oturumunuz sona erdi; yeniden giriş yapın' });
    }
}

async function pushPlaces() {
    if (!session()) return;
    try {
        await request('PUT', 'account/places', placesBody(favorites()));
        store(PLACES_DIRTY_KEY, null);
    } catch (error) {
        if (error.status === 401) {
            signOut({ keepPlaces: true, message: 'Oturumunuz sona erdi; yeniden giriş yapınca yerleriniz hesabınıza yazılır' });
        } else {
            toast('Kayıtlı yerler hesabınıza yazılamadı; bir sonraki açılışta yeniden denenecek', true);
        }
    }
}

/** The places as the server sends them: a missing home or workplace is none. */
function accountPlaces(remote) {
    return { home: remote?.home ?? null, work: remote?.work ?? null, places: remote?.places ?? [] };
}

/** The places as the server takes them. */
function placesBody(saved) {
    const one = (place) => (place ? {
        label: String(place.label).slice(0, 200),
        detail: place.detail ? String(place.detail).slice(0, 200) : null,
        lat: place.lat,
        lon: place.lon
    } : null);
    return { home: one(saved.home), work: one(saved.work), places: saved.places.slice(0, MAX_FAVORITES).map(one) };
}

/**
 * Signs out on this device. The account's places leave the device with it, so that the next person using it does
 * not see someone's home; when they could not be saved to the account, they stay.
 */
function signOut({ keepPlaces = false, message = 'Çıkış yapıldı; kayıtlı yerleriniz hesabınızda duruyor' } = {}) {
    clearTimeout(pushTimer);
    store(SESSION_KEY, null);
    store(PLACES_DIRTY_KEY, null);
    if (!keepPlaces) keepFavorites({ home: null, work: null, places: [] });
    updateAccountButton();
    toast(message);
}

/** Deleting an account takes a second tap, without a browser dialog. */
async function deleteAccount() {
    const button = $('#account-delete');
    if (button.dataset.confirm !== 'yes') {
        button.dataset.confirm = 'yes';
        button.textContent = 'Emin misiniz? Silmek için tekrar dokunun';
        return;
    }
    try {
        await request('DELETE', 'account');
        $('#account-dialog').close();
        signOut({ message: 'Hesabınız ve kayıtlı yerleriniz silindi' });
    } catch (error) {
        toast(error.message, true);
    }
}

function resetDelete() {
    const button = $('#account-delete');
    delete button.dataset.confirm;
    button.textContent = 'Hesabı sil';
}

/**
 * The map's details, waiting for the server while it wakes up: free hosting puts it to sleep when nobody uses it,
 * and the first request then takes about a minute.
 */
async function waitForServer() {
    const started = Date.now();
    let warned = false;
    for (;;) {
        try {
            const area = await api.get('map');
            if (warned) toast('Sunucu hazır');
            return area;
        } catch (error) {
            if (!STARTUP_RETRY_STATUSES.includes(error.status) || Date.now() - started > WAKE_TIMEOUT_MS) throw error;
            if (!warned) {
                toast('Sunucu uyanıyor: ücretsiz sunucu bir süre kullanılmayınca uyur, açılması birkaç dakika '
                    + 'sürebilir…', false, WAKE_TIMEOUT_MS);
                warned = true;
            }
            await new Promise((resolve) => setTimeout(resolve, 4000));
        }
    }
}

/** What a stop is saved as: its name, with the street of a point picked on the map looked up first. */
async function savedPlace(point) {
    const label = point.label && point.label !== 'Seçilen nokta' ? point.label
        : (await streetName(point)) ?? 'Kaydedilen yer';
    return { label, lat: point.lat, lon: point.lon };
}

async function saveShortcut(which, point) {
    pendingShortcut = null;
    $('#to-input').placeholder = 'Nereye gidiyorsunuz?';
    const place = await savedPlace(point);
    storeFavorites({ ...favorites(), [which]: place });
    toast(`${SHORTCUTS[which].label} kaydedildi: ${place.label}`);
}

/** Which of Ev, İş and the saved places a point is. */
function savedAs(point) {
    if (!point) return [];
    const saved = favorites();
    const same = (place) => place && distanceMeters(place, point) < SAME_PLACE_METERS;
    return [...['home', 'work'].filter((which) => same(saved[which])),
        ...(saved.places.some(same) ? ['place'] : [])];
}

function renderShortcuts() {
    const saved = favorites();
    $('#shortcuts').replaceChildren(...Object.entries(SHORTCUTS).map(([which, shortcut]) => {
        const place = saved[which];
        return el('button', {
            class: `shortcut${place ? '' : ' unset'}`,
            type: 'button',
            title: place ? `${shortcut.label}: ${place.label}` : `${shortcut.label} adresini ayarlayın`,
            onclick: () => useShortcut(which)
        }, el('span', { class: 'shortcut-icon' }, icon(shortcut.icon)),
        el('span', { class: 'shortcut-text' }, el('strong', {}, shortcut.label),
            el('span', {}, place ? place.label : 'Dokunup seçin')));
    }));
}

/** One tap: a route to Ev or İş, or, if it is not set yet, picking its place. */
function useShortcut(which) {
    const place = favorites()[which];
    if (place) {
        goTo({ ...place, icon: SHORTCUTS[which].icon });
        return;
    }
    pendingShortcut = which;
    const input = $('#to-input');
    input.placeholder = `${SHORTCUTS[which].label} adresini arayın…`;
    input.value = '';
    input.focus();
    toast(`${SHORTCUTS[which].label} için bir yer arayın ya da haritada dokunup "Buraya git"i seçin`);
}

function renderFavorites() {
    const places = favorites().places;
    $('#favorites-block').hidden = places.length === 0;
    $('#favorites-list').replaceChildren(...places.map((place) => el('li', {
        tabindex: '0',
        role: 'button',
        onclick: () => goTo(place),
        onkeydown: (event) => {
            if (event.key === 'Enter') goTo(place);
        }
    }, el('span', { class: 'place-icon saved' }, icon('star')),
    el('span', { class: 'place-text' }, el('strong', {}, place.label),
        place.detail ? el('span', {}, place.detail) : null),
    el('button', {
        class: 'icon-button plain small-remove',
        type: 'button',
        title: 'Kaydedilenlerden çıkar',
        'aria-label': `${place.label}: kaydedilenlerden çıkar`,
        onclick: (event) => {
            event.stopPropagation();
            unsave('place', place);
        }
    }, icon('close')))));
}

function unsave(kind, point) {
    const saved = favorites();
    if (kind === 'place') {
        saved.places = saved.places.filter((place) => distanceMeters(place, point) >= SAME_PLACE_METERS);
    } else {
        saved[kind] = null;
    }
    storeFavorites(saved);
}

/** The star in the destination field: filled when the destination is saved. */
function updateSaveButton() {
    const button = $('#save-place');
    button.hidden = !state.to;
    closeSaveMenu();
    const saved = savedAs(state.to).length > 0;
    button.classList.toggle('saved', saved);
    button.title = saved ? 'Kaydedildi: değiştirmek için dokunun' : 'Bu yeri kaydet';
}

function openSaveMenu() {
    if (!state.to) return;
    const kinds = savedAs(state.to);
    const item = (iconName, text, action) => el('button', {
        type: 'button',
        role: 'menuitem',
        onclick: async () => {
            closeSaveMenu();
            await action();
        }
    }, icon(iconName), text);
    const toggle = async (kind) => {
        if (kinds.includes(kind)) {
            unsave(kind, state.to);
            toast(kind === 'place' ? 'Kaydedilenlerden çıkarıldı' : `${SHORTCUTS[kind].label} kaydı kaldırıldı`);
        } else if (kind === 'place') {
            const place = await savedPlace(state.to);
            const saved = favorites();
            storeFavorites({ ...saved, places: [place, ...saved.places].slice(0, MAX_FAVORITES) });
            toast(`Kaydedildi: ${place.label}`);
        } else {
            await saveShortcut(kind, state.to);
        }
    };
    $('#save-menu').replaceChildren(
        item('star', kinds.includes('place') ? 'Kaydedilenlerden çıkar' : 'Kaydedilenlere ekle', () => toggle('place')),
        item('home', kinds.includes('home') ? 'Ev kaydını kaldır' : 'Ev olarak kaydet', () => toggle('home')),
        item('briefcase', kinds.includes('work') ? 'İş kaydını kaldır' : 'İş olarak kaydet', () => toggle('work')));
    $('#save-menu').hidden = false;
    $('#save-place').setAttribute('aria-expanded', 'true');
    $('#save-menu').querySelector('button').focus();
}

function closeSaveMenu() {
    $('#save-menu').hidden = true;
    $('#save-place').setAttribute('aria-expanded', 'false');
}

/* ---------- routes ---------- */

let routeRequest = 0;

async function planRoute() {
    writeHash();
    if (!state.from || !state.to) {
        clearRoute();
        return;
    }
    const requestId = ++routeRequest;
    $('#result').classList.add('busy');
    try {
        const departAt = departureTime();
        if (state.mode === 'transit') {
            const journey = await api.get(`transit?from=${coordinate(state.from)}&to=${coordinate(state.to)}`
                + (departAt ? `&departAt=${encodeURIComponent(departAt.toISOString())}` : ''));
            if (requestId !== routeRequest) return;
            state.routes = [journey, ...journey.alternatives];
            state.route = journey;
            drawTransit(journey, { fit: true });
            showTransit(journey);
            return;
        }
        const stops = state.stops.filter(Boolean);
        const route = await api.get(`route?from=${coordinate(state.from)}&to=${coordinate(state.to)}`
            + `&mode=${state.mode}&alternatives=${stops.length === 0}`
            + (stops.length ? `&via=${stops.map(coordinate).join(';')}` : '')
            + (departAt ? `&departAt=${encodeURIComponent(departAt.toISOString())}` : ''));
        if (requestId !== routeRequest) return;
        state.routes = [route, ...route.alternatives];
        state.route = route;
        drawRoute(route, { fit: !state.nav });
        showRoute(route);
    } catch (error) {
        if (requestId !== routeRequest) return;
        clearRoute();
        toast(error.message, true);
    } finally {
        if (requestId === routeRequest) $('#result').classList.remove('busy');
    }
}

/** The chosen departure, or null for now. */
function departureTime() {
    if (state.depart === 'now') return null;
    const time = new Date();
    if (state.depart.startsWith('tomorrow-')) {
        time.setDate(time.getDate() + 1);
        time.setHours(Number(state.depart.slice('tomorrow-'.length)), 0, 0, 0);
    } else {
        time.setMinutes(time.getMinutes() + Number(state.depart));
    }
    return time;
}

/**
 * Draws the chosen route and, outside navigation, the other offered routes faded beneath it. A tap anywhere on a
 * faded route, or on its time label, chooses it.
 */
function drawRoute(route, { fit }) {
    layers.route.clearLayers();
    const others = state.nav ? [] : state.routes.filter((other) => other !== route);
    for (const other of others) drawAlternative(other);
    drawChosen(route);
    if (others.length) {
        const placed = others.map((other) => labelPoint(other, route));
        others.forEach((other, i) => routeLabel(other, placed[i], false).addTo(layers.route));
        routeLabel(route, labelPoint(route, others[0], placed[0]), true).addTo(layers.route);
    }
    // during navigation the part already driven stays on top of the redrawn route
    state.nav?.doneLine?.setLatLngs([]).addTo(layers.route);
    if (fit) map.fitBounds(L.latLngBounds([route, ...others].flatMap((one) => one.geometry)), panelPadding());
}

function drawChosen(route) {
    const line = route.geometry;
    const foot = route.mode === 'foot';
    // a light halo lifts the line off a light map; on the dark map a soft shadow does
    L.polyline(line, {
        color: darkScheme.matches ? '#000000' : '#ffffff',
        weight: foot ? 10 : 14,
        opacity: darkScheme.matches ? 0.45 : 0.9,
        lineJoin: 'round',
        interactive: false
    }).addTo(layers.route);
    if (!foot) {
        L.polyline(line, { color: '#2e3038', weight: 10, lineCap: 'round', lineJoin: 'round', interactive: false })
            .addTo(layers.route);
    }
    L.polyline(line, {
        color: foot ? '#2e3038' : '#b8fc00',
        weight: foot ? 6 : 5,
        dashArray: foot ? '1 11' : null,
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false
    }).addTo(layers.route);
    // where traffic or an incident slows the road, the route takes the traffic layer's colour (yellow, orange, red)
    if (!foot) {
        for (const stretch of route.traffic ?? []) {
            L.polyline(line.slice(stretch.from, stretch.to + 1), {
                color: TRAFFIC_COLORS.find(([from]) => stretch.factor >= from)[1],
                weight: 5,
                lineCap: 'round',
                lineJoin: 'round',
                interactive: false
            }).addTo(layers.route);
        }
    }
    // where the routes share a road, taps belong to the chosen one; they go on to the map as before
    if (state.routes.length > 1 && !state.nav) {
        L.polyline(line, { opacity: 0, weight: ROUTE_TAP_WIDTH, lineCap: 'round', lineJoin: 'round', className: 'route-shield' })
            .addTo(layers.route);
    }
    // dashed connectors from the chosen points to where they meet the road
    for (const [which, snapped] of [['from', route.from], ['to', route.to]]) {
        const chosen = state[which];
        if (chosen && snapped.distanceMeters > 10) {
            L.polyline([[chosen.lat, chosen.lon], [snapped.lat, snapped.lon]],
                { color: '#64748b', weight: 2, dashArray: '4 6', interactive: false }).addTo(layers.route);
        }
    }
}

/** A route that can be chosen instead: thinner and paler, with a wide invisible line that catches taps. */
function drawAlternative(route) {
    const line = route.geometry;
    const foot = route.mode === 'foot';
    const style = ALTERNATIVE_STYLE[darkScheme.matches ? 'dark' : 'light'];
    const casing = L.polyline(line, {
        color: style.casing,
        weight: foot ? 6 : 9,
        dashArray: foot ? '1 10' : null,
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false
    }).addTo(layers.route);
    if (!foot) {
        L.polyline(line, { color: style.core, weight: 4.5, lineCap: 'round', lineJoin: 'round', interactive: false })
            .addTo(layers.route);
    }
    L.polyline(line, {
        opacity: 0,
        weight: ROUTE_TAP_WIDTH,
        lineCap: 'round',
        lineJoin: 'round',
        className: 'route-choice',
        bubblingMouseEvents: false
    })
        .bindTooltip(`Bu rotaya geç · ${formatDuration(route.durationSeconds)}`, {
            sticky: true, direction: 'top', offset: [0, -10], className: 'route-tip'
        })
        .on('mouseover', () => casing.setStyle({ color: style.hover }))
        .on('mouseout', () => casing.setStyle({ color: style.casing }))
        .on('click', () => chooseRoute(route))
        .addTo(layers.route);
}

/** The travel time of a route in a small label on its line; tapping the label of another route chooses it. */
function routeLabel(route, at, chosen) {
    const html = el('div', { class: `route-label${chosen ? ' chosen' : ''}` }, formatDuration(route.durationSeconds));
    const marker = L.marker(at, {
        icon: L.divIcon({ className: 'route-label-anchor', html, iconSize: null }),
        interactive: !chosen,
        keyboard: !chosen,
        title: chosen ? '' : `Bu rotaya geç: ${formatDuration(route.durationSeconds)}, ${formatDistance(route.distanceMeters)}`,
        zIndexOffset: chosen ? 600 : 500
    });
    if (!chosen) marker.on('click', () => chooseRoute(route));
    return marker;
}

/**
 * The point of a route farthest from another one, so that its label sits where the two lines part rather than on
 * the road they share. With {@code avoid} (another label), points within a quarter of the route's size from it are
 * skipped, so that two labels do not crowd the one place where the routes part. The other line is thinned to a few
 * hundred points, which is plenty for placing a label.
 */
function labelPoint(route, other, avoid = null) {
    const line = route.geometry;
    const stride = Math.max(1, Math.ceil(other.geometry.length / 300));
    const others = other.geometry.filter((_, i) => i % stride === 0 || i === other.geometry.length - 1)
        .map(([lat, lon]) => ({ lat, lon }));
    const box = L.latLngBounds(line);
    const keepAway = avoid ? 0.25 * distanceMeters({ lat: box.getSouth(), lon: box.getWest() },
        { lat: box.getNorth(), lon: box.getEast() }) : 0;
    let best = null;
    const step = Math.max(1, Math.floor(line.length / 80));
    for (let i = Math.floor(line.length * 0.1); i < Math.ceil(line.length * 0.9); i += step) {
        const point = { lat: line[i][0], lon: line[i][1] };
        if (avoid && distanceMeters(point, { lat: avoid[0], lon: avoid[1] }) < keepAway) continue;
        let away = Infinity;
        for (let k = 0; k + 1 < others.length && away > (best?.away ?? -1); k++) {
            away = Math.min(away, project(point, others[k], others[k + 1]).distance);
        }
        if (!best || away > best.away) best = { index: i, away };
    }
    if (best) return line[best.index];
    return avoid ? labelPoint(route, other) : line[Math.floor(line.length / 2)];
}

/** Makes another offered route the chosen one; the map keeps its view. */
function chooseRoute(route) {
    if (route === state.route || state.nav) return;
    state.route = route;
    drawRoute(route, { fit: false });
    showRoute(route, { scroll: false });
}

/** Padding for fitBounds that keeps the side panel (or the bottom sheet on phones) off the content. */
function panelPadding() {
    if (!phoneLayout.matches) return { paddingTopLeft: [440, 60], paddingBottomRight: [60, 60] };
    return { paddingTopLeft: [24, 70], paddingBottomRight: [24, $('#panel').offsetHeight + 24] };
}

function clearRoute() {
    state.route = null;
    state.routes = [];
    layers.route?.clearLayers();
    searchAlong();
    $('#result').hidden = true;
    $('#home').hidden = false;
}

function showRoute(route, { scroll = true } = {}) {
    $('#home').hidden = true;
    $('#result').hidden = false;
    $('#result').classList.remove('transit');
    $('#summary-time').textContent = formatDuration(route.durationSeconds);
    $('#summary-distance').textContent = formatDistance(route.distanceMeters);
    const departure = route.departAt ? Date.parse(route.departAt) : Date.now();
    const later = departure - Date.now() > 5 * 60000;
    $('#summary-arrival').textContent = later
        ? `${clockFormat.format(new Date(departure))} → ${arrivalTime(route.durationSeconds, departure)}`
        : `Varış ${arrivalTime(route.durationSeconds)}`;
    const delay = route.trafficDelaySeconds;
    const traffic = $('#summary-traffic');
    traffic.hidden = delay === null || delay === undefined || route.mode !== 'car';
    traffic.className = `tag ${delay >= 60 ? 'delay' : 'clear'}`;
    traffic.replaceChildren(icon('traffic'), delay >= 60 ? `Trafik nedeniyle +${formatDuration(delay)}` : 'Trafik akıcı');
    $('#summary-engine').textContent = ENGINE_LABELS[route.engine] ?? route.engine;
    const fastest = isFastest(route);
    const choice = $('#summary-choice');
    choice.hidden = state.routes.length < 2;
    choice.className = `tag ${fastest ? 'clear' : 'alternative'}`;
    choice.textContent = fastest ? 'En hızlı rota' : 'Alternatif rota';
    showOtherRoute(route);
    $('#steps-count').textContent = `${route.instructions.length} adım`;

    const onRoute = route.incidentIds.map((id) => state.incidents.find((incident) => incident.id === id))
        .filter(Boolean);
    $('#route-warnings').replaceChildren(...onRoute.map((incident) => el('li', {}, icon('warning'),
        el('span', {}, `${incident.typeLabel}${incident.street ? ` · ${incident.street}` : ''}: `
            + 'rota bu noktada yavaşlıyor'))));

    $('#steps').replaceChildren(...route.instructions.map((step) => el('li', {
        tabindex: '0',
        onclick: () => focusStep(step),
        onkeydown: (event) => {
            if (event.key === 'Enter') focusStep(step);
        }
    },
    el('span', { class: 'step-icon' }, icon(MANEUVER_ICONS[step.maneuver] ?? 'straight')),
    el('span', {}, el('span', { class: 'step-text' }, step.text),
        step.street ? el('span', { class: 'step-street' }, step.street) : null),
    el('span', { class: 'step-distance' }, step.distanceMeters > 0 ? formatDistance(step.distanceMeters) : ''))));

    $('#route-stats').textContent = routeStats(route);
    searchAlong();
    if (scroll && phoneLayout.matches && !state.nav) {
        // on a phone the sheet scrolls to the summary and the start button
        const panel = $('#panel');
        panel.scrollTo({ top: $('#result').offsetTop - 12, behavior: reducedMotion.matches ? 'auto' : 'smooth' });
    }
}

/** Whether no other offered route is quicker. */
const isFastest = (route) => state.routes.every((other) => other.durationSeconds >= route.durationSeconds);

/** The other offered route in the panel, as a button that chooses it (the same as tapping it on the map). */
function showOtherRoute(route) {
    const other = state.routes.find((one) => one !== route);
    const button = $('#route-switch');
    button.hidden = !other || Boolean(state.nav);
    if (!other) return;
    const delta = other.durationSeconds - route.durationSeconds;
    const compared = Math.abs(delta) < 60 ? 'benzer süre'
        : `${formatDuration(Math.abs(delta))} daha ${delta > 0 ? 'uzun' : 'kısa'}`;
    $('#switch-title').textContent = `${isFastest(other) ? 'En hızlı rota' : 'Alternatif rota'} · `
        + formatDuration(other.durationSeconds);
    $('#switch-detail').textContent = `${formatDistance(other.distanceMeters)} · ${compared}`;
    button.onclick = () => chooseRoute(other);
}

/** Which engine planned the route and what it took into account. */
function routeStats(route) {
    const fastest = isFastest(route);
    const kind = `${MODES[route.mode]} · ${fastest ? 'en hızlı' : 'alternatif'} rota.`;
    const found = fastest ? '' : route.engine === 'osrm' ? ' Alternatifi de OSRM önerdi.'
        : ' Alternatif, en hızlı rotanın yolları pahalılaştırılıp A* yeniden çalıştırılarak bulundu.';
    const visited = route.visitedNodes.toLocaleString('tr-TR');
    if (route.engine === 'mixed') {
        return `${MODES[route.mode]} · ara duraklı rota. Her bacak ayrı hesaplandı; bacaklar farklı motorlarla bulundu `
            + '(ayrıntılı bölgede kendi A* motorumuz, dışında ulusal grafik ya da OSRM).';
    }
    if (route.engine === 'national') {
        const graph = state.area?.nationalGraph;
        const taken = graph?.dataTimestamp ? ` (${dayFormat.format(new Date(graph.dataTimestamp))} verisi)` : '';
        return `${kind} Kendi A* motorumuz Türkiye araç yolu ağında${taken} ${visited} kavşağı inceledi; `
            + 'İstanbul\'daki yollarda İBB trafik verisi hesaba katıldı. Yol olayları yalnızca ayrıntılı bölgedeki '
            + `rotalarda kullanılır.${found}`;
    }
    if (route.engine === 'osrm') {
        return `${kind} Bu rotayı OpenStreetMap verisiyle OSRM (FOSSGIS sunucusu) hesapladı; trafik ve yol `
            + `olayları hesaba katılmadı.${found}`;
    }
    return `${kind} Kendi A* motorumuz ${visited} yol düğümünü inceledi`
        + (route.mode === 'car' ? '; yol olayları ve İBB trafik verisi hesaba katıldı.' : '; yol olayları hesaba katıldı.')
        + found;
}

let stepMarker;
function focusStep(step) {
    stepMarker?.remove();
    stepMarker = L.circleMarker([step.lat, step.lon], {
        radius: 9, color: '#2563eb', weight: 3, fillColor: '#ffffff', fillOpacity: 1
    }).addTo(layers.route);
    map.setView([step.lat, step.lon], 17);
}

/* ---------- journeys by public transport ---------- */

/** Walks dotted, rides in the colour of their kind, an icon where to get on and a ring where to get off. */
function drawTransit(journey, { fit }) {
    layers.route.clearLayers();
    for (const leg of journey.legs) {
        const style = TRANSIT_STYLE[leg.kind] ?? TRANSIT_STYLE.BUS;
        if (leg.kind === 'WALK') {
            L.polyline(leg.geometry, {
                color: style.color, weight: 5, dashArray: '1 10', lineCap: 'round', interactive: false
            }).addTo(layers.route);
            continue;
        }
        L.polyline(leg.geometry, {
            color: '#ffffff', weight: 10, opacity: 0.85, lineCap: 'round', lineJoin: 'round', interactive: false
        }).addTo(layers.route);
        L.polyline(leg.geometry, {
            color: style.color, weight: 6, lineCap: 'round', lineJoin: 'round', interactive: false
        }).addTo(layers.route);
        L.circleMarker(leg.geometry.at(-1), {
            radius: 6, color: style.color, weight: 3, fillColor: '#ffffff', fillOpacity: 1, interactive: false
        }).addTo(layers.route);
        L.marker(leg.geometry[0], {
            keyboard: false,
            title: `${legName(leg)} · ${leg.from}`,
            icon: L.divIcon({
                className: '',
                html: el('div', { class: 'marker-transit', style: `background:${style.color}` }, icon(style.icon)),
                iconSize: [26, 26],
                iconAnchor: [13, 13]
            })
        }).bindTooltip(`${legName(leg)} · ${leg.from}`, { direction: 'top', offset: [0, -14] }).addTo(layers.route);
    }
    if (fit) map.fitBounds(L.latLngBounds(journey.legs.flatMap((leg) => leg.geometry)), panelPadding());
}

/** "Metro M4", "Otobüs 14M", "Vapur", "Marmaray". */
function legName(leg) {
    return leg.line && leg.line !== leg.label ? `${leg.label} ${leg.line}` : leg.label;
}

/** "Aktarmasız", "1 aktarma", or walking all the way. */
function changesText(journey) {
    if (journey.rides === 0) return 'Yürüyerek';
    return journey.rides === 1 ? 'Aktarmasız' : `${journey.rides - 1} aktarma`;
}

function showTransit(journey, { scroll = true } = {}) {
    $('#home').hidden = true;
    const result = $('#result');
    result.hidden = false;
    result.classList.add('transit');
    $('#summary-time').textContent = formatDuration(journey.durationSeconds);
    $('#summary-distance').textContent = changesText(journey);
    $('#summary-arrival').textContent = `${clockFormat.format(new Date(journey.departAt))} → `
        + clockFormat.format(new Date(journey.arriveAt));
    $('#summary-traffic').hidden = true;
    const choice = $('#summary-choice');
    choice.hidden = state.routes.length < 2;
    choice.className = `tag ${journey === state.routes[0] ? 'clear' : 'alternative'}`;
    choice.textContent = journey === state.routes[0] ? 'Önerilen' : 'Diğer seçenek';
    $('#summary-engine').textContent = 'Toplu taşıma · İBB tarifeleri';
    $('#transit-legs').replaceChildren(...journey.legs.flatMap((leg, index) => [
        index > 0 ? el('span', { class: 'leg-arrow', 'aria-hidden': 'true' }, '›') : null,
        leg.kind === 'WALK'
            ? el('span', { class: 'leg-chip walk', title: `Yürüyüş, ${formatDistance(leg.distanceMeters)}` },
                icon('foot'), formatDuration(leg.durationSeconds))
            : el('span', { class: 'leg-chip', style: `background:${TRANSIT_STYLE[leg.kind]?.color}`,
                title: `${legName(leg)} · ${leg.lineName ?? ''}` },
            icon(TRANSIT_STYLE[leg.kind]?.icon ?? 'bus'), leg.line ?? leg.label)
    ]).filter(Boolean));
    showOtherJourney(journey);
    $('#route-warnings').replaceChildren();
    $('#steps-count').textContent = `${journey.legs.length} adım`;
    $('#steps').replaceChildren(...journey.legs.map((leg) => transitStep(leg)), el('li', {},
        el('span', { class: 'step-icon' }, icon('arrive')),
        el('span', {}, el('span', { class: 'step-text' }, 'Varış')),
        el('span', { class: 'step-distance' }, clockFormat.format(new Date(journey.arriveAt)))));
    $('#route-stats').textContent = `Tarifeler: ${journey.source}. Saatler haftalık düzene göredir; gerçek kalkışlar `
        + 'birkaç dakika farklı olabilir.';
    if (scroll && phoneLayout.matches) {
        const panel = $('#panel');
        panel.scrollTo({ top: result.offsetTop - 12, behavior: reducedMotion.matches ? 'auto' : 'smooth' });
    }
    searchAlong();
}

/** A walk or a ride as a step of the directions; a tap shows where it starts. */
function transitStep(leg) {
    const walk = leg.kind === 'WALK';
    const text = walk
        ? (leg.to ? `${leg.to} durağına yürüyün` : 'Varış noktasına yürüyün')
        : `${clockFormat.format(new Date(leg.departAt))} ${legName(leg)}`
            + (leg.headsign ? ` · ${leg.headsign} yönü` : '');
    const detail = walk
        ? formatDistance(leg.distanceMeters)
        : `${leg.from} → ${leg.to} · ${leg.stops} durak` + (leg.lineName ? ` · ${leg.lineName}` : '');
    const start = { lat: leg.geometry[0][0], lon: leg.geometry[0][1] };
    return el('li', {
        tabindex: '0',
        onclick: () => focusStep(start),
        onkeydown: (event) => {
            if (event.key === 'Enter') focusStep(start);
        }
    },
    el('span', { class: 'step-icon', style: walk ? null : `background:${TRANSIT_STYLE[leg.kind]?.color};color:#fff` },
        icon(TRANSIT_STYLE[leg.kind]?.icon ?? 'bus')),
    el('span', {}, el('span', { class: 'step-text' }, text), el('span', { class: 'step-street' }, detail)),
    el('span', { class: 'step-distance' }, formatDuration(leg.durationSeconds)));
}

/** The next of the offered journeys, one tap away: faster with more changes, or with fewer changes. */
function showOtherJourney(journey) {
    const button = $('#route-switch');
    const index = state.routes.indexOf(journey);
    const other = state.routes.length > 1 ? state.routes[(index + 1) % state.routes.length] : null;
    button.hidden = !other;
    if (!other) return;
    const delta = other.arriveAt && journey.arriveAt ? (Date.parse(other.arriveAt) - Date.parse(journey.arriveAt)) / 1000
        : 0;
    const compared = Math.abs(delta) < 60 ? 'aynı saatte varır'
        : `${formatDuration(Math.abs(delta))} ${delta > 0 ? 'sonra' : 'önce'} varır`;
    $('#switch-title').textContent = `${changesText(other)} · ${formatDuration(other.durationSeconds)}`;
    $('#switch-detail').textContent = `${other.legs.filter((leg) => leg.kind !== 'WALK').map(legName).join(' › ')
        || 'Yürüyüş'} · ${compared}`;
    button.onclick = () => {
        state.route = other;
        drawTransit(other, { fit: true });
        showTransit(other, { scroll: false });
    };
}

/* ---------- places along the route ---------- */

let alongRequest = 0;

/** The chips for what can be looked for, those that suit the route's way of travelling. */
function renderAlongChips(route) {
    $('#along-chips').replaceChildren(...Object.entries(ALONG_NEEDS)
        .filter(([, need]) => need.modes.includes(route.mode))
        .map(([key, need]) => el('button', {
            class: 'quick-chip along-chip',
            type: 'button',
            'aria-pressed': String(state.alongNeed === key),
            onclick: () => {
                state.alongNeed = state.alongNeed === key ? null : key;
                searchAlong();
            }
        }, el('span', { class: 'chip-icon', style: `background:${need.color}` }, icon(need.icon)), need.label)));
}

/**
 * Looks for the kind of place whose chip is on along the chosen route, or clears the search when no chip is on or
 * there is no route. The route goes along as an encoded polyline, simplified to fit in a link.
 */
async function searchAlong() {
    const route = state.nav ? null : state.route;
    if (route && state.alongNeed && !ALONG_NEEDS[state.alongNeed].modes.includes(route.mode)) state.alongNeed = null;
    const need = route ? state.alongNeed : null;
    const requestId = ++alongRequest;
    layers.along?.clearLayers();
    const list = $('#along-list');
    list.hidden = true;
    list.replaceChildren();
    $('#along-status').textContent = '';
    if (route) renderAlongChips(route);
    if (!need) return;
    $('#along-status').textContent = 'Aranıyor…';
    try {
        const line = encodePolyline(simplifyLine(route.geometry, ALONG_MAX_POINTS));
        const places = await api.get(`places/along?need=${need}&mode=${route.mode}`
            + `&line=${encodeURIComponent(line)}`);
        if (requestId !== alongRequest) return;
        showAlong(places, need, route);
    } catch (error) {
        if (requestId !== alongRequest) return;
        $('#along-status').textContent = '';
        toast(error.message, true);
    }
}

function showAlong(places, need, route) {
    const style = ALONG_NEEDS[need];
    const list = $('#along-list');
    list.hidden = false;
    $('#along-status').textContent = places.length ? `${places.length} yer` : '';
    if (!places.length) {
        list.replaceChildren(el('li', { class: 'along-empty' }, `Rotanın yakınında ${style.noun} bulunamadı`));
        return;
    }
    const markers = places.map((place) => L.marker([place.lat, place.lon], {
        title: place.name,
        keyboard: false,
        icon: L.divIcon({
            className: '',
            html: el('div', { class: 'marker-business marker-along', style: `background:${style.color}` },
                icon(style.icon)),
            iconSize: [28, 28],
            iconAnchor: [14, 14]
        })
    }).bindPopup(() => alongPopup(place, route)).addTo(layers.along));
    const full = state.stops.length >= MAX_STOPS;
    list.replaceChildren(...places.map((place, index) => el('li', {
        tabindex: '0',
        role: 'button',
        onclick: () => focusAlong(markers[index]),
        onkeydown: (event) => {
            if (event.key === 'Enter' && event.target === event.currentTarget) focusAlong(markers[index]);
        }
    },
    el('span', { class: 'place-icon', style: `background:${style.color}` }, icon(style.icon)),
    el('span', { class: 'place-text' }, el('strong', {}, place.name),
        el('span', {}, alongDescription(place, route.mode))),
    el('button', {
        class: 'button small along-add',
        type: 'button',
        disabled: full,
        title: full ? `En fazla ${MAX_STOPS} ara durak eklenebilir` : 'Rotaya ara durak olarak ekle',
        'aria-label': `${place.name}: ara durak olarak ekle`,
        onclick: (event) => {
            event.stopPropagation();
            stopOnTheWay(place);
        }
    }, icon('plus'), 'Durak'))));
}

/** "2,4 km sonra · sağda, yoldan 40 m · Bağdat Caddesi"; which side only matters to drivers. */
function alongDescription(place, mode) {
    const side = mode === 'car' ? (place.side === 'RIGHT' ? 'sağda' : 'solda') : null;
    const off = place.offsetMeters >= 30 ? `yoldan ${formatDistance(place.offsetMeters)}` : null;
    return [place.alongMeters < 50 ? 'başlangıçta' : `${formatDistance(place.alongMeters)} sonra`,
        [side, off].filter(Boolean).join(', '), place.detail].filter(Boolean).join(' · ');
}

function alongPopup(place, route) {
    const full = state.stops.length >= MAX_STOPS;
    return el('div', { class: 'popup' },
        el('div', { class: 'popup-title' }, place.name),
        el('div', { class: 'muted small' }, place.kind),
        el('div', { class: 'small' }, alongDescription(place, route.mode)),
        el('div', { class: 'popup-actions' },
            el('button', {
                class: 'button small primary',
                type: 'button',
                disabled: full,
                title: full ? `En fazla ${MAX_STOPS} ara durak eklenebilir` : null,
                onclick: () => {
                    map.closePopup();
                    stopOnTheWay(place);
                }
            }, 'Durak ekle'),
            el('button', { class: 'button small', type: 'button', onclick: () => {
                map.closePopup();
                setStop('to', { lat: place.lat, lon: place.lon, label: place.name });
            } }, 'Buraya git')));
}

function focusAlong(marker) {
    map.setView(marker.getLatLng(), Math.max(map.getZoom(), 16));
    marker.openPopup();
}

/** Adds a place found along the route as a stop, among the other stops in the order the route reaches them. */
function stopOnTheWay(place) {
    if (state.stops.length >= MAX_STOPS) {
        toast(`En fazla ${MAX_STOPS} ara durak eklenebilir`, true);
        return;
    }
    state.stops.splice(stopIndexAt(place.alongMeters), 0, { lat: place.lat, lon: place.lon, label: place.name });
    state.alongNeed = null;
    renderStopFields();
    drawStops();
    planRoute();
    toast(`${place.name} ara durak olarak eklendi`);
}

/** Where a stop reached this far into the route goes in the list: after the stops the route reaches before it. */
function stopIndexAt(alongMeters) {
    const route = state.route;
    const points = route.geometry.map(([lat, lon]) => ({ lat, lon }));
    let passed = 0;
    let meters = 0;
    let point = 0;
    for (const step of route.instructions) {
        if (step.maneuver !== 'WAYPOINT') continue;
        for (; point < step.pointIndex; point++) meters += distanceMeters(points[point], points[point + 1]);
        if (meters < alongMeters) passed++;
    }
    // after the passed-th stop that has a place; empty stop fields keep their places
    let index = 0;
    while (passed > 0 && index < state.stops.length) {
        if (state.stops[index]) passed--;
        index++;
    }
    return index;
}

/** The line with as few points as keep it within a tolerance, which doubles until at most maxPoints are left. */
function simplifyLine(line, maxPoints) {
    if (line.length <= maxPoints) return line;
    const points = line.map(([lat, lon]) => ({ lat, lon }));
    for (let tolerance = 5; ; tolerance *= 2) {
        const kept = douglasPeucker(points, tolerance);
        if (kept.length <= maxPoints) return kept.map((index) => line[index]);
    }
}

/** Douglas–Peucker: the indexes of the points that keep the line within the tolerance (m). */
function douglasPeucker(points, tolerance) {
    const keep = new Uint8Array(points.length);
    keep[0] = 1;
    keep[points.length - 1] = 1;
    const pending = [[0, points.length - 1]];
    while (pending.length) {
        const [first, last] = pending.pop();
        let farthest = -1;
        let farthestMeters = tolerance;
        for (let i = first + 1; i < last; i++) {
            const meters = project(points[i], points[first], points[last]).distance;
            if (meters > farthestMeters) {
                farthest = i;
                farthestMeters = meters;
            }
        }
        if (farthest > 0) {
            keep[farthest] = 1;
            pending.push([first, farthest], [farthest, last]);
        }
    }
    const kept = [];
    keep.forEach((flag, index) => {
        if (flag) kept.push(index);
    });
    return kept;
}

/** Google's encoded polyline with five decimals: a few characters per point. */
function encodePolyline(line) {
    let text = '';
    let lastLat = 0;
    let lastLon = 0;
    for (const [lat, lon] of line) {
        const y = Math.round(lat * 1e5);
        const x = Math.round(lon * 1e5);
        text += encodeNumber(y - lastLat) + encodeNumber(x - lastLon);
        lastLat = y;
        lastLon = x;
    }
    return text;
}

function encodeNumber(value) {
    let rest = value < 0 ? ~(value << 1) : value << 1;
    let text = '';
    while (rest >= 0x20) {
        text += String.fromCharCode((0x20 | (rest & 0x1f)) + 63);
        rest >>= 5;
    }
    return text + String.fromCharCode(rest + 63);
}

/* ---------- businesses ---------- */

function businessesOn() {
    return stored(BUSINESS_KEY) !== 'off';
}

let businessRequest = 0;

async function refreshBusinesses() {
    const button = $('#business-toggle');
    const center = map.getCenter();
    const inArea = insideArea({ lat: center.lat, lon: center.lng }, 2000);
    const close = map.getZoom() >= BUSINESS_MIN_ZOOM;
    button.title = !inArea ? `İşletmeler haritada yalnızca ${state.area?.name ?? 'ayrıntılı bölgede'} gösterilir; `
        + 'Türkiye genelinde işletmeleri arama kutusundan bulabilirsiniz'
        : close ? 'İşletmeleri gizle/göster' : 'İşletmeleri görmek için yakınlaştırın';
    button.classList.toggle('waiting', businessesOn() && (!inArea || !close));
    const requestId = ++businessRequest;
    if (!businessesOn() || state.nav || !inArea || !close) {
        layers.businesses.clearLayers();
        return;
    }
    // ask for at most the largest view the server serves, around the centre
    const view = map.getBounds();
    const minLat = Math.max(view.getSouth(), center.lat - BUSINESS_MAX_SPAN.lat / 2);
    const maxLat = Math.min(view.getNorth(), center.lat + BUSINESS_MAX_SPAN.lat / 2);
    const minLon = Math.max(view.getWest(), center.lng - BUSINESS_MAX_SPAN.lon / 2);
    const maxLon = Math.min(view.getEast(), center.lng + BUSINESS_MAX_SPAN.lon / 2);
    try {
        const businesses = await api.get(`businesses?minLat=${minLat.toFixed(5)}&minLon=${minLon.toFixed(5)}`
            + `&maxLat=${maxLat.toFixed(5)}&maxLon=${maxLon.toFixed(5)}`);
        if (requestId === businessRequest) drawBusinesses(businesses);
    } catch (error) {
        if (requestId === businessRequest) toast(error.message, true);
    }
}

function drawBusinesses(businesses) {
    layers.businesses.clearLayers();
    for (const business of businesses) {
        const [color, iconName] = BUSINESS_STYLE[business.category] ?? ['#64748b', 'store'];
        L.marker([business.lat, business.lon], {
            title: business.name,
            keyboard: false,
            icon: L.divIcon({
                className: '',
                html: el('div', { class: 'marker-business', style: `background:${color}` }, icon(iconName)),
                iconSize: [22, 22],
                iconAnchor: [11, 11]
            })
        }).bindPopup(() => placePopup(business)).addTo(layers.businesses);
    }
}

/** A business (or search result) with its details and the two route buttons. */
function placePopup(place) {
    const point = { lat: place.lat, lon: place.lon, label: place.name };
    const website = /^https?:\/\//i.test(place.website ?? '') ? place.website : null;
    return el('div', { class: 'popup' },
        el('div', { class: 'popup-title' }, place.name),
        el('div', { class: 'muted small' }, place.kind ?? place.categoryLabel),
        place.detail ? el('div', { class: 'small' }, place.detail) : null,
        place.openingHours ? el('div', { class: 'small' }, `Çalışma saatleri: ${openingHoursInTurkish(place.openingHours)}`)
            : null,
        place.phone ? el('div', { class: 'small' }, 'Telefon: ',
            el('a', { href: `tel:${place.phone.replace(/[^+0-9]/g, '')}` }, place.phone)) : null,
        website ? el('div', { class: 'small' },
            el('a', { href: website, target: '_blank', rel: 'noopener noreferrer' }, new URL(website).hostname)) : null,
        el('div', { class: 'popup-actions' },
            el('button', { class: 'button small', type: 'button', onclick: () => {
                map.closePopup();
                setStop('from', { ...point });
            } }, 'Buradan başla'),
            el('button', { class: 'button small primary', type: 'button', onclick: () => {
                map.closePopup();
                setStop('to', { ...point });
            } }, 'Buraya git')));
}

/** "Mo-Fr 09:00-19:00; Su off" becomes "Pzt-Cum 09:00-19:00; Paz kapalı". */
function openingHoursInTurkish(hours) {
    return hours.replace(/\b(Mo|Tu|We|Th|Fr|Sa|Su|PH)\b/g, (day) => OPENING_DAYS[day])
        .replace(/\boff\b/g, 'kapalı')
        .replace('24/7', '7/24 açık');
}

function toggleBusinesses() {
    const on = !businessesOn();
    store(BUSINESS_KEY, on ? 'on' : 'off');
    $('#business-toggle').setAttribute('aria-pressed', String(on));
    refreshBusinesses();
}

/* ---------- traffic ---------- */

function trafficOn() {
    return stored(TRAFFIC_KEY) === 'on';
}

let trafficRequest = 0;

async function refreshTraffic() {
    const button = $('#traffic-toggle');
    const close = map.getZoom() >= TRAFFIC_MIN_ZOOM;
    $('#traffic-legend').hidden = !trafficOn();
    button.classList.toggle('waiting', trafficOn() && !close);
    const requestId = ++trafficRequest;
    if (!trafficOn() || state.nav || !close) {
        if (!state.nav) layers.traffic.clearLayers();
        $('#traffic-note').textContent = close ? 'İBB trafik verisi · İstanbul' : 'Trafiği görmek için yakınlaştırın';
        return;
    }
    const center = map.getCenter();
    const view = map.getBounds();
    const minLat = Math.max(view.getSouth(), center.lat - TRAFFIC_MAX_SPAN.lat / 2);
    const maxLat = Math.min(view.getNorth(), center.lat + TRAFFIC_MAX_SPAN.lat / 2);
    const minLon = Math.max(view.getWest(), center.lng - TRAFFIC_MAX_SPAN.lon / 2);
    const maxLon = Math.min(view.getEast(), center.lng + TRAFFIC_MAX_SPAN.lon / 2);
    try {
        const roads = await api.get(`traffic/roads?minLat=${minLat.toFixed(5)}&minLon=${minLon.toFixed(5)}`
            + `&maxLat=${maxLat.toFixed(5)}&maxLon=${maxLon.toFixed(5)}`);
        if (requestId !== trafficRequest) return;
        drawTraffic(roads);
        $('#traffic-note').textContent = roads.length ? 'İBB verisi: bu saatin olağan trafiği ve canlı endeks'
            : 'Burada trafik verisi yok (yalnızca İstanbul)';
    } catch (error) {
        if (requestId === trafficRequest) toast(error.message, true);
    }
}

function drawTraffic(roads) {
    layers.traffic.clearLayers();
    const weight = map.getZoom() >= 15 ? 6 : map.getZoom() >= 13 ? 4 : 3;
    for (const road of roads) {
        const color = TRAFFIC_COLORS.find(([from]) => road.factor >= from)[1];
        L.polyline(road.points, { pane: 'traffic', color, weight, opacity: 0.85, interactive: false })
            .addTo(layers.traffic);
    }
}

function toggleTraffic() {
    const on = !trafficOn();
    store(TRAFFIC_KEY, on ? 'on' : 'off');
    $('#traffic-toggle').setAttribute('aria-pressed', String(on));
    if (on && map.getZoom() < TRAFFIC_MIN_ZOOM) toast('Trafiği görmek için haritayı biraz yakınlaştırın');
    refreshTraffic();
}

/** The live Istanbul traffic index next to the chip; the coloured roads follow it. */
async function loadTrafficStatus() {
    try {
        const status = await api.get('traffic');
        const badge = $('#traffic-level');
        badge.hidden = !status.level;
        if (!status.level) {
            $('#traffic-toggle').title = 'İstanbul için olağan trafik (İBB verisi); canlı endekse şu an ulaşılamıyor';
            return;
        }
        badge.textContent = status.level;
        badge.className = `traffic-level ${TRAFFIC_LEVELS[status.level] ?? ''}`;
        $('#traffic-toggle').title = `İstanbul trafik endeksi ${status.index}`
            + (status.typical === null ? '' : ` (bu saatte genelde ${status.typical})`)
            + (status.comparison ? `: ${status.comparison}` : '') + '. Kaynak: İBB Açık Veri Portalı';
    } catch {
        // the badge is extra information; the map works without it
    }
}

/* ---------- incidents ---------- */

async function loadIncidents() {
    try {
        state.incidents = await api.get('incidents');
        drawIncidents();
    } catch (error) {
        toast(error.message, true);
    }
}

function describeEnd(incident) {
    return incident.endsAt ? `${dayFormat.format(new Date(incident.endsAt))} tarihine kadar` : 'Kaldırılana kadar';
}

function incidentPopup(incident) {
    return el('div', { class: 'popup' },
        el('div', { class: 'popup-title' }, incident.typeLabel),
        incident.street ? el('div', {}, incident.street) : null,
        incident.description ? el('div', { class: 'muted small' }, incident.description) : null,
        el('div', { class: 'muted small' }, `Bildirildi ${dayFormat.format(new Date(incident.reportedAt))}`),
        el('div', { class: 'muted small' }, describeEnd(incident)),
        el('div', { class: 'popup-actions' },
            el('button', { class: 'button small ghost', type: 'button', onclick: () => resolveIncident(incident) },
                'Yol açıldı, kaldır')));
}

function drawIncidents() {
    layers.incidents.clearLayers();
    for (const incident of state.incidents) {
        const color = INCIDENT_COLORS[incident.type] ?? '#dc2626';
        for (const line of incident.lines) {
            L.polyline(line, {
                color,
                weight: 7,
                opacity: 0.8,
                lineCap: 'round',
                dashArray: incident.type === 'CLOSURE' ? '1 12' : null
            }).bindPopup(() => incidentPopup(incident)).addTo(layers.incidents);
        }
        const html = el('div', { class: 'marker-incident', style: `background:${color}` }, icon('warning'));
        incident.marker = L.marker([incident.lat, incident.lon], {
            title: incident.typeLabel,
            icon: L.divIcon({ className: '', html, iconSize: [26, 26], iconAnchor: [13, 13] })
        }).bindPopup(() => incidentPopup(incident)).addTo(layers.incidents);
    }
    $('#incident-list').replaceChildren(...(state.incidents.length
        ? state.incidents.map((incident) => el('li', {
            tabindex: '0',
            onclick: () => {
                map.setView([incident.lat, incident.lon], 17);
                incident.marker.openPopup();
            }
        },
        el('span', { class: 'swatch', style: `background:${INCIDENT_COLORS[incident.type]}` }),
        el('div', {}, el('strong', {}, incident.typeLabel), incident.street ? ` · ${incident.street}` : '',
            el('div', { class: 'muted small' }, describeEnd(incident)))))
        : [el('li', { class: 'empty' }, 'Şu an bildirilmiş bir olay yok.')]));
}

function startPicking() {
    state.picking = true;
    document.body.classList.add('picking');
    $('#report-button').replaceChildren(icon('close'), 'Vazgeç');
    toast('Olayın olduğu yola haritada dokunun');
}

function stopPicking() {
    state.picking = false;
    document.body.classList.remove('picking');
    $('#report-button').replaceChildren(icon('warning'), 'Olay bildir');
}

function openIncidentDialog(point) {
    const dialog = $('#incident-dialog');
    dialog.point = point;
    $('#incident-where').textContent = 'Konum aranıyor…';
    streetName(point).then((name) => {
        $('#incident-where').textContent = name ? `Yol: ${name}` : 'İsimsiz bir yol';
    });
    $('#incident-form').reset();
    // closing with Escape keeps the previous returnValue, so start from a clean one
    dialog.returnValue = '';
    dialog.showModal();
}

async function submitIncident() {
    const dialog = $('#incident-dialog');
    const form = $('#incident-form');
    const point = dialog.point;
    const duration = form.elements.duration.value;
    try {
        const incident = await api.post('incidents', {
            type: form.elements.type.value,
            description: form.elements.description.value.trim() || null,
            lat: point.lat,
            lon: point.lon,
            durationMinutes: duration ? Number(duration) : null
        });
        toast(`${incident.typeLabel} bildirildi; rotalar artık bunu hesaba katıyor`);
        await refreshAfterIncidentChange();
    } catch (error) {
        toast(error.message, true);
    }
}

async function resolveIncident(incident) {
    map.closePopup();
    try {
        await api.post(`incidents/${incident.id}/resolve`);
        toast(`${incident.typeLabel} kaldırıldı`);
        await refreshAfterIncidentChange();
    } catch (error) {
        toast(error.message, true);
    }
}

async function refreshAfterIncidentChange() {
    await loadIncidents();
    if (state.route && !state.nav) await planRoute();
}

/* ---------- sharing the arrival time ---------- */

/**
 * Shares where the trip goes and when it arrives, with a link that opens the destination in the app. The link names
 * only the destination: the traveller's own position is not shared.
 */
async function shareArrival() {
    const route = state.route;
    if (!route || !state.to) return;
    const nav = state.nav;
    const seconds = nav?.remainingSeconds ?? route.durationSeconds;
    const start = nav ? Date.now() : (route.departAt ? Date.parse(route.departAt) : Date.now());
    const arrival = clockFormat.format(new Date(start + seconds * 1000));
    const place = state.to.label ?? 'hedef';
    const text = nav
        ? `Yoldayım: ${place}. Tahmini varış ${arrival} (${formatDuration(seconds)} kaldı).`
        : `${place}: tahmini varış ${arrival} (${TRAVEL_BY[route.mode]} ${formatDuration(seconds)}).`;
    const url = `${location.origin}${location.pathname}#to=${coordinate(state.to)}&mode=${route.mode}`;
    if (navigator.share) {
        try {
            await navigator.share({ title: 'ozecmaps', text, url });
            return;
        } catch (error) {
            // closing the share sheet is no failure; anything else falls back to copying
            if (error.name === 'AbortError') return;
        }
    }
    try {
        await navigator.clipboard.writeText(`${text} ${url}`);
        toast('Varış bilgisi kopyalandı; mesajınıza yapıştırın');
    } catch {
        window.prompt('Varış bilgisini kopyalayın:', `${text} ${url}`);
    }
}

/* ---------- turn-by-turn navigation ---------- */

function prepareTrack(route) {
    const points = route.geometry.map(([lat, lon]) => ({ lat, lon }));
    const along = [0];
    for (let i = 1; i < points.length; i++) along.push(along[i - 1] + distanceMeters(points[i - 1], points[i]));
    const steps = route.instructions.map((step) => ({ ...step, along: along[step.pointIndex] }));
    // how far each step lies from the one before: what its first announcement says when that is closer than 500 m
    steps.forEach((step, i) => {
        step.gap = i === 0 ? 0 : step.along - steps[i - 1].along;
    });
    return { route, points, along, total: along.at(-1), steps, piece: 0 };
}

/** The nearest point of the route, preferring the stretch just ahead of the last match. */
function matchOnTrack(track, position) {
    let best = null;
    const scan = (first, last) => {
        for (let i = first; i < last; i++) {
            const { t, distance } = project(position, track.points[i], track.points[i + 1]);
            if (!best || distance < best.distance) {
                best = { piece: i, distance, along: track.along[i] + t * (track.along[i + 1] - track.along[i]) };
            }
        }
    };
    scan(Math.max(0, track.piece - 2), Math.min(track.points.length - 1, track.piece + 80));
    if (!best || best.distance > 60) scan(0, track.points.length - 1);
    track.piece = best.piece;
    return best;
}

function pointAlongTrack(track, distance) {
    const target = Math.min(Math.max(0, distance), track.total);
    let low = 0;
    let high = track.along.length - 1;
    while (high - low > 1) {
        const middle = (low + high) >> 1;
        if (track.along[middle] <= target) low = middle;
        else high = middle;
    }
    const a = track.points[low];
    const b = track.points[high];
    const span = track.along[high] - track.along[low];
    const t = span > 0 ? (target - track.along[low]) / span : 0;
    return { lat: a.lat + (b.lat - a.lat) * t, lon: a.lon + (b.lon - a.lon) * t, heading: bearing(a, b) };
}

function startNavigation() {
    if (!state.route || state.route.mode === 'transit') return;
    const nav = state.nav = {
        /** Whether the map follows the arrow; dragging the map stops it, "Konumuma odaklan" starts it again. */
        follow: true,
        position: null,
        track: prepareTrack(state.route),
        mode: state.route.mode,
        voice: stored(VOICE_KEY) !== 'off',
        spoken: new Set(),
        offRoute: 0,
        rerouting: false,
        watchId: null,
        simulation: null,
        source: 'waiting',
        userMarker: null,
        wakeLock: null
    };
    document.body.classList.add('navigating');
    layers.businesses.clearLayers();
    // the places along the route were for planning stops; the trip itself needs a clear map
    state.alongNeed = null;
    searchAlong();
    $('#nav-banner').hidden = false;
    $('#nav-bar').hidden = false;
    $('#route-switch').hidden = true;
    // only the chosen route is driven; the part already driven turns grey under the arrow
    nav.doneLine = L.polyline([], { color: '#9ca3af', weight: 7, lineCap: 'round', lineJoin: 'round', interactive: false });
    drawRoute(state.route, { fit: false });
    updateVoiceButton();
    map.closePopup();
    navigator.wakeLock?.request('screen').then((lock) => {
        nav.wakeLock = lock;
    }).catch(() => {
        // the screen may dim; navigation still works
    });

    const first = nav.track.steps[0];
    // spoken inside the tap, which mobile browsers require before they allow any speech
    speak(`Navigasyon başladı. ${first.text}${first.street ? `, ${first.street}` : ''}`, { urgent: true });
    updateNavigation(nav, pointAlongTrack(nav.track, 0), 0);

    setNavSource('waiting');
    const problem = locationProblem();
    if (problem) {
        toast(`${problem} Rota şimdi simülasyonla sürülüyor.`, false, 9000);
        startSimulation();
    } else {
        let firstFix = true;
        nav.watchId = navigator.geolocation.watchPosition((position) => {
            const fix = {
                lat: position.coords.latitude,
                lon: position.coords.longitude,
                heading: position.coords.heading,
                speed: position.coords.speed
            };
            if (firstFix) {
                firstFix = false;
                const away = matchOnTrack(nav.track, fix).distance;
                if (away > 200) {
                    nav.track.piece = 0;
                    toast(`Gerçek konumunuz rotadan ${formatDistance(away)} uzakta, bu yüzden rota simülasyonla `
                        + 'sürülüyor. Gerçek konumla gitmek için başlangıcı "Konumum" yapın.', false, 10000);
                    startSimulation();
                    return;
                }
                setNavSource('gps');
            }
            onPosition(fix);
        }, (error) => {
            toast(`${LOCATION_ERRORS[error.code] ?? 'Konum alınamadı.'} Rota simülasyonla sürülüyor.`, true, 9000);
            startSimulation();
        }, { enableHighAccuracy: true, maximumAge: 1000, timeout: 20000 });
    }
}

const NAV_SOURCES = {
    waiting: 'Konum bekleniyor',
    gps: 'Canlı konum',
    simulation: 'Simülasyon',
    paused: 'Simülasyon duraklatıldı'
};

/** Shows whether the arrow is the real position or a simulated drive. */
function setNavSource(source) {
    const nav = state.nav;
    if (!nav) return;
    nav.source = source === 'paused' ? 'simulation' : source;
    const badge = $('#nav-source');
    badge.textContent = NAV_SOURCES[source];
    badge.className = `nav-source ${source}`;
    refreshHereVisibility();
}

function stopNavigation({ keepSpeaking = false } = {}) {
    const nav = state.nav;
    if (!nav) return;
    if (nav.watchId !== null) navigator.geolocation.clearWatch(nav.watchId);
    clearInterval(nav.simulation);
    nav.userMarker?.remove();
    nav.doneLine?.remove();
    $('#nav-speed').hidden = true;
    nav.wakeLock?.release().catch(() => {});
    if (!keepSpeaking) silence();
    state.nav = null;
    refreshHereVisibility();
    document.body.classList.remove('navigating');
    $('#nav-banner').hidden = true;
    $('#nav-bar').hidden = true;
    $('#nav-recenter').hidden = true;
    if (state.route) {
        drawRoute(state.route, { fit: true });
        showOtherRoute(state.route);
    }
}

function startSimulation() {
    const nav = state.nav;
    if (!nav || nav.simulation) return;
    if (nav.watchId !== null) {
        navigator.geolocation.clearWatch(nav.watchId);
        nav.watchId = null;
    }
    const route = nav.track.route;
    const metersPerTick = (route.distanceMeters / Math.max(1, route.durationSeconds)) * SIMULATION_SPEEDUP[nav.mode] * 0.5;
    let along = nav.lastAlong ?? 0;
    // the drive is faster than real time; the speed shown is the route's own average
    const shownSpeed = route.distanceMeters / Math.max(1, route.durationSeconds);
    nav.simulation = setInterval(() => {
        along += metersPerTick;
        onPosition({ ...pointAlongTrack(nav.track, along), speed: shownSpeed });
    }, 500);
    $('#nav-simulate').replaceChildren(icon('pause'), el('span', { class: 'label' }, 'Duraklat'));
    setNavSource('simulation');
}

function pauseSimulation() {
    const nav = state.nav;
    clearInterval(nav.simulation);
    nav.simulation = null;
    $('#nav-simulate').replaceChildren(icon('play'), el('span', { class: 'label' }, 'Simülasyon'));
    setNavSource('paused');
}

function onPosition(position) {
    const nav = state.nav;
    if (!nav) return;
    const match = matchOnTrack(nav.track, position);
    if (match.distance > OFF_ROUTE_METERS[nav.mode]) {
        nav.offRoute++;
        if (nav.offRoute >= 3 && !nav.rerouting) reroute(position);
    } else {
        nav.offRoute = 0;
    }
    nav.lastAlong = match.along;
    const heading = position.heading ?? pointAlongTrack(nav.track, match.along + 5).heading;
    updateNavigation(nav, { ...position, heading }, match.along);
}

function updateNavigation(nav, position, along) {
    const { track } = nav;
    const remaining = Math.max(0, track.total - along);
    if (remaining <= ARRIVAL_METERS[nav.mode] && along > 0) {
        arrive();
        return;
    }
    const nextIndex = track.steps.findIndex((step) => step.along > along + 2);
    const next = track.steps[nextIndex] ?? track.steps.at(-1);
    const toNext = Math.max(0, next.along - along);

    $('#nav-icon').replaceChildren(icon(MANEUVER_ICONS[next.maneuver] ?? 'straight'));
    $('#nav-distance').textContent = formatDistance(toNext);
    $('#nav-instruction').textContent = next.text;
    $('#nav-street').textContent = next.street ?? '';
    // the manoeuvre after this one, when it follows closely
    const then = nextIndex >= 0 ? track.steps[nextIndex + 1] : null;
    const thenSoon = then && then.along - next.along < 400;
    $('#nav-then').hidden = !thenSoon;
    if (thenSoon) {
        $('#nav-then-icon').replaceChildren(icon(MANEUVER_ICONS[then.maneuver] ?? 'straight'));
        $('#nav-then-text').textContent = then.maneuver === 'ARRIVE' ? 'Hedefe varış'
            : `${then.text}${then.street ? ` · ${then.street}` : ''}`;
    }
    const seconds = track.route.durationSeconds * (remaining / Math.max(1, track.total));
    nav.remainingSeconds = seconds;
    $('#nav-time').textContent = formatDuration(seconds);
    $('#nav-meta').textContent = `${formatDistance(remaining)} · varış ${arrivalTime(seconds)}`;
    $('#nav-progress').style.width = `${Math.min(100, (along / Math.max(1, track.total)) * 100).toFixed(1)}%`;
    if (Number.isFinite(position.speed) && position.speed >= 0) {
        $('#nav-speed').hidden = false;
        $('#nav-speed-value').textContent = String(Math.round(position.speed * 3.6));
    }
    nav.doneLine?.setLatLngs([...track.points.slice(0, track.piece + 1).map((point) => [point.lat, point.lon]),
        [position.lat, position.lon]]);
    announce(nav, next, toNext);

    const latLng = [position.lat, position.lon];
    nav.position = { lat: position.lat, lon: position.lon };
    if (!nav.userMarker) {
        const html = el('div', { class: 'marker-user' }, icon('navigate'));
        nav.userMarker = L.marker(latLng, {
            interactive: false,
            keyboard: false,
            zIndexOffset: 1000,
            icon: L.divIcon({ className: '', html, iconSize: [30, 30], iconAnchor: [15, 15] })
        }).addTo(layers.markers);
        nav.userElement = html;
        // no animation: later pans would interrupt a running zoom and leave the map half-scaled
        if (nav.follow) followCamera(nav, along, { zoom: 17, animate: false });
    } else {
        nav.userMarker.setLatLng(latLng);
        if (nav.follow) followCamera(nav, along);
    }
    if (typeof position.heading === 'number' && !Number.isNaN(position.heading)) {
        nav.userElement.classList.add('has-heading');
        nav.userElement.style.setProperty('--heading', `${Math.round(position.heading)}deg`);
    }
}

/**
 * Keeps the arrow in view with the road ahead of it: the map is centred a little further along the route, so the arrow
 * sits behind the middle of the area the instruction banner and the bottom bar leave free, and more of what is coming
 * shows than of what lies behind.
 */
function followCamera(nav, along, { zoom = map.getZoom(), animate = true } = {}) {
    if (!nav.position) return;
    const ahead = pointAlongTrack(nav.track, along + LOOK_AHEAD_METERS[nav.mode]);
    const herePx = map.project([nav.position.lat, nav.position.lon], zoom);
    const direction = map.project([ahead.lat, ahead.lon], zoom).subtract(herePx);
    const length = Math.hypot(direction.x, direction.y);
    const size = map.getSize();
    const box = map.getContainer().getBoundingClientRect();
    const top = Math.max(0, $('#nav-banner').getBoundingClientRect().bottom - box.top);
    const bottom = Math.min(size.y, $('#nav-bar').getBoundingClientRect().top - box.top);
    const shift = length > 1
        ? direction.multiplyBy(LOOK_AHEAD_SHARE * Math.min(size.x, bottom - top) / length) : L.point(0, 0);
    // where the middle of the free area lies, seen from the middle of the map
    const freeMiddle = L.point(0, (top + bottom) / 2 - size.y / 2);
    map.setView(map.unproject(herePx.add(shift).subtract(freeMiddle), zoom), zoom, { animate, duration: 0.4 });
}

/** The traveller moved the map to look around: stop following until "Konumuma odaklan". */
function stopFollowing() {
    const nav = state.nav;
    if (!nav?.follow) return;
    nav.follow = false;
    $('#nav-recenter').hidden = false;
}

function startFollowing() {
    const nav = state.nav;
    if (!nav) return;
    nav.follow = true;
    $('#nav-recenter').hidden = true;
    followCamera(nav, nav.lastAlong ?? 0, { zoom: Math.max(16, map.getZoom()) });
}

function announce(nav, step, distance) {
    if (step.maneuver === 'ARRIVE' && distance <= ARRIVAL_METERS[nav.mode]) return;
    const thresholds = ANNOUNCE_AT[nav.mode];
    for (let level = thresholds.length - 1; level >= 0; level--) {
        if (distance > thresholds[level]) continue;
        const key = `${step.pointIndex}:${level}`;
        if (nav.spoken.has(key)) return;
        for (let earlier = 0; earlier <= level; earlier++) nav.spoken.add(`${step.pointIndex}:${earlier}`);
        // the arrival itself is announced by arrive(); only the early notice is spoken here
        const text = announcement(step, level, nav.mode);
        if (text) speak(text, { urgent: level === thresholds.length - 1 });
        return;
    }
}

/**
 * The sentence for a step at an announcement level. The distance it names is the level's own (500 m, 200 m…), or the
 * whole way from the step before when that is shorter.
 */
function announcement(step, level, mode) {
    const thresholds = ANNOUNCE_AT[mode];
    const ahead = spokenDistance(Math.min(thresholds[level], step.gap));
    if (step.maneuver === 'ARRIVE') return level < thresholds.length - 1 ? `${ahead} sonra hedefinize ulaşacaksınız` : null;
    if (step.maneuver === 'WAYPOINT') {
        return level < thresholds.length - 1 ? `${ahead} sonra ${lowerFirst(step.text).replace('ulaştınız', 'ulaşacaksınız')}`
            : step.text;
    }
    const street = step.street ? `, ${step.street}` : '';
    if (level === thresholds.length - 1) return `Şimdi ${lowerFirst(step.text)}${street}`;
    return `${ahead} sonra ${lowerFirst(step.text)}${street}`;
}

function arrive() {
    speak('Hedefinize ulaştınız', { urgent: true });
    toast('Hedefinize ulaştınız');
    stopNavigation({ keepSpeaking: true });
}

async function reroute(position) {
    const nav = state.nav;
    nav.rerouting = true;
    toast('Rotadan çıktınız, rota yeniden hesaplanıyor');
    speak('Rota yeniden hesaplanıyor', { urgent: true });
    try {
        state.from = { lat: position.lat, lon: position.lon, label: 'Konumum' };
        $('#from-input').value = 'Konumum';
        // stops already reached are done; the new route goes through the others
        const along = nav.lastAlong ?? 0;
        const reached = nav.track.steps.filter((step) => step.maneuver === 'WAYPOINT' && step.along <= along).length;
        state.stops = state.stops.filter(Boolean).slice(reached);
        renderStopFields();
        const route = await api.get(`route?from=${coordinate(state.from)}&to=${coordinate(state.to)}`
            + `&mode=${state.mode}`
            + (state.stops.length ? `&via=${state.stops.map(coordinate).join(';')}` : ''));
        if (state.nav !== nav) return;
        state.routes = [route];
        state.route = route;
        nav.track = prepareTrack(route);
        nav.spoken.clear();
        drawRoute(route, { fit: false });
        drawStops();
    } catch (error) {
        toast(error.message, true);
    } finally {
        nav.rerouting = false;
        nav.offRoute = 0;
    }
}

/* ---------- voice ---------- */

/**
 * Directions are read by the device's own voice through the browser (Web Speech API): free, and as natural as the
 * device allows, since the most natural Turkish voice it offers is chosen (ADR-0011).
 *
 * Sentences go out one at a time from a queue of our own and are never cut off. Chrome on macOS hangs its speech
 * engine when cancel() lands just as a sentence starts, and the hang lasts, across pages, until the browser is
 * restarted. So an urgent sentence ("Şimdi sağa dönün") does not interrupt; it drops the sentences still waiting and
 * goes next, as soon as the current one ends (one to four seconds).
 */
const voice = { turkish: null, queue: [], current: null, watchdog: null, warned: new Set() };
/** A sentence the browser has not started after this long is lost: its speech engine does not answer. */
const SPEECH_START_MS = 4000;
/** Reading out takes about 80 ms a character in Turkish; a sentence still not done well after that is let go. */
const SPEECH_MS_PER_CHARACTER = 110;
const SPEECH_END_SLACK_MS = 3000;

/** The device's Turkish voice, the more natural ones first (Edge's "Natural" voices, Apple's enhanced and premium ones). */
function loadVoices() {
    if (!('speechSynthesis' in window)) return;
    const natural = (candidate) => (/natural|neural|premium|enhanced|gelişmiş|online/i.test(candidate.name) ? 1 : 0);
    voice.turkish = window.speechSynthesis.getVoices()
        .filter((candidate) => candidate.lang?.toLowerCase().replace('_', '-').startsWith('tr'))
        .sort((a, b) => natural(b) - natural(a))[0] ?? null;
}

/** Speaks during navigation, if the voice is switched on. */
function speak(text, { urgent = false } = {}) {
    if (state.nav?.voice) say(text, urgent);
}

/**
 * Queues a sentence. An urgent one replaces everything still waiting; a normal one replaces waiting normal ones,
 * whose distances would no longer be true by the time they were read out.
 */
function say(text, urgent = false) {
    voice.queue = urgent ? [] : voice.queue.filter((waiting) => waiting.urgent);
    voice.queue.push({ text, urgent });
    sayNext();
}

/** Stops what has not been said yet; the sentence being read out finishes (it may not be cut off, see above). */
function silence() {
    voice.queue = [];
}

function sayNext() {
    if (voice.current || !voice.queue.length) return;
    const item = voice.queue.shift();
    voice.current = item;
    item.finish = () => {
        if (voice.current !== item) return;
        clearTimeout(voice.watchdog);
        voice.current = null;
        sayNext();
    };
    speakNow(item);
}

function speakNow(item) {
    if (!('speechSynthesis' in window)) {
        warnAboutVoice('unsupported', 'Bu tarayıcı sesli okumayı desteklemiyor.');
        item.finish();
        return;
    }
    const synth = window.speechSynthesis;
    if (!voice.turkish) loadVoices();
    if (!voice.turkish) {
        warnAboutVoice('turkish', 'Bu tarayıcıda Türkçe ses bulunamadı; yönlendirme varsayılan sesle okunuyor. Mac\'te '
            + 'Sistem Ayarları › Erişilebilirlik › Seslendirilmiş İçerik bölümünden Türkçe ses ekleyebilirsiniz.');
    }
    const utterance = new SpeechSynthesisUtterance(spoken(item.text));
    utterance.lang = 'tr-TR';
    if (voice.turkish) utterance.voice = voice.turkish;
    // holding the utterance keeps Chrome from garbage-collecting it mid-sentence, which loses its end event
    item.utterance = utterance;
    utterance.onstart = () => {
        // started: give it time to finish, but do not wait for an end event that may never come
        clearTimeout(voice.watchdog);
        voice.watchdog = setTimeout(item.finish, SPEECH_END_SLACK_MS + item.text.length * SPEECH_MS_PER_CHARACTER);
    };
    utterance.onend = item.finish;
    utterance.onerror = (event) => {
        if (event.error === 'not-allowed') {
            warnAboutVoice('blocked', 'Tarayıcı sesli okumayı engelledi. Sayfaya bir kez dokunup navigasyonu yeniden başlatın.');
        }
        item.finish();
    };
    // a sentence that never starts must not hold up the rest of the drive, and the user should know why it is quiet
    clearTimeout(voice.watchdog);
    voice.watchdog = setTimeout(() => {
        warnAboutVoice('stuck', 'Tarayıcının ses motoru yanıt vermiyor. Chrome\'u tamamen kapatıp (Mac\'te Cmd+Q) '
            + 'yeniden açın; bu bir tarayıcı hatası ve ancak yeniden başlatınca düzeliyor.');
        item.finish();
    }, SPEECH_START_MS);
    if (synth.paused) synth.resume();
    synth.speak(utterance);
}

/** The text as it should sound: "3. çıkışa girin" is read "üçüncü çıkışa girin", "2. ara durağa" "ikinci ara durağa". */
function spoken(text) {
    return text.replace(/\b(\d{1,2})\. (?=çıkış|ara durağ)/g,
        (match, number) => (SPOKEN_ORDINALS[number - 1] ?? match.trim()) + ' ');
}

/** Each kind of problem is reported once per visit. */
function warnAboutVoice(kind, message) {
    if (voice.warned.has(kind)) return;
    voice.warned.add(kind);
    toast(message, true, 12000);
}

function updateVoiceButton() {
    const on = state.nav?.voice ?? stored(VOICE_KEY) !== 'off';
    const button = $('#nav-voice');
    button.setAttribute('aria-pressed', String(on));
    button.replaceChildren(icon(on ? 'volume' : 'volumeOff'));
    button.title = on ? 'Sesli yönlendirmeyi kapat' : 'Sesli yönlendirmeyi aç';
}

/* ---------- welcome screen ---------- */

function showIntro() {
    const intro = $('#intro');
    intro.classList.remove('leaving');
    intro.hidden = false;
    $('#intro-start').focus({ preventScroll: true });
}

function hideIntro() {
    store(INTRO_KEY, 'seen');
    const intro = $('#intro');
    if (intro.hidden) return;
    intro.classList.add('leaving');
    setTimeout(() => {
        intro.hidden = true;
        intro.classList.remove('leaving');
        map.invalidateSize();
    }, reducedMotion.matches ? 0 : 280);
}

/* ---------- installing as an app ---------- */

let installPrompt = null;

/** Already added to the home screen and opened from there. */
const isStandalone = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
/** An iPhone or iPad (including iPadOS, which reports a desktop platform but has touch). */
const isApple = () => /iphone|ipad|ipod/i.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
/** On iOS, only Safari can add an app to the home screen; other browsers (Chrome, Firefox, Edge) cannot. */
const isIosSafari = () => isApple() && !/crios|fxios|edgios|opios/i.test(navigator.userAgent);

function setUpInstall() {
    window.addEventListener('beforeinstallprompt', (event) => {
        // keep the event; the header and welcome-screen buttons trigger it instead of the browser's own banner
        event.preventDefault();
        installPrompt = event;
        showInstallAffordances(true);
    });
    window.addEventListener('appinstalled', () => {
        installPrompt = null;
        showInstallAffordances(false);
        toast('ozecmaps ana ekranınıza eklendi');
    });
    // on iOS no beforeinstallprompt is ever fired, so offer the buttons there from the start (they open instructions)
    showInstallAffordances(!isStandalone() && isApple());
    $('#intro-ios').hidden = true;
    for (const button of document.querySelectorAll('#install-dialog [data-close-install]')) {
        button.addEventListener('click', () => $('#install-dialog').close());
    }
    $('#install-copy').addEventListener('click', copyLink);
    if ('serviceWorker' in navigator && window.isSecureContext) {
        navigator.serviceWorker.register('sw.js').catch(() => {
            // without the offline cache the site still works online
        });
    }
}

function showInstallAffordances(show) {
    $('#install-button').hidden = !show;
    $('#intro-install').hidden = !show;
}

async function installApp() {
    if (installPrompt) {
        installPrompt.prompt();
        const choice = await installPrompt.userChoice.catch(() => null);
        if (choice?.outcome === 'accepted') showInstallAffordances(false);
        installPrompt = null;
        return;
    }
    if (isApple()) {
        openInstallHelp();
        return;
    }
    // other browsers without a prompt (e.g. desktop Firefox): point to the browser menu
    openInstallHelp();
}

/** Shows how to add the app to the home screen, worded for the device and browser in use. */
function openInstallHelp() {
    const steps = $('#install-steps');
    const note = $('#install-note');
    const copy = $('#install-copy');
    note.hidden = true;
    copy.hidden = true;
    if (isApple() && isIosSafari()) {
        $('#install-intro').textContent = "ozecmaps'i iPhone/iPad ana ekranınıza uygulama gibi ekleyin:";
        steps.replaceChildren(
            el('li', {}, 'Aşağıdaki araç çubuğunda ', el('strong', {}, 'Paylaş'), ' düğmesine dokunun (yukarı ok).'),
            el('li', {}, el('strong', {}, 'Ana Ekrana Ekle'), "'yi seçin."),
            el('li', {}, 'Sağ üstte ', el('strong', {}, 'Ekle'), "'ye dokunun."));
    } else if (isApple()) {
        $('#install-intro').textContent = 'iPhone ve iPad’de uygulama olarak eklemek yalnızca Safari ile olur '
            + '(şu an başka bir tarayıcıdasınız):';
        steps.replaceChildren(
            el('li', {}, 'Bu bağlantıyı kopyalayın ve ', el('strong', {}, 'Safari'), "'de açın."),
            el('li', {}, 'Safari’de ', el('strong', {}, 'Paylaş'), ' → ', el('strong', {}, 'Ana Ekrana Ekle'),
                "'yi seçin."));
        copy.hidden = false;
    } else {
        $('#install-intro').textContent = 'Tarayıcı menüsünden uygulama olarak ekleyebilirsiniz:';
        steps.replaceChildren(
            el('li', {}, 'Tarayıcının ', el('strong', {}, 'menüsünü'), ' açın (⋮ ya da paylaş).'),
            el('li', {}, el('strong', {}, 'Uygulamayı yükle'), ' ya da ', el('strong', {}, 'Ana ekrana ekle'),
                "'yi seçin."));
    }
    $('#install-dialog').showModal();
}

async function copyLink() {
    const url = location.origin + location.pathname;
    try {
        await navigator.clipboard.writeText(url);
        toast('Bağlantı kopyalandı; Safari’de açıp yapıştırın');
    } catch {
        window.prompt('Bu bağlantıyı kopyalayıp Safari’de açın:', url);
    }
}

/* ---------- the bottom sheet on phones ---------- */

function setUpSheet() {
    const panel = $('#panel');
    const handle = $('#sheet-handle');
    const setExpanded = (expanded) => {
        panel.classList.toggle('expanded', expanded);
        handle.setAttribute('aria-expanded', String(expanded));
        handle.setAttribute('aria-label', expanded ? 'Paneli küçült' : 'Paneli büyüt');
    };
    let startY = null;
    handle.addEventListener('pointerdown', (event) => {
        startY = event.clientY;
    });
    handle.addEventListener('pointerup', (event) => {
        const moved = startY === null ? 0 : event.clientY - startY;
        startY = null;
        if (moved < -24) setExpanded(true);
        else if (moved > 24) setExpanded(false);
        else setExpanded(!panel.classList.contains('expanded'));
    });
    // the map controls and fitted routes stay clear of the sheet
    const follow = () => {
        document.documentElement.style.setProperty('--sheet-offset',
            phoneLayout.matches ? `${panel.offsetHeight + 8}px` : '0px');
    };
    new ResizeObserver(follow).observe(panel);
    phoneLayout.addEventListener?.('change', follow);
    follow();
}

/* ---------- address bar ---------- */

function writeHash() {
    const parts = [];
    if (state.from) parts.push(`from=${coordinate(state.from)}`);
    if (state.to) parts.push(`to=${coordinate(state.to)}`);
    const stops = state.stops.filter(Boolean);
    if (stops.length) parts.push(`via=${stops.map(coordinate).join(';')}`);
    parts.push(`mode=${state.mode}`);
    history.replaceState(null, '', `#${parts.join('&')}`);
}

function readHash() {
    const params = new URLSearchParams(location.hash.slice(1));
    const point = (value) => {
        const [lat, lon] = (value ?? '').split(',').map(Number);
        return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180
            ? { lat, lon } : null;
    };
    if (MODES[params.get('mode')]) state.mode = params.get('mode');
    const stops = (params.get('via') ?? '').split(';').map(point).filter(Boolean).slice(0, MAX_STOPS);
    return { from: point(params.get('from')), to: point(params.get('to')), stops };
}

function selectMode(mode) {
    const changed = state.mode !== mode;
    state.mode = mode;
    for (const button of document.querySelectorAll('.modes button')) {
        button.setAttribute('aria-checked', String(button.dataset.mode === mode));
    }
    // a journey by public transport goes straight to the destination; the stops wait for the other modes
    document.body.classList.toggle('transit-mode', mode === 'transit');
    if (layers.markers) drawStops();
    if (changed && mode === 'transit' && state.stops.some(Boolean)) {
        toast('Toplu taşımada ara duraklar kullanılmaz; diğer ulaşım şekillerinde yerinde duruyorlar');
    }
}

/* ---------- start ---------- */

function wireUp() {
    for (const holder of document.querySelectorAll('[data-icon]')) holder.prepend(icon(holder.dataset.icon));
    for (const holder of document.querySelectorAll('span[data-icon]')) holder.replaceWith(holder.firstChild);

    $('#planner').addEventListener('submit', (event) => event.preventDefault());
    $('#locate').addEventListener('click', locateMe);
    $('#swap').addEventListener('click', swapStops);
    for (const button of document.querySelectorAll('.modes button')) {
        button.addEventListener('click', () => {
            selectMode(button.dataset.mode);
            planRoute();
        });
    }
    $('#depart').addEventListener('change', (event) => {
        state.depart = event.target.value;
        planRoute();
    });
    $('#start-navigation').addEventListener('click', startNavigation);
    $('#nav-stop').addEventListener('click', () => stopNavigation());
    $('#nav-recenter').addEventListener('click', startFollowing);
    $('#share-arrival').addEventListener('click', shareArrival);
    $('#nav-share').addEventListener('click', shareArrival);
    $('#nav-simulate').addEventListener('click', () => {
        if (state.nav?.simulation) pauseSimulation();
        else startSimulation();
    });
    $('#nav-voice').addEventListener('click', () => {
        if (!state.nav) return;
        state.nav.voice = !state.nav.voice;
        store(VOICE_KEY, state.nav.voice ? 'on' : 'off');
        if (state.nav.voice) say('Sesli yönlendirme açık', true);
        else silence();
        updateVoiceButton();
    });
    $('#business-toggle').addEventListener('click', toggleBusinesses);
    $('#traffic-toggle').addEventListener('click', toggleTraffic);
    $('#brand').addEventListener('click', showIntro);
    $('#intro-start').addEventListener('click', hideIntro);
    $('#intro-locate').addEventListener('click', () => {
        hideIntro();
        locateMe();
    });
    $('#install-button').addEventListener('click', installApp);
    $('#intro-install').addEventListener('click', installApp);
    $('#add-stop').addEventListener('click', () => addStop());
    $('#save-place').addEventListener('click', () => {
        if ($('#save-menu').hidden) openSaveMenu();
        else closeSaveMenu();
    });
    document.addEventListener('click', (event) => {
        if (!event.target.closest('#save-menu, #save-place')) closeSaveMenu();
    });
    $('#clear-recent').addEventListener('click', () => {
        store(RECENT_KEY, null);
        renderRecent();
    });
    $('#traffic-toggle').setAttribute('aria-pressed', String(trafficOn()));
    $('#business-toggle').setAttribute('aria-pressed', String(businessesOn()));
    $('#report-button').addEventListener('click', () => {
        if (state.picking) stopPicking();
        else startPicking();
    });
    $('#incident-dialog').addEventListener('close', () => {
        if ($('#incident-dialog').returnValue === 'report') submitIncident();
    });
    $('#account-button').addEventListener('click', openAccount);
    $('#account-tab-login').addEventListener('click', () => setAccountMode('login'));
    $('#account-tab-register').addEventListener('click', () => setAccountMode('register'));
    $('#account-form').addEventListener('submit', (event) => {
        event.preventDefault();
        submitAccount();
    });
    $('#account-signout').addEventListener('click', () => {
        $('#account-dialog').close();
        signOut();
    });
    $('#account-delete').addEventListener('click', deleteAccount);
    for (const button of document.querySelectorAll('#account-dialog [data-close]')) {
        button.addEventListener('click', () => $('#account-dialog').close());
    }
    $('#admin-button').addEventListener('click', () => {
        if (stored(TOKEN_KEY, sessionStorage)) {
            store(TOKEN_KEY, null, sessionStorage);
            updateAdminButton();
            toast('Yönetici anahtarı silindi');
        } else if (askToken()) {
            toast('Yönetici anahtarı bu sekme için kaydedildi');
        }
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && state.picking) stopPicking();
        if (event.key === 'Escape' && !$('#intro').hidden) hideIntro();
        if (event.key === 'Escape' && !$('#save-menu').hidden) {
            closeSaveMenu();
            $('#save-place').focus();
        }
        if (event.key === 'Escape' && pendingShortcut) {
            pendingShortcut = null;
            $('#to-input').placeholder = 'Nereye gidiyorsunuz?';
        }
    });
    setUpSearch();
    setUpSheet();
    setUpInstall();
    renderShortcuts();
    renderFavorites();
    renderQuickDestinations();
    renderRecent();
    updateAdminButton();
}

async function init() {
    loadVoices();
    if ('speechSynthesis' in window) window.speechSynthesis.addEventListener?.('voiceschanged', loadVoices);
    setUpMap();
    wireUp();
    const fromHash = readHash();
    if (!fromHash.from && !fromHash.to && stored(INTRO_KEY) !== 'seen') showIntro();
    selectMode(state.mode);
    try {
        showArea(await waitForServer());
    } catch (error) {
        toast(error.message, true);
    }
    refreshAccount();
    await loadIncidents();
    loadTrafficStatus();
    refreshTraffic();
    navigator.permissions?.query({ name: 'geolocation' }).then((status) => {
        if (status.state === 'granted') startWatchingLocation();
    }).catch(() => {
        // the Permissions API is optional; the dot then appears after the first tap on the location button
    });
    document.addEventListener('visibilitychange', () => {
        if (document.hidden && !state.nav) {
            here.resumeOnShow = here.watchId !== null;
            stopWatchingLocation();
        } else if (!document.hidden && here.resumeOnShow) {
            here.resumeOnShow = false;
            startWatchingLocation();
        }
    });
    if (fromHash.from) await setStop('from', fromHash.from, { plan: false });
    if (fromHash.to) await setStop('to', fromHash.to, { plan: false });
    state.stops = fromHash.stops.map(() => null);
    renderStopFields();
    for (const [index, stop] of fromHash.stops.entries()) await setStop(`stop${index}`, stop, { plan: false });
    await planRoute();
    // other people may report or clear incidents; keep the picture fresh
    setInterval(() => {
        if (!state.nav) loadIncidents();
    }, 60000);
    // İBB publishes a new traffic index every five minutes
    setInterval(() => {
        loadTrafficStatus();
        if (!state.nav) refreshTraffic();
    }, 5 * 60000);
}

init();
