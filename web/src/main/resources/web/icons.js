// Inline SVG icon set (line style, currentColor) — replaces all emoji on the site.
(function () {
  const s = (p, filled) => '<svg viewBox="0 0 24 24" width="1em" height="1em" fill="'
    + (filled ? 'currentColor' : 'none') + '" stroke="currentColor" stroke-width="1.9"'
    + ' stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-0.15em;display:inline-block">'
    + p + '</svg>';
  const ICON = {
    home: s('<path d="M3 11l9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M9 20v-6h6v6"/>'),
    news: s('<rect x="3" y="4" width="13" height="16" rx="1"/><path d="M16 8h4v10a2 2 0 0 1-2 2H7"/><path d="M6 8h7M6 12h7M6 16h4"/>'),
    trophy: s('<path d="M8 4h8v5a4 4 0 0 1-8 0z"/><path d="M8 5H5v2a3 3 0 0 0 3 3M16 5h3v2a3 3 0 0 1-3 3"/><path d="M10 15h4M9 20h6M12 15v-2"/>'),
    cart: s('<circle cx="9" cy="20" r="1.4"/><circle cx="17" cy="20" r="1.4"/><path d="M3 4h2l2.4 12h10L20 8H6"/>'),
    gift: s('<rect x="3" y="8" width="18" height="4" rx="1"/><path d="M5 12v9h14v-9"/><path d="M12 8v13"/><path d="M12 8C12 8 11 3 8.5 3a2.5 2.5 0 0 0 0 5zM12 8s1-5 3.5-5a2.5 2.5 0 0 1 0 5z"/>'),
    card: s('<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>'),
    user: s('<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/>'),
    users: s('<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.5 3-5 6.5-5s6.5 1.5 6.5 5"/><path d="M16 5a3.5 3.5 0 0 1 0 7M18 20c0-3-1-4.5-3-5.3"/>'),
    mail: s('<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 7l9 6 9-6"/>'),
    send: s('<path d="M22 3L11 14"/><path d="M22 3l-7 19-4-8-8-4z"/>'),
    phone: s('<path d="M5 4h4l2 5-2.5 1.8a12 12 0 0 0 5.7 5.7L16 15l5 2v4a1.5 1.5 0 0 1-1.6 1.5A16.5 16.5 0 0 1 3.5 5.6 1.5 1.5 0 0 1 5 4z"/>'),
    gem: s('<path d="M6 3h12l3 6-9 12L3 9z"/><path d="M3 9h18M9 3l-3 6 6 12 6-12-3-6"/>'),
    coin: s('<ellipse cx="12" cy="6" rx="8" ry="3"/><path d="M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6"/><path d="M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"/>'),
    swords: s('<path d="M14.5 3H21v6.5M21 3l-8 8M3 14.5V21h6.5M3 21l8-8M13 13l3 3M6 16l-2 2 2 2 2-2"/>'),
    shield: s('<path d="M12 3l8 3v6c0 5-4 8-8 9-4-1-8-4-8-9V6z"/>'),
    social: s('<circle cx="7" cy="9" r="2.5"/><circle cx="17" cy="9" r="2.5"/><path d="M2.5 19c0-3 2-4.5 4.5-4.5S11.5 16 11.5 19M12.5 19c0-3 2-4.5 4.5-4.5S21.5 16 21.5 19"/>'),
    paw: s('<circle cx="6" cy="10" r="1.5" fill="currentColor" stroke="none"/><circle cx="10" cy="7" r="1.5" fill="currentColor" stroke="none"/><circle cx="14" cy="7" r="1.5" fill="currentColor" stroke="none"/><circle cx="18" cy="10" r="1.5" fill="currentColor" stroke="none"/><path d="M12 12c-2.6 0-4.5 1.8-4.5 3.6S9.4 18.5 12 18.5s4.5-1.1 4.5-2.9S14.6 12 12 12z"/>'),
    mobile: s('<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/>'),
    monitor: s('<rect x="3" y="4" width="18" height="12" rx="1"/><path d="M8 20h8M12 16v4"/>'),
    apple: s('<path d="M14 3c.2 1.8-1 3.2-2.6 3.1M12 7.5c-3 0-5 2.2-5 6 0 2.9 2 6.5 3.8 6.5.9 0 1.2-.5 2.2-.5s1.3.5 2.2.5c1.8 0 3.8-3.6 3.8-6.5 0-3.8-2-6-5-6z"/>'),
    coffee: s('<path d="M4 8h13v5a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z"/><path d="M17 9h2a2 2 0 0 1 0 4h-2"/><path d="M6 3v1.5M9.5 3v1.5M13 3v1.5M4 21h13"/>'),
    dot: s('<circle cx="12" cy="12" r="6" fill="currentColor" stroke="none"/>'),
    bag: s('<path d="M6 8h12l1 12H5z"/><path d="M9 8a3 3 0 0 1 6 0"/>'),
    monster: s('<path d="M5 12a7 7 0 0 1 14 0v4l-2 2-2-1.5L13 19l-2-2.5L9 18l-2-2z"/><circle cx="9.5" cy="11" r="1" fill="currentColor" stroke="none"/><circle cx="14.5" cy="11" r="1" fill="currentColor" stroke="none"/><path d="M9 4l1.5 3M15 4l-1.5 3"/>'),
    map: s('<path d="M9 4L3 6v14l6-2 6 2 6-2V4l-6 2-6-2z"/><path d="M9 4v14M15 6v14"/>'),
    store: s('<path d="M4 9l1.2-5h13.6L20 9M4 9h16v11H4zM4 9a2 2 0 0 0 4 0 2 2 0 0 0 4 0 2 2 0 0 0 4 0 2 2 0 0 0 4 0M9 20v-5h6v5"/>'),
    bank: s('<path d="M3 10h18M12 3l8 5H4zM6 10v7M10 10v7M14 10v7M18 10v7M3 21h18"/>'),
    box: s('<path d="M12 3l8 4v10l-8 4-8-4V7z"/><path d="M4 7l8 4 8-4M12 11v10"/>'),
    receipt: s('<path d="M5 3h14v18l-2.3-1.3-2.3 1.3-2.4-1.3L9.6 21l-2.3-1.3L5 21z"/><path d="M8 7h8M8 11h8M8 15h5"/>'),
    scroll: s('<path d="M7 4h11a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2H7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"/><path d="M9 8h5M9 12h5M9 16h3"/>'),
    chart: s('<path d="M5 20V10M12 20V4M19 20v-7M3 21h18"/>'),
    megaphone: s('<path d="M3 11v2a1 1 0 0 0 1 1h2l3 5h2l-1.2-5L20 18V6l-10 3H4a1 1 0 0 0-1 1z"/>'),
    bolt: s('<path d="M13 2L4 14h6l-1 8 9-12h-6z"/>'),
    key: s('<circle cx="8" cy="8" r="4"/><path d="M11 11l9 9M17 17l2-2M14 20l2-2"/>'),
    check: s('<circle cx="12" cy="12" r="9"/><path d="M8 12l3 3 5-6"/>'),
    clock: s('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>'),
    close: s('<path d="M6 6l12 12M18 6L6 18"/>'),
    menu: s('<path d="M3 6h18M3 12h18M3 18h18"/>'),
    right: s('<path d="M5 12h14M13 6l6 6-6 6"/>'),
    left: s('<path d="M19 12H5M11 18l-6-6 6-6"/>'),
    book: s('<path d="M12 6C10 4.3 6.5 4 3.5 5v13c3-1 6.5-.7 8.5 1 2-1.7 5.5-2 8.5-1V5c-3-1-6.5-.7-8.5 1z"/><path d="M12 6v13"/>'),
    facebook: s('<path d="M14 8.5h3V5h-3a3.5 3.5 0 0 0-3.5 3.5V11H8v3.5h2.5V21H14v-6.5h2.7l.6-3.5H14V8.8c0-.2.2-.3.4-.3z"/>'),
  };
  window.ICON = ICON;
  window.hydrateIcons = function (root) {
    (root || document).querySelectorAll('[data-icon]').forEach(function (e) {
      const name = e.getAttribute('data-icon');
      if (ICON[name]) e.innerHTML = ICON[name];
    });
  };
})();
