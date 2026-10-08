/* Site-wide UI behaviour: scroll reveal, animated counters, navbar on scroll, hero headline. */
(function () {
    'use strict';
    var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // ---- Navbar: transparent over the home hero, solid once the page scrolls ----
    var nav = document.querySelector('.luxe-navbar.navbar-overlay');
    if (nav) {
        var onScroll = function () { nav.classList.toggle('is-scrolled', window.scrollY > 40); };
        onScroll();
        window.addEventListener('scroll', onScroll, { passive: true });
    }

    // ---- Hero headline: split into words that rise in one after another ----
    document.querySelectorAll('[data-split-words]').forEach(function (el) {
        if (reduceMotion) return;
        var delay = parseFloat(el.getAttribute('data-split-words')) || 0;
        var i = 0;
        var wrap = function (node) {
            Array.prototype.slice.call(node.childNodes).forEach(function (child) {
                if (child.nodeType === 3) {
                    var frag = document.createDocumentFragment();
                    child.textContent.split(/(\s+)/).forEach(function (part) {
                        if (!part) return;
                        if (/^\s+$/.test(part)) { frag.appendChild(document.createTextNode(part)); return; }
                        var span = document.createElement('span');
                        span.className = 'word';
                        span.style.animationDelay = (delay + i++ * 0.08) + 's';
                        span.textContent = part;
                        frag.appendChild(span);
                    });
                    child.parentNode.replaceChild(frag, child);
                } else if (child.nodeType === 1) {
                    wrap(child);
                }
            });
        };
        wrap(el);
    });

    // ---- Scroll reveal ----
    var revealables = document.querySelectorAll('.reveal');
    if (!('IntersectionObserver' in window) || reduceMotion) {
        revealables.forEach(function (el) { el.classList.add('is-visible'); });
    } else {
        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('is-visible');
                    io.unobserve(entry.target);
                }
            });
        }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
        revealables.forEach(function (el) { io.observe(el); });
    }

    // ---- Animated counters: <span data-count="42">0</span> ----
    var counters = document.querySelectorAll('[data-count]');
    var runCounter = function (el) {
        var target = parseFloat(el.getAttribute('data-count')) || 0;
        var plain = el.hasAttribute('data-plain');   // years etc. - no thousands separator
        var fmt = function (n) { return plain ? String(n) : n.toLocaleString(); };
        if (reduceMotion || target === 0) { el.textContent = fmt(target); return; }
        var start = null, duration = 1600;
        var step = function (ts) {
            if (!start) start = ts;
            var p = Math.min((ts - start) / duration, 1);
            var eased = 1 - Math.pow(1 - p, 3);
            el.textContent = fmt(Math.round(target * eased));
            if (p < 1) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    };
    if ('IntersectionObserver' in window) {
        var co = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) { runCounter(entry.target); co.unobserve(entry.target); }
            });
        }, { threshold: 0.5 });
        counters.forEach(function (el) { co.observe(el); });
    } else {
        counters.forEach(runCounter);
    }

    // ---- Hero search: keep check-out after check-in ----
    var inEl = document.querySelector('[data-checkin]');
    var outEl = document.querySelector('[data-checkout]');
    if (inEl && outEl) {
        inEl.addEventListener('change', function () {
            if (!inEl.value) return;
            var next = new Date(inEl.value);
            next.setDate(next.getDate() + 1);
            var iso = next.toISOString().slice(0, 10);
            outEl.min = iso;
            if (!outEl.value || outEl.value <= inEl.value) outEl.value = iso;
        });
    }

    // ---- Arched photo slideshow: <div data-slideshow="5500"> with .arch-slide children and .arch-dots buttons ----
    document.querySelectorAll('[data-slideshow]').forEach(function (stage) {
        var slides = stage.querySelectorAll('.arch-slide');
        var dots = stage.querySelectorAll('.arch-dots button');
        var ms = parseInt(stage.getAttribute('data-slideshow'), 10) || 5500;
        var current = 0, timer = null;
        stage.style.setProperty('--slide-ms', ms + 'ms');
        var show = function (i) {
            current = (i + slides.length) % slides.length;
            slides.forEach(function (s, k) { s.classList.toggle('is-active', k === current); });
            dots.forEach(function (d, k) {
                d.classList.remove('is-active');
                if (k === current) { void d.offsetWidth; d.classList.add('is-active'); }   // restart the progress bar
                d.setAttribute('aria-selected', k === current ? 'true' : 'false');
            });
        };
        var start = function () {
            clearInterval(timer);
            if (!reduceMotion) timer = setInterval(function () { show(current + 1); }, ms);
        };
        dots.forEach(function (d, k) { d.addEventListener('click', function () { show(k); start(); }); });
        document.addEventListener('visibilitychange', function () { if (document.hidden) clearInterval(timer); else start(); });
        show(0);
        start();
    });

    // ---- Live local time: <span data-clock="Asia/Colombo"> ----
    document.querySelectorAll('[data-clock]').forEach(function (el) {
        var tz = el.getAttribute('data-clock');
        var tick = function () {
            try {
                el.textContent = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: tz }).format(new Date());
            } catch (e) { el.textContent = ''; }
        };
        tick();
        setInterval(tick, 20000);
    });

    // ---- Island map: hovering a destination in the list highlights its pin, and the other way round ----
    document.querySelectorAll('[data-map]').forEach(function (section) {
        var link = function (name, on) {
            section.querySelectorAll('[data-dest]').forEach(function (el) {
                if (el.getAttribute('data-dest') === name) el.classList.toggle('is-hot', on);
            });
        };
        section.querySelectorAll('[data-dest]').forEach(function (el) {
            var name = el.getAttribute('data-dest');
            el.addEventListener('mouseenter', function () { link(name, true); });
            el.addEventListener('mouseleave', function () { link(name, false); });
            el.addEventListener('focus', function () { link(name, true); });
            el.addEventListener('blur', function () { link(name, false); });
        });
    });

    var finePointer = window.matchMedia && window.matchMedia('(hover: hover) and (pointer: fine)').matches;

    // ---- Gentle 3D tilt on cards: <div data-tilt> ----
    if (finePointer && !reduceMotion) {
        document.querySelectorAll('[data-tilt]').forEach(function (card) {
            card.addEventListener('mousemove', function (e) {
                var r = card.getBoundingClientRect();
                var x = (e.clientX - r.left) / r.width - 0.5;
                var y = (e.clientY - r.top) / r.height - 0.5;
                card.classList.add('tilting');
                card.style.transform = 'perspective(900px) rotateX(' + (-y * 6).toFixed(2) + 'deg) rotateY(' + (x * 8).toFixed(2) + 'deg) translateY(-6px)';
            });
            card.addEventListener('mouseleave', function () {
                card.classList.remove('tilting');
                card.style.transform = '';
            });
        });
    }

    // ---- Light parallax: <div data-parallax="0.1"> moves at a fraction of the scroll speed ----
    var parallax = document.querySelectorAll('[data-parallax]');
    if (parallax.length && !reduceMotion) {
        var ticking = false;
        var update = function () {
            var y = window.scrollY;
            parallax.forEach(function (el) {
                if (y > window.innerHeight * 1.5) return;   // only while the hero is near the viewport
                el.style.transform = 'translate3d(0,' + (y * parseFloat(el.getAttribute('data-parallax'))).toFixed(1) + 'px,0)';
            });
            ticking = false;
        };
        window.addEventListener('scroll', function () {
            if (!ticking) { ticking = true; requestAnimationFrame(update); }
        }, { passive: true });
    }

    // ---- WhatsApp button: show its label briefly once per visit so people notice it ----
    var wa = document.querySelector('[data-wa]');
    if (wa) {
        var seen = false;
        try { seen = sessionStorage.getItem('waPeek') === '1'; sessionStorage.setItem('waPeek', '1'); } catch (e) { /* storage blocked */ }
        if (!seen) {
            setTimeout(function () { wa.classList.add('peek'); }, 2500);
            setTimeout(function () { wa.classList.remove('peek'); }, 7500);
        }
    }

    // ---- Offer countdowns: <div data-ends-at="epochMillis"> ... <span class="js-countdown"></span> ----
    // When the deadline passes the offer is marked expired and its code can no longer be copied,
    // so a page left open never keeps showing a coupon that the server would refuse.
    var timed = document.querySelectorAll('[data-ends-at]');
    if (timed.length) {
        var fmt = function (ms) {
            var s = Math.max(0, Math.floor(ms / 1000));
            var d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60), sec = s % 60;
            if (d > 0) return d + 'd ' + h + 'h';
            if (h > 0) return h + 'h ' + String(m).padStart(2, '0') + 'm';
            return m + 'm ' + String(sec).padStart(2, '0') + 's';
        };
        var tickOffers = function () {
            var now = Date.now();
            timed.forEach(function (el) {
                if (el.classList.contains('is-expired')) return;
                var left = parseInt(el.getAttribute('data-ends-at'), 10) - now;
                var label = el.querySelector('.js-countdown');
                if (left <= 0) {
                    el.classList.add('is-expired');
                    if (label) label.textContent = 'Ended';
                    el.querySelectorAll('[data-code]').forEach(function (c) {
                        c.removeAttribute('onclick');
                        c.setAttribute('aria-disabled', 'true');
                        c.title = 'This offer has ended';
                    });
                    return;
                }
                if (label) label.textContent = 'Ends in ' + fmt(left);
                el.classList.toggle('is-ending', left < 10 * 60 * 1000);
            });
        };
        tickOffers();
        setInterval(tickOffers, 1000);
    }
})();
