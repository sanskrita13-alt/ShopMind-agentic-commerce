import { useNavigate } from "react-router-dom";
import { useState } from "react";

const SHOE_IMAGE = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=900&q=80";

const popular = [
  { l: "Daily Wear",    v: "ghost" },
  { l: "Running",       v: "ghost" },
  { l: "Gym",           v: "ghost" },
  { l: "Formal",        v: "ghost" },
  { l: "Trekking",      v: "ghost" },
  { l: "Comfort-first", v: "" },
  { l: "Style-first",   v: "" },
];

export default function LandingPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  function start() { navigate("/chat"); }

  return (
    <div className="cp-app">
      {/* Nav */}
      <header className="cp-nav">
        <div className="cp-logo">Shop<b>Mind</b></div>
        <nav className="cp-nav-tabs">
          <button className="cp-nav-tab is-active">Curations</button>
          <button className="cp-nav-tab" onClick={start}>Intelligence</button>
          <button className="cp-nav-tab" onClick={start}>Archive</button>
        </nav>
        <div className="cp-nav-right">
          <button className="cp-nav-icon" aria-label="Search">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
            </svg>
          </button>
        </div>
      </header>

      <main className="cp-main">
        <section className="cp-welcome">
          {/* Left */}
          <div className="cp-welcome-left">
            <h1>Find footwear you'll actually want to keep.</h1>
            <p>Shop confidently through intelligent conversations, transparent reasoning, and smarter recommendations.</p>

            <form className="cp-welcome-search" onSubmit={(e) => { e.preventDefault(); start(); }}>
              <label className="cp-welcome-search-input">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M9.5 2A2.5 2.5 0 0 1 12 4.5v15a2.5 2.5 0 0 1-5 0V4.5A2.5 2.5 0 0 1 9.5 2z"/>
                  <path d="M14.5 2A2.5 2.5 0 0 0 12 4.5v15a2.5 2.5 0 0 0 5 0V4.5A2.5 2.5 0 0 0 14.5 2z"/>
                </svg>
                <input
                  placeholder="Describe what you're looking for…"
                  value={query}
                  onChange={e => setQuery(e.target.value)}
                />
              </label>
              <button type="submit" className="cp-btn cp-btn--dark">Start Finding My Fit</button>
            </form>

            <div className="cp-welcome-popular">
              <span className="cp-kicker">Popular requests</span>
              <div className="cp-welcome-popular-pills">
                {popular.map((p, i) => (
                  <button key={i} className={`cp-pill${p.v ? ` cp-pill--${p.v}` : ""}`} onClick={start}>
                    {p.l}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Right — hero image with tooltips */}
          <div className="cp-welcome-right">
            <div className="cp-welcome-stage">
              <img src={SHOE_IMAGE} alt="Featured shoe" referrerPolicy="no-referrer" />
            </div>

            <div className="cp-tooltip cp-tooltip--a">
              <div className="cp-tooltip-head">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>
                </svg>
                Insight
              </div>
              Optimal arch support detected for extended daily wear.
            </div>

            <div className="cp-tooltip cp-tooltip--b">
              <div className="cp-tooltip-head">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M2 20a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8l-7-7H4a2 2 0 0 0-2 2v17z"/>
                  <path d="M14 2v6h6"/>
                </svg>
                Materials
              </div>
              ReactX foam cushioning for all-day comfort.
            </div>
          </div>
        </section>

        {/* How it works */}
        <section style={{ padding: "60px var(--cp-pad) 80px", borderTop: "1px solid var(--cp-line)" }}>
          <div style={{ maxWidth: 880, margin: "0 auto" }}>
            <span className="cp-kicker" style={{ display: "block", marginBottom: 16 }}>How it works</span>
            <h2 style={{ fontFamily: "var(--cp-serif)", fontSize: "clamp(32px,4vw,48px)", fontWeight: 400, letterSpacing: "-0.01em", lineHeight: 1.05, marginBottom: 48 }}>
              Four stages.<br />One clear decision.
            </h2>

            <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 2 }}>
              {[
                { n: "01", t: "Curations", d: "Tell me what you need. One focused question at a time." },
                { n: "02", t: "Intelligence", d: "I score every option against your weighted priorities." },
                { n: "03", t: "Compare", d: "Side-by-side metrics. Winner highlighted per row." },
                { n: "04", t: "Archive", d: "Full reasoning, retailer table, and confident checkout." },
              ].map((s, i) => (
                <div key={i} style={{ background: "var(--cp-panel)", padding: "24px 20px", borderRadius: i === 0 ? "10px 0 0 10px" : i === 3 ? "0 10px 10px 0" : 0, border: "1px solid var(--cp-line)" }}>
                  <div style={{ fontFamily: "var(--cp-serif)", fontSize: 40, lineHeight: 0.9, color: i === 0 ? "var(--cp-cta)" : "var(--cp-faint)", marginBottom: 14, fontWeight: 500 }}>{s.n}</div>
                  <h3 style={{ fontFamily: "var(--cp-serif)", fontSize: 20, fontWeight: 500, marginBottom: 8 }}>{s.t}</h3>
                  <p style={{ fontSize: 13, color: "var(--cp-mute)", lineHeight: 1.6 }}>{s.d}</p>
                </div>
              ))}
            </div>

            <div style={{ marginTop: 40, textAlign: "center" }}>
              <button className="cp-btn cp-btn--dark cp-btn--lg" onClick={start}>
                Start a session
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </button>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer style={{ padding: "20px var(--cp-pad)", borderTop: "1px solid var(--cp-line)", display: "flex", alignItems: "center", justifyContent: "space-between", background: "var(--cp-panel-2)", position: "relative", zIndex: 1 }}>
        <div className="cp-logo" style={{ fontSize: 18 }}>Shop<b>Mind</b></div>
        <span className="cp-kicker">AI Footwear Decision Intelligence</span>
      </footer>
    </div>
  );
}
