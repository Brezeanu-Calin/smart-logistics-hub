<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <main class="landing">
    <header class="landing-header">
      <RouterLink class="brand" to="/" aria-label="Haulmaster home">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 48 32"><path d="M2 5h27v19H2zM29 11h9l8 8v5H29zM33 13v6h9" /><circle cx="11" cy="26" r="3" /><circle cx="37" cy="26" r="3" /></svg>
        </span>
        <span class="brand-name">HAULMASTER<small>LOGISTICS, IN MOTION</small></span>
      </RouterLink>
      <nav aria-label="Main navigation">
        <a href="#platform">Platform</a>
        <RouterLink class="header-cta" to="/app">Start using Haulmaster <span aria-hidden="true">→</span></RouterLink>
      </nav>
    </header>

    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow"><span></span> WELCOME TO HAULMASTER</p>
        <h1>Move every delivery forward.</h1>
        <p class="intro">One clear view of your fleet, freight, and routes. Make confident dispatch decisions and keep your logistics moving.</p>
        <RouterLink class="start-button" to="/app">Start now <span aria-hidden="true">→</span></RouterLink>
        <div class="trust-note"><span class="trust-check">✓</span> Built for the people who keep business moving</div>
      </div>

      <div class="visual" aria-label="Illustration of a delivery route and active vehicle">
        <div class="visual-top"><span>NETWORK OVERVIEW</span><span class="live"><i></i> OPERATIONS READY</span></div>
        <div class="route-canvas">
          <div class="map-grid"></div>
          <svg class="route-line" viewBox="0 0 520 300" preserveAspectRatio="none" aria-hidden="true">
            <path d="M84 221 C132 208 132 131 203 142 S283 223 335 177 S386 80 444 87" />
          </svg>
          <span class="map-pin origin"><i></i></span>
          <span class="map-pin destination"><i></i></span>
          <div class="vehicle-card">
            <span class="truck-icon" aria-hidden="true">↗</span>
            <span><strong>Fleet at a glance</strong><small>Routes, vehicles &amp; deliveries</small></span>
            <span class="card-arrow">→</span>
          </div>
          <span class="map-label label-origin">CLUJ-NAPOCA</span>
          <span class="map-label label-destination">BUCHAREST</span>
          <div class="map-coordinate">46.77° N&nbsp; 23.59° E</div>
        </div>
        <div class="visual-bottom"><span><b>01</b> Plan with clarity</span><span><b>02</b> Deliver with confidence</span></div>
      </div>
    </section>

    <section id="platform" class="platform">
      <div class="platform-heading">
        <p class="eyebrow">A BETTER WAY TO DISPATCH</p>
        <h2>Everything moving. Nothing missed.</h2>
        <p>Bring day-to-day logistics into one dependable workspace.</p>
      </div>
      <div class="feature-grid">
        <article class="feature-card">
          <span class="feature-icon routes-icon" aria-hidden="true">↗</span>
          <h3>Plan smarter routes</h3>
          <p>Build dispatches around your drivers, vehicles, and delivery commitments.</p>
        </article>
        <article class="feature-card">
          <span class="feature-icon fleet-icon" aria-hidden="true">▰</span>
          <h3>Know your fleet</h3>
          <p>Keep vehicles, trailers, driver availability, and maintenance in view.</p>
        </article>
        <article class="feature-card">
          <span class="feature-icon insight-icon" aria-hidden="true">▤</span>
          <h3>Stay on top of freight</h3>
          <p>Track shipments from assignment through delivery, all in one place.</p>
        </article>
      </div>
    </section>

    <footer class="landing-footer">
      <span>HAULMASTER</span>
      <span>Made for teams that keep the world moving.</span>
      <RouterLink to="/app">Enter dispatch <span aria-hidden="true">→</span></RouterLink>
    </footer>
  </main>
</template>

