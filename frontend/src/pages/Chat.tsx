import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useChatStore, type NavTab } from "@/store/chat";
import type { RecommendationDTO } from "@/lib/types";

/* ── Inline SVG helpers ── */
const Ico = {
  Arrow: (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M5 12h14M12 5l7 7-7 7"/></svg>,
  Back:  (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M19 12H5M12 19l-7-7 7-7"/></svg>,
  Send:  (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M5 12l14-7-4 16-3-7-7-2z"/></svg>,
  Search:(p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>,
  Info:  (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>,
  Check: (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M5 12l4 4 10-10"/></svg>,
  Plus:  (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12 5v14M5 12h14"/></svg>,
  X:     (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M6 6l12 12M18 6l-12 12"/></svg>,
  Expand:(p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3"/></svg>,
  Leaf:  (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10z"/></svg>,
  Truck: (p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M1 7h13v10H1z"/><path d="M14 10h4l3 3v4h-7z"/><circle cx="5.5" cy="18.5" r="2"/><circle cx="17.5" cy="18.5" r="2"/></svg>,
  Return:(p: React.SVGProps<SVGSVGElement>) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M3 7v6h6"/><path d="M3 13a9 9 0 1 0 3-7.7"/></svg>,
};

/* ── Top Nav ── */
function TopNav({ activeTab, onTab }: { activeTab: NavTab; onTab: (t: NavTab) => void }) {
  const navigate = useNavigate();
  const tabs: NavTab[] = ["Curations", "Intelligence", "Archive"];
  return (
    <header className="cp-nav">
      <button className="cp-logo" style={{ background: "transparent" }} onClick={() => navigate("/")}>
        Shop<b>Mind</b>
      </button>
      <nav className="cp-nav-tabs">
        {tabs.map(tab => (
          <button key={tab} className={`cp-nav-tab${tab === activeTab ? " is-active" : ""}`} onClick={() => onTab(tab)}>
            {tab}
          </button>
        ))}
      </nav>
    </header>
  );
}

/* ── Mini chat rail (inside Discovery) ── */
function ChatMini() {
  const { messages, isLoading, send } = useChatStore();
  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => { scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" }); }, [messages.length]);

  function handleSend() {
    if (!input.trim() || isLoading) return;
    send(input.trim());
    setInput("");
  }

  return (
    <div className="cp-chat-mini">
      <div className="cp-chat-mini-msgs" ref={scrollRef}>
        {messages.length === 0 ? (
          <div style={{ color: "var(--cp-mute)", fontSize: 13 }}>Start chatting — tell me what you need.</div>
        ) : messages.map(m => (
          <div key={m.id} className={`cp-chat-mini-msg${m.role === "user" ? " cp-chat-mini-msg--user" : ""}`}>
            {m.role === "assistant" && <span className="cp-chat-mini-who">ShopMind</span>}
            <div className={`cp-chat-mini-bub${m.role === "user" ? " cp-chat-mini-bub--user" : ""}`}>{m.content}</div>
          </div>
        ))}
        {isLoading && (
          <div className="cp-chat-mini-msg">
            <span className="cp-chat-mini-who">ShopMind</span>
            <div className="cp-chat-typing"><span /><span /><span /></div>
          </div>
        )}
      </div>
      <div className="cp-chat-mini-input">
        <input
          placeholder="Message ShopMind…"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === "Enter" && handleSend()}
          disabled={isLoading}
        />
        <button className="cp-chat-mini-send" onClick={handleSend} disabled={!input.trim() || isLoading}>
          <Ico.Send width={14} height={14} />
        </button>
      </div>
    </div>
  );
}

/* ── Rec flag ── */
function RecFlag({ flag }: { flag: RecommendationDTO["regretFlags"][number] }) {
  const cls = flag.severity === "MEDIUM" ? "cp-rec-flag--medium" : flag.severity === "HIGH" ? "cp-rec-flag--medium" : "";
  return (
    <div className={`cp-rec-flag ${cls}`}>
      <span className="cp-rec-flag-icon"><Ico.Info width={12} height={12} /></span>
      <div className="cp-rec-flag-body">
        <div className="cp-rec-flag-title">{flag.title}</div>
        {flag.description}
      </div>
    </div>
  );
}

function fitLabel(pct: number): string {
  if (pct >= 90) return "Excellent";
  if (pct >= 75) return "Strong";
  if (pct >= 60) return "Good";
  if (pct >= 45) return "Moderate";
  if (pct >= 30) return "Low";
  return "Weak";
}

/* ══════════════════════════════════════════════════════════════════
   STAGE: DISCOVERY / INTELLIGENCE
   ══════════════════════════════════════════════════════════════════ */
function StageDiscovery({ onShortlist: _onShortlist }: { onShortlist: () => void }) {
  const { intent, messages } = useChatStore();
  const confidence = Math.round((intent?.confidenceScore || 0.45) * 100);
  const userCount = messages.filter(m => m.role === "user").length;

  return (
    <section className="cp-disco">
      {/* Left col */}
      <div>
        {intent?.primaryUseCase && (
          <div className="cp-active-req">
            <div className="cp-active-req-icon">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z"/>
              </svg>
            </div>
            <div className="cp-active-req-body">
              <span className="cp-kicker">What I've understood so far</span>
              <q>I need shoes for {intent.primaryUseCase}.</q>
            </div>
          </div>
        )}

        <ChatMini />

        {userCount > 0 && (
          <div className="cp-q-progress" style={{ marginTop: 14, padding: "0 4px" }}>
            <span className="cp-q-progress-txt">Signal {userCount} / 5</span>
            <div className="cp-q-progress-bar"><div style={{ width: `${Math.min(userCount / 5 * 100, 100)}%` }} /></div>
          </div>
        )}
      </div>

      {/* Right col — Decision Intelligence */}
      <div className="cp-card">
        <div className="cp-intel-head">
          <h3>Decision Intelligence</h3>
          <button className="cp-intel-iconbtn" style={{ width: 28, height: 28, borderRadius: 6, display: "grid", placeItems: "center", color: "var(--cp-mute)", transition: "background .15s" }}>
            <Ico.Expand width={14} height={14} />
          </button>
        </div>

        <div className="cp-intel-fit">
          <span className="cp-intel-fit-lbl">Fit Score:</span>
          <span className="cp-intel-fit-val">{confidence}%</span>
        </div>
        <p className="cp-intel-fit-explain">
          {confidence > 70
            ? "Strong fit for your lifestyle — optimized for comfort, daily use, and budget."
            : "Building your profile. Answer a few more questions to sharpen the shortlist."}
        </p>

        <div className="cp-intent-summary">
          <div className="cp-intent-summary-k">Intent Summary</div>
          <div className="cp-intent-summary-grid">
            <div>
              <div className="cp-intent-summary-cell-k">Use-case</div>
              <div className="cp-intent-summary-cell-v">{intent?.primaryUseCase || "—"}</div>
            </div>
            <div>
              <div className="cp-intent-summary-cell-k">Budget</div>
              <div className="cp-intent-summary-cell-v">{intent?.budget ? `₹${intent.budget}` : "—"}</div>
            </div>
          </div>
          {intent?.comfortPriority != null && (
            <div className="cp-intent-summary-priority">
              <div className="cp-intent-summary-priority-k">Current Priority Matrix</div>
              <div className="cp-intent-summary-priority-v">
                Comfort <em>›</em> Style <em>›</em> Durability
              </div>
            </div>
          )}
        </div>

        <div className="cp-fit-bars">
          <div className="cp-fit-bars-k">Explainable fit</div>
          {[
            { l: "Cushioning & Support", v: Math.round((intent?.comfortPriority || 0.7) * 100) },
            { l: "Value-for-Money",      v: 78 },
            { l: "Daily Aesthetics",     v: Math.round((intent?.stylePriority  || 0.5) * 100) },
          ].map((r, i) => (
            <div key={i} className="cp-fitbar">
              <div className="cp-fitbar-head">
                <div className="cp-fitbar-lbl">{r.l}</div>
                <div className="cp-fitbar-val">{fitLabel(r.v)}</div>
              </div>
              <div className="cp-fitbar-track">
                <div className="cp-fitbar-fill" style={{ width: `${r.v}%` }} />
              </div>
            </div>
          ))}
        </div>

        <div className="cp-tradeoff">
          <div className="cp-tradeoff-k">Current tradeoff analysis</div>
          <div className="cp-tradeoff-row">
            <span className="cp-tradeoff-row-icon">+</span>
            Prioritizing high cushioning for walking comfort within budget.
          </div>
          <div className="cp-tradeoff-row">
            <span className="cp-tradeoff-row-icon">−</span>
            May compromise on ultra-lightweight materials or premium leather finishes.
          </div>
        </div>
      </div>
    </section>
  );
}

/* ══════════════════════════════════════════════════════════════════
   STAGE: SHORTLIST
   ══════════════════════════════════════════════════════════════════ */
function StageShortlist() {
  const { recommendations, compareSet, toggleCompare, setPhase, setActiveProduct } = useChatStore();

  function openDeepDive(rec: RecommendationDTO) { setActiveProduct(rec.id); setPhase("deepdive"); }

  const tagSets = [
    ["Top pick", "Comfort lead"],
    ["High Arch Support", "Wide-Fit Available"],
    ["All-Day Comfort", "Long Lifespan"],
  ];

  return (
    <section className="cp-stage">
      <header className="cp-sechead">
        <div>
          <span className="cp-kicker">Curated shortlist · {recommendations.length} of 47</span>
          <h2>Three pairs I'd stand behind.</h2>
          <p>Scored against your weighted priorities — comfort, daily campus wear, within budget. Tap a card for full reasoning.</p>
        </div>
        <div className="cp-sechead-side">
          <button className="cp-btn cp-btn--ghost" onClick={() => setPhase("compare")}>
            <Ico.Expand width={13} height={13} /> Compare all
          </button>
        </div>
      </header>

      <div className="cp-recs">
        {recommendations.map((rec, i) => (
          <article key={rec.id} className="cp-rec" onClick={() => openDeepDive(rec)}>
            <div className="cp-rec-media">
              {rec.productImageUrl && <img src={rec.productImageUrl} alt={rec.productName} referrerPolicy="strict-origin-when-cross-origin" />}
              <span className="cp-rec-fit">{Math.round(rec.matchScore * 100)}% Match</span>
              <div className="cp-rec-rank">{rec.rank}</div>
            </div>
            <div className="cp-rec-body">
              <div>
                <div className="cp-rec-brand">{rec.productBrand}</div>
                <div className="cp-rec-name">{rec.productName}</div>
              </div>
              <p className="cp-rec-tagline">{rec.tagline || rec.reasoning.split(".")[0]}</p>

              <div className="cp-rec-pills">
                {(tagSets[i] || ["Recommended"]).map((t, ti) => (
                  <span key={ti} className={`cp-pill${ti === 1 ? " cp-pill--blue" : ""}`}>{t}</span>
                ))}
              </div>

              <div className="cp-rec-scores">
                {[
                  { l: "Comfort",    v: rec.comfortScore },
                  { l: "Durability", v: rec.durabilityScore },
                  { l: "Style",      v: rec.styleScore },
                ].map((r, ri) => (
                  <div key={ri} className="cp-rec-score">
                    <div className="cp-rec-score-lbl">{r.l}</div>
                    <div className="cp-rec-score-track"><div className="cp-rec-score-fill" style={{ width: `${r.v}%` }} /></div>
                    <div className="cp-rec-score-val">{r.v}/100</div>
                  </div>
                ))}
              </div>

              {rec.regretFlags.length > 0 && <RecFlag flag={rec.regretFlags[0]} />}

              <div className="cp-rec-foot">
                <div className="cp-rec-price"><small>₹</small>{rec.price.toLocaleString("en-IN")}</div>
                <div className="cp-rec-actions" onClick={e => e.stopPropagation()}>
                  <button className="cp-btn cp-btn--ghost cp-btn--sm" onClick={() => toggleCompare(rec.id)}>
                    {compareSet.includes(rec.id) ? "✓ In compare" : "+ Compare"}
                  </button>
                  <button className="cp-btn cp-btn--dark cp-btn--sm" onClick={() => openDeepDive(rec)}>
                    Open <Ico.Arrow width={11} height={11} />
                  </button>
                </div>
              </div>
            </div>
          </article>
        ))}
      </div>

      <div className="cp-trust">
        <div className="cp-trust-num">47</div>
        <div className="cp-trust-body">
          <div>Considered <b>47 pairs</b>. Shortlisted {recommendations.length}.</div>
          <div className="cp-trust-reasons">
            <span>Excluded <b>6</b> · narrow-only sizing</span>
            <span>Excluded <b>4</b> · low durability</span>
            <span>Excluded <b>3</b> · over-budget, no upside</span>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ══════════════════════════════════════════════════════════════════
   STAGE: COMPARE
   ══════════════════════════════════════════════════════════════════ */
function StageCompare() {
  const { recommendations, compareSet, toggleCompare, setPhase, setActiveProduct, intent } = useChatStore();
  const comparing = recommendations.filter(r => compareSet.includes(r.id));

  const winners = useMemo(() => {
    const w: Record<string, number> = {};
    if (!comparing.length) return w;
    const pick = (vals: number[], max: boolean) => vals.indexOf(max ? Math.max(...vals) : Math.min(...vals));
    w.matchScore      = pick(comparing.map(r => r.matchScore), true);
    w.comfortScore    = pick(comparing.map(r => r.comfortScore), true);
    w.durabilityScore = pick(comparing.map(r => r.durabilityScore), true);
    w.styleScore      = pick(comparing.map(r => r.styleScore), true);
    w.price           = pick(comparing.map(r => r.price), false);
    w.weightG         = pick(comparing.map(r => r.weightG ?? 9999), false);
    return w;
  }, [comparing]);

  return (
    <section className="cp-stage">
      <header className="cp-sechead">
        <div>
          <span className="cp-kicker">Head-to-head · {comparing.length} candidates</span>
          <h2>Compare the shortlist, side by side.</h2>
          <p>Aligned on every metric. A small dot marks the winner per row. Differences only matter where your priorities lean.</p>
        </div>
        <div className="cp-sechead-side">
          <button className="cp-btn cp-btn--ghost" onClick={() => setPhase("shortlist")}>
            <Ico.Back width={13} height={13} /> Back
          </button>
        </div>
      </header>

      <div className="cp-compare">
        {comparing.map((rec, i) => (
          <article key={rec.id} className={`cp-cmp${i === winners.matchScore ? " is-hero" : ""}`}>
            <div className="cp-cmp-media">
              {rec.productImageUrl && <img src={rec.productImageUrl} alt={rec.productName} referrerPolicy="strict-origin-when-cross-origin" />}
              <div className="cp-cmp-tags">
                {i === winners.matchScore && <span className="cp-pill">Top match</span>}
                {i === winners.comfortScore && i !== winners.matchScore && <span className="cp-pill cp-pill--blue">Comfort lead</span>}
                {i === winners.durabilityScore && i !== winners.matchScore && i !== winners.comfortScore && <span className="cp-pill cp-pill--sage">Durability lead</span>}
              </div>
              {comparing.length > 2 && (
                <button className="cp-cmp-x" onClick={() => toggleCompare(rec.id)}><Ico.X width={12} height={12} /></button>
              )}
            </div>

            <div className="cp-cmp-body">
              <div>
                <div className="cp-cmp-brand">{rec.productBrand}</div>
                <div className="cp-cmp-name">{rec.productName}</div>
              </div>

              <div className="cp-cmp-table">
                {[
                  { k: "Match",      v: `${Math.round(rec.matchScore * 100)}`, suffix: "%",    wk: "matchScore" },
                  { k: "Price",      v: `₹${rec.price.toLocaleString("en-IN")}`, suffix: "", wk: "price" },
                  { k: "Comfort",    v: rec.comfortScore,    suffix: "/100", wk: "comfortScore" },
                  { k: "Durability", v: rec.durabilityScore, suffix: "/100", wk: "durabilityScore" },
                  { k: "Style",      v: rec.styleScore,      suffix: "/100", wk: "styleScore" },
                  ...(rec.weightG ? [{ k: "Weight", v: rec.weightG, suffix: "g", wk: "weightG" }] : []),
                  ...(rec.dropMm  ? [{ k: "Drop",   v: rec.dropMm,  suffix: "mm", wk: "" }] : []),
                  ...(rec.fitNote ? [{ k: "Fit",    v: rec.fitNote.split(".")[0], suffix: "", wk: "" }] : []),
                  { k: "Best at", v: rec.merchantOffers[0]?.merchantName || "—", suffix: "", wk: "" },
                ].map((row, ri) => (
                  <div key={ri} className="cp-cmp-table-row">
                    <div className="cp-cmp-table-k">{row.k}</div>
                    <div className="cp-cmp-table-v">
                      {row.v}{row.suffix && <small>{row.suffix}</small>}
                      {row.wk && winners[row.wk] === i && <span className="cp-cmp-win" />}
                    </div>
                  </div>
                ))}
              </div>

              {rec.regretFlags.length > 0
                ? <RecFlag flag={rec.regretFlags[0]} />
                : <div className="cp-cmp-noflag"><Ico.Check width={12} height={12} /> No flags raised</div>
              }

              <button className="cp-btn cp-btn--dark" onClick={() => { setActiveProduct(rec.id); setPhase("deepdive"); }}>
                Pick this <Ico.Arrow width={13} height={13} />
              </button>
            </div>
          </article>
        ))}

        {comparing.length < 3 && (
          <button className="cp-cmp-add" onClick={() => setPhase("shortlist")}>
            <Ico.Plus width={20} height={20} /> <span>Add another</span>
          </button>
        )}
      </div>

      <div className="cp-cmp-verdict">
        <span className="cp-kicker">My call</span>
        <h4>Stretch ₹1,000 — or stay safe.</h4>
        <p>
          If you can flex on budget, the <b>Fresh Foam X 1080v14</b> is the comfort leader and the only one in genuine wide width.
          If budget is firm, the <b>Pegasus 41</b> is the safest all-rounder at ₹10,799.
          {intent?.comfortPriority && intent.comfortPriority > 0.7 && " Comfort weighted — leans toward the cushion leader."}
        </p>
        <div className="cp-cmp-verdict-foot">
          {intent?.comfortPriority && <span className="cp-pill cp-pill--ghost">Comfort · {Math.round(intent.comfortPriority * 100)}%</span>}
          {intent?.budget && <span className="cp-pill cp-pill--ghost">Budget · ₹{intent.budget.toLocaleString("en-IN")}</span>}
          <span className="cp-pill cp-pill--ghost">Style · secondary</span>
        </div>
      </div>
    </section>
  );
}

/* ══════════════════════════════════════════════════════════════════
   STAGE: DEEP DIVE / ARCHIVE
   ══════════════════════════════════════════════════════════════════ */
function StageDeepDive() {
  const { recommendations, activeProductId, setActiveProduct, setPhase, messages } = useChatStore();
  const [askInput, setAskInput] = useState("");
  const activeRec = recommendations.find(r => r.id === activeProductId) || recommendations[0];
  if (!activeRec) return null;

  const userMsgCount = messages.filter(m => m.role === "user").length;

  const drivers = [
    { l: "Comfort Match",    v: `+${Math.round(activeRec.comfortScore * 0.4)}` },
    { l: "Walking Support",  v: `+${Math.round(activeRec.matchScore * 28)}` },
    { l: "Budget Alignment", v: activeRec.price <= 12000 ? "+20" : "+10" },
    { l: "Style Preference", v: `+${Math.round(activeRec.styleScore * 0.18)}` },
  ];

  const heroPills = [
    { l: "All-Day Comfort", c: "" },
    { l: activeRec.rank === 1 ? "Daily Trainer" : activeRec.rank === 2 ? "Wide-Fit Available" : "Long Lifespan", c: "blue" },
  ];

  return (
    <section className="cp-stage">
      <button className="cp-deep-back" onClick={() => setPhase("shortlist")}>
        <Ico.Back width={13} height={13} /> Back to shortlist
      </button>

      {/* Shoe tab strip */}
      <div className="cp-deep-tabs">
        {recommendations.map(r => (
          <button
            key={r.id}
            onClick={() => setActiveProduct(r.id)}
            className={`cp-pill${r.id === activeProductId ? "" : " cp-pill--ghost"}`}
            style={{ cursor: "pointer", padding: "8px 14px" }}>
            {r.productBrand} · {r.productName.split(" ").slice(-2).join(" ")} — {Math.round(r.matchScore * 100)}%
          </button>
        ))}
      </div>

      <div className="cp-deep">
        {/* Main column */}
        <div>
          <div className="cp-deep-hero">
            <div className="cp-deep-media">
              {activeRec.productImageUrl && <img src={activeRec.productImageUrl} alt={activeRec.productName} referrerPolicy="strict-origin-when-cross-origin" />}
              <span className="cp-deep-media-fit">{Math.round(activeRec.matchScore * 100)}% Match</span>
            </div>

            <div className="cp-deep-info">
              <div className="cp-deep-info-head">
                <span className="cp-kicker">{activeRec.productBrand} · Premium daily wear</span>
                <h1 className="cp-deep-name">{activeRec.productName}</h1>
                <div className="cp-deep-sub">{(activeRec.tagline || activeRec.reasoning).split(".")[0]}</div>
              </div>

              <div className="cp-deep-pills">
                {heroPills.map((p, i) => (
                  <span key={i} className={`cp-pill${p.c ? ` cp-pill--${p.c}` : ""}`}>{p.l}</span>
                ))}
              </div>

              <div className="cp-deep-section">
                <h4><span className="cp-glyph">◐</span> Why this works</h4>
                <p>{activeRec.reasoning}</p>
              </div>

              {activeRec.tradeoffs && (
                <div className="cp-deep-section">
                  <h4><span className="cp-glyph">⇄</span> Tradeoff</h4>
                  <p>{activeRec.tradeoffs}</p>
                  {activeRec.notSuitableFor && <a className="cp-why-link" href="#">Why not this? — {activeRec.notSuitableFor}</a>}
                </div>
              )}
            </div>
          </div>

          {/* Retailer table */}
          <div className="cp-retail">
            <h3>Retailer Availability</h3>
            <div className="cp-retail-table">
              <div className="cp-retail-row cp-retail-head">
                <div>Merchant</div>
                <div>Price</div>
                <div>Delivery</div>
                <div>Returns</div>
              </div>
              {activeRec.merchantOffers.map((o, i) => (
                <div key={i} className={`cp-retail-row${o.bestValue ? " is-best" : ""}`}>
                  <div>
                    <div className="cp-retail-name">{o.merchantName}</div>
                    {o.bestValue && (
                      <span className="cp-retail-best-pill">
                        <Ico.Leaf width={10} height={10} /> Recommended · {o.whyRecommended.split(".")[0]}
                      </span>
                    )}
                  </div>
                  <div className="cp-retail-price">₹{o.price.toLocaleString("en-IN")}</div>
                  <div className="cp-retail-cell">{o.deliveryEstimate}</div>
                  <div className="cp-retail-cell">{o.returnPolicy}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="cp-deep-cta">
            <button className="cp-btn cp-btn--ghost" onClick={() => setPhase("compare")}>Compare again</button>
            <a href={activeRec.merchantOffers[0]?.checkoutUrl || "#"} target="_blank" rel="noopener noreferrer" className="cp-btn cp-btn--dark cp-btn--lg">
              Checkout · ₹{(activeRec.merchantOffers[0]?.price || activeRec.price).toLocaleString("en-IN")} <Ico.Arrow width={14} height={14} />
            </a>
          </div>
        </div>

        {/* Right rail */}
        <aside className="cp-deep-rail">
          <div className="cp-rail-card">
            <h4><span className="cp-rail-dot" /> Fit Score</h4>
            <div className="cp-rail-fitnum">{Math.round(activeRec.matchScore * 100)}%</div>
            <div className="cp-rail-fithint">Based on {userMsgCount} signal{userMsgCount !== 1 ? "s" : ""} from you</div>
          </div>

          <div className="cp-rail-card">
            <span className="cp-kicker" style={{ display: "block", marginBottom: 8 }}>Match Drivers</span>
            <div className="cp-drivers">
              {drivers.map((d, i) => (
                <div key={i} className="cp-driver">
                  <span className="cp-driver-lbl">{d.l}</span>
                  <span className="cp-driver-val">{d.v}</span>
                </div>
              ))}
            </div>

            {activeRec.fitNote && (
              <div className="cp-note">
                <h5>Fit Note</h5>
                <p>{activeRec.fitNote}</p>
              </div>
            )}

            {activeRec.regretFlags.length > 0 && (
              <div className="cp-note">
                <h5>Safeguard</h5>
                <p>{activeRec.regretFlags[0].title} — {activeRec.regretFlags[0].description}</p>
              </div>
            )}
          </div>

          <div className="cp-ask">
            <input
              placeholder="Ask a follow-up…"
              value={askInput}
              onChange={e => setAskInput(e.target.value)}
            />
            <button className="cp-ask-send">
              <Ico.Send width={13} height={13} />
            </button>
          </div>
        </aside>
      </div>
    </section>
  );
}

/* ══════════════════════════════════════════════════════════════════
   STAGE: ARCHIVE — persisted recommendations across all sessions
   ══════════════════════════════════════════════════════════════════ */
function StageArchive() {
  const { archive, setActiveProduct, setPhase } = useChatStore();

  if (archive.length === 0) {
    return (
      <section className="cp-stage" style={{ textAlign: "center", paddingTop: 80 }}>
        <div style={{ color: "var(--cp-mute)", fontSize: 14, lineHeight: 1.7 }}>
          <div style={{ fontSize: 32, marginBottom: 16 }}>📭</div>
          No saved recommendations yet.<br />
          Complete a discovery session and your shortlisted shoes will appear here.
        </div>
      </section>
    );
  }

  // Group by date
  const grouped = archive.reduce<Record<string, typeof archive>>((acc, rec) => {
    const day = rec.createdAt ? new Date(rec.createdAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "Unknown date";
    (acc[day] ??= []).push(rec);
    return acc;
  }, {});

  return (
    <section className="cp-stage">
      <header className="cp-sechead">
        <div>
          <span className="cp-kicker">Saved recommendations · {archive.length} shoe{archive.length !== 1 ? "s" : ""}</span>
          <h2>Your recommendation history.</h2>
          <p>Every shortlist ShopMind built for you, preserved across sessions.</p>
        </div>
      </header>

      {Object.entries(grouped).map(([date, recs]) => (
        <div key={date} style={{ marginBottom: 32 }}>
          <div className="cp-kicker" style={{ marginBottom: 14, paddingBottom: 8, borderBottom: "1px solid var(--cp-line)" }}>{date}</div>
          <div className="cp-recs">
            {recs.map(rec => (
              <article key={rec.id} className="cp-rec" onClick={() => { setActiveProduct(rec.id); setPhase("deepdive"); }}>
                <div className="cp-rec-media">
                  {rec.productImageUrl && <img src={rec.productImageUrl} alt={rec.productName} referrerPolicy="strict-origin-when-cross-origin" />}
                  <span className="cp-rec-fit">{Math.round(rec.matchScore * 100)}% Match</span>
                  <div className="cp-rec-rank">{rec.rank}</div>
                </div>
                <div className="cp-rec-body">
                  <div>
                    <div className="cp-rec-brand">{rec.productBrand}</div>
                    <div className="cp-rec-name">{rec.productName}</div>
                  </div>
                  <p className="cp-rec-tagline">{rec.tagline || rec.reasoning.split(".")[0]}</p>
                  <div className="cp-rec-scores">
                    {[
                      { l: "Comfort",    v: rec.comfortScore },
                      { l: "Durability", v: rec.durabilityScore },
                      { l: "Style",      v: rec.styleScore },
                    ].map((r, i) => (
                      <div key={i} className="cp-rec-score">
                        <div className="cp-rec-score-lbl">{r.l}</div>
                        <div className="cp-rec-score-track"><div className="cp-rec-score-fill" style={{ width: `${r.v}%` }} /></div>
                        <div className="cp-rec-score-val">{r.v}/100</div>
                      </div>
                    ))}
                  </div>
                  <div className="cp-rec-foot">
                    <div className="cp-rec-price"><small>₹</small>{rec.price.toLocaleString("en-IN")}</div>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </div>
      ))}
    </section>
  );
}

/* ══════════════════════════════════════════════════════════════════
   MAIN CHAT PAGE
   ══════════════════════════════════════════════════════════════════ */
function BackendDownScreen() {
  const navigate = useNavigate();
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", minHeight: "60vh", textAlign: "center", padding: "40px var(--cp-pad)" }}>
      <div style={{ fontSize: 64, lineHeight: 1, marginBottom: 24, opacity: 0.25 }}>503</div>
      <h2 style={{ fontFamily: "var(--cp-serif)", fontSize: "clamp(24px,3vw,36px)", fontWeight: 400, marginBottom: 12 }}>Backend not reachable</h2>
      <p style={{ color: "var(--cp-mute)", fontSize: 15, maxWidth: 420, lineHeight: 1.6, marginBottom: 32 }}>
        ShopMind couldn't connect to the server. Make sure the backend is running on{" "}
        <code style={{ background: "var(--cp-panel)", padding: "2px 6px", borderRadius: 4, fontSize: 13 }}>
          localhost:8080
        </code>{" "}
        and try again.
      </p>
      <div style={{ display: "flex", gap: 12 }}>
        <button className="cp-btn cp-btn--dark" onClick={() => window.location.reload()}>
          Retry
        </button>
        <button className="cp-btn cp-btn--ghost" onClick={() => navigate("/")}>
          Go home
        </button>
      </div>
    </div>
  );
}

export default function ChatPage() {
  const { phase, setPhase, recommendations, initSession, loadArchive, send, backendDown } = useChatStore();
  const location = useLocation();
  const initialized = useRef(false);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;
    const query = (location.state as { query?: string } | null)?.query?.trim();
    initSession().then(() => {
      if (query) {
        send(query);
        // clear state so a back-navigate doesn't re-fire it
        window.history.replaceState({}, "");
      }
    });
  }, []);

  const activeTab: NavTab = (phase === "deepdive" || phase === "archive") ? "Archive" : "Intelligence";

  function handleTab(tab: NavTab) {
    if (tab === "Curations") { window.location.href = "/"; return; }
    if (tab === "Intelligence") {
      if (recommendations.length > 0) setPhase("shortlist");
      else setPhase("discovery");
    }
    if (tab === "Archive") {
      loadArchive();
      setPhase("archive");
    }
  }

  const Stage = () => {
    if (phase === "archive")                                    return <StageArchive />;
    if (phase === "compare"  && recommendations.length > 0)     return <StageCompare />;
    if (phase === "deepdive" && recommendations.length > 0)     return <StageDeepDive />;
    if (phase === "shortlist" && recommendations.length > 0)    return <StageShortlist />;
    return <StageDiscovery onShortlist={() => recommendations.length > 0 ? setPhase("shortlist") : undefined} />;
  };

  return (
    <div className="cp-app">
      <TopNav activeTab={activeTab} onTab={handleTab} />
      <main className="cp-main">
        {backendDown ? <BackendDownScreen /> : <Stage />}
      </main>
    </div>
  );
}