<style scoped>
.landing {
  --ink: #17283e;
  --muted: #66778a;
  --blue: #176b8a;
  --line: #e2eaf0;
  min-height: 100vh;
  overflow: hidden;
  color: var(--ink);
  background: #f7fafb;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.landing * { box-sizing: border-box; }
.landing-header, .hero, .platform, .landing-footer { width: min(1190px, calc(100% - 64px)); margin: 0 auto; }
.landing-header { display: flex; height: 82px; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--line); }
.brand { display: inline-flex; align-items: center; gap: 11px; color: var(--ink); text-decoration: none; }
.brand-mark { display: grid; width: 39px; height: 39px; place-items: center; border-radius: 11px; background: #dceff3; }
.brand-mark svg { width: 29px; fill: none; stroke: var(--blue); stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; }
.brand-name { font-size: 13px; font-weight: 800; letter-spacing: .075em; }
.brand-name small { display: block; margin-top: 3px; color: #8192a1; font-size: 8px; font-weight: 650; letter-spacing: .13em; }
.landing-header nav { display: flex; align-items: center; gap: 32px; }
.landing-header nav > a { color: #54687a; font-size: 13px; font-weight: 600; text-decoration: none; }
.landing-header nav > a:hover, .landing-footer a:hover { color: var(--blue); }
.landing-header nav .header-cta { color: var(--blue); }
.header-cta span { margin-left: 6px; }

.hero { display: grid; grid-template-columns: 1fr 1.03fr; align-items: center; gap: 65px; padding-top: 78px; padding-bottom: 87px; }
.hero-copy { padding: 5px 0; }
.eyebrow { margin: 0 0 17px; color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: .14em; }
.hero-copy .eyebrow { display: flex; align-items: center; gap: 9px; }
.hero-copy .eyebrow span { width: 7px; height: 7px; border-radius: 50%; background: #39a88b; box-shadow: 0 0 0 4px #e1f3ed; }
.hero h1 { max-width: 520px; margin: 0; color: var(--ink); font-size: clamp(42px, 5.2vw, 66px); font-weight: 740; letter-spacing: -.06em; line-height: 1.04; }
.intro { max-width: 465px; margin: 22px 0 25px; color: var(--muted); font-size: 16px; line-height: 1.75; }
.start-button { display: inline-flex; min-height: 50px; align-items: center; gap: 22px; border-radius: 7px; padding: 0 20px; color: #fff; background: #176b8a; box-shadow: 0 7px 16px #176b8a2b; font-size: 14px; font-weight: 700; text-decoration: none; transition: background .2s, transform .2s; }
.start-button:hover { transform: translateY(-2px); background: #115a75; }
.start-button span { font-size: 18px; }
.trust-note { display: flex; align-items: center; gap: 9px; margin-top: 22px; color: #8190a0; font-size: 11px; }
.trust-check { display: grid; width: 18px; height: 18px; place-items: center; border-radius: 50%; color: #238567; background: #e3f3ec; font-size: 11px; font-weight: 800; }

.visual { overflow: hidden; border: 1px solid #e1eaf0; border-radius: 13px; background: #fff; box-shadow: 0 22px 60px #19344a12; }
.visual-top, .visual-bottom { display: flex; align-items: center; justify-content: space-between; padding: 17px 19px; color: #8091a0; font-size: 9px; font-weight: 750; letter-spacing: .09em; }
.visual-top { border-bottom: 1px solid #edf1f4; }
.live { display: inline-flex; align-items: center; gap: 6px; color: #26836d; font-size: 8px; }
.live i { width: 6px; height: 6px; border-radius: 50%; background: #42b292; }
.route-canvas { position: relative; height: 300px; overflow: hidden; background: #f1f7f7; }
.map-grid { position: absolute; inset: -20%; opacity: .63; background-image: linear-gradient(28deg, transparent 47%, #fff 47.5%, #fff 49%, transparent 49.5%), linear-gradient(112deg, transparent 44%, #fff 44.5%, #fff 46%, transparent 46.5%), linear-gradient(0deg, transparent 48%, #e1ebeb 48.5%, #e1ebeb 49%, transparent 49.5%); background-size: 120px 92px, 150px 112px, 100% 83px; transform: rotate(-8deg) scale(1.15); }
.route-line { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
.route-line path { fill: none; stroke: #2a8b9b; stroke-dasharray: 6 6; stroke-linecap: round; stroke-width: 3; }
.map-pin { position: absolute; z-index: 1; display: grid; width: 18px; height: 18px; place-items: center; border: 4px solid #fff; border-radius: 50%; background: #217f91; box-shadow: 0 2px 7px #18394a33; }
.map-pin i { width: 4px; height: 4px; border-radius: 50%; background: #fff; }
.origin { left: 15%; top: 71%; }
.destination { left: 83%; top: 26%; background: #e19a43; }
.map-label { position: absolute; z-index: 1; color: #5f7381; font-size: 8px; font-weight: 800; letter-spacing: .08em; }
.label-origin { left: 9%; top: 80%; }
.label-destination { left: 77%; top: 17%; }
.vehicle-card { position: absolute; z-index: 1; top: 53%; left: 43%; display: flex; align-items: center; gap: 10px; border: 1px solid #e4ebee; border-radius: 8px; padding: 10px 12px; background: #fff; box-shadow: 0 6px 18px #21394b12; }
.truck-icon { display: grid; width: 31px; height: 31px; place-items: center; border-radius: 7px; color: #176b8a; background: #e6f2f5; font-size: 17px; }
.vehicle-card strong, .vehicle-card small { display: block; white-space: nowrap; }
.vehicle-card strong { color: #25394c; font-size: 10px; }
.vehicle-card small { margin-top: 3px; color: #8a9aa7; font-size: 8px; }
.card-arrow { margin-left: 7px; color: #8093a0; }
.map-coordinate { position: absolute; right: 12px; bottom: 10px; color: #8da0a7; font-size: 8px; letter-spacing: .04em; }
.visual-bottom { justify-content: flex-start; gap: 25px; border-top: 1px solid #edf1f4; font-size: 8px; letter-spacing: .025em; }
.visual-bottom b { margin-right: 5px; color: #318295; }

.platform { padding: 52px 0 78px; border-top: 1px solid var(--line); }
.platform-heading { margin-bottom: 27px; text-align: center; }
.platform-heading .eyebrow { margin-bottom: 10px; }
.platform-heading h2 { margin: 0; color: var(--ink); font-size: 29px; letter-spacing: -.04em; }
.platform-heading > p:last-child { margin: 9px 0 0; color: var(--muted); font-size: 13px; }
.feature-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; }
.feature-card { min-height: 167px; border: 1px solid var(--line); border-radius: 9px; padding: 20px; background: #fff; }
.feature-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 8px; font-size: 17px; font-weight: 700; }
.routes-icon { color: #287c91; background: #e8f4f6; }
.fleet-icon { color: #6b66bb; background: #f0effa; }
.insight-icon { color: #318768; background: #eaf5ef; }
.feature-card h3 { margin: 14px 0 6px; color: #263b4e; font-size: 14px; }
.feature-card p { max-width: 300px; margin: 0; color: #788a99; font-size: 11px; line-height: 1.65; }
.landing-footer { display: flex; min-height: 66px; align-items: center; justify-content: space-between; border-top: 1px solid var(--line); color: #8a9aa7; font-size: 10px; }
.landing-footer > span:first-child { color: #526a7c; font-size: 9px; font-weight: 800; letter-spacing: .1em; }
.landing-footer a { color: var(--blue); font-weight: 650; text-decoration: none; }
.landing-footer a span { margin-left: 4px; }

@media (max-width: 850px) {
  .hero { grid-template-columns: 1fr; gap: 38px; padding-top: 52px; padding-bottom: 60px; }
  .hero-copy { max-width: 620px; }
  .visual { width: min(650px, 100%); justify-self: center; }
}
@media (max-width: 600px) {
  .landing-header, .hero, .platform, .landing-footer { width: calc(100% - 36px); }
  .landing-header { height: 68px; }
  .landing-header nav { gap: 13px; }
  .landing-header nav > a { font-size: 11px; }
  .landing-header nav > a:first-child { display: none; }
  .header-cta span { margin-left: 2px; }
  .hero { gap: 30px; padding-top: 43px; padding-bottom: 45px; }
  .hero h1 { font-size: clamp(41px, 12vw, 56px); }
  .intro { font-size: 14px; }
  .route-canvas { height: 235px; }
  .vehicle-card { left: 33%; transform: scale(.9); transform-origin: left center; }
  .visual-bottom { gap: 12px; padding: 14px 12px; font-size: 7px; }
  .platform { padding: 39px 0 48px; }
  .platform-heading h2 { font-size: 24px; }
  .feature-grid { grid-template-columns: 1fr; }
  .feature-card { min-height: auto; }
  .landing-footer { flex-wrap: wrap; gap: 9px; padding: 15px 0; }
  .landing-footer > span:nth-child(2) { order: 3; width: 100%; }
}
</style>
