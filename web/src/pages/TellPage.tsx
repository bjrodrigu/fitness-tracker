import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, nutrition, parser } from "../lib/api";
import { useUser } from "../lib/auth";
import { addDays, mealName, todayIso } from "../lib/food";
import { formatDate, formatDuration, parseDuration } from "../lib/format";
import { MEALS, type Activity, type Draft, type FoodResult, type IngredientDraft, type Meal } from "../lib/types";

/** A draft on screen: the server's draft plus what the person changed, and which match each ingredient uses. */
interface Card {
  key: number;
  draft: Draft;
  activity: Activity | null;
  dish: string;
  meal: Meal;
  ingredients: (IngredientDraft & { pick: number; query: string; searching?: boolean })[];
}

const PLACEHOLDER = "Easy 4 miles this morning, legs felt heavy. Then bench 3x8 at 185. Lunch was a turkey sandwich.";

const EXAMPLES = [
  { label: "A run and a lift", text: "Easy 4 miles this morning in 38 min, legs felt heavy. Then bench 3x8 at 185 and 3 sets of 10 pull ups." },
  { label: "Fixing something already logged", text: "Actually the last bench set was only 6 reps." },
  { label: "A meal you cooked", text: "Made a chicken burrito bowl for dinner with rice, black beans, chicken, cheese and salsa." },
];

/** "Ollama qwen2.5:3b" -> where messages go, in words. */
function modelLine(model: string) {
  if (model.startsWith("Ollama ")) return `Messages are read by ${model.slice(7)}, a small model running on the computer that hosts this journal. They don't leave it.`;
  if (model.startsWith("Claude ")) return `Messages are read by Claude (${model.slice(7)}) through the Anthropic API.`;
  return null;
}

function macros(ing: Card["ingredients"][number]) {
  const f: FoodResult | undefined = ing.options[ing.pick];
  if (!f) return { kcal: 0, p: 0, c: 0, fat: 0 };
  const k = ing.grams / 100;
  return { kcal: f.per100g.kcal * k, p: f.per100g.proteinG * k, c: f.per100g.carbsG * k, fat: f.per100g.fatG * k };
}

function totals(card: Card) {
  return card.ingredients.reduce(
    (t, i) => {
      const m = macros(i);
      return { kcal: t.kcal + m.kcal, p: t.p + m.p, c: t.c + m.c, fat: t.fat + m.fat };
    },
    { kcal: 0, p: 0, c: 0, fat: 0 },
  );
}

const r1 = (n: number) => Math.round(n * 10) / 10;

function dayLabel(iso: string) {
  const today = todayIso();
  if (iso === today) return "today";
  if (iso === addDays(today, -1)) return "yesterday";
  return formatDate(iso);
}

export default function TellPage() {
  const user = useUser();
  const [message, setMessage] = useState("");
  const [cards, setCards] = useState<Card[]>([]);
  const [notes, setNotes] = useState<string[]>([]);
  const [reading, setReading] = useState(false);
  const [slow, setSlow] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<{ count: number; food: boolean; training: boolean } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [model, setModel] = useState<string | null>(null);
  const box = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    parser.status().then((s) => { setEnabled(s.enabled); setModel(s.model); }).catch(() => setEnabled(null));
  }, []);

  useEffect(() => {
    if (!reading) return;
    const t = setTimeout(() => setSlow(true), 6000);
    return () => clearTimeout(t);
  }, [reading]);

  const read = async (e?: FormEvent) => {
    e?.preventDefault();
    const text = message.trim();
    if (!text || reading) return;
    setReading(true); setSlow(false); setError(null); setSaved(null);
    try {
      const now = new Date();
      const res = await parser.read(text, todayIso(), now.getHours());
      let k = 0;
      setCards(res.drafts.map((d) => ({
        key: k++,
        draft: d,
        activity: d.activity,
        dish: d.food?.name ?? "",
        meal: d.food?.meal ?? "SNACK",
        ingredients: (d.food?.ingredients ?? []).map((i) => ({ ...i, pick: 0, query: i.said })),
      })));
      setNotes(res.notes);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setReading(false);
    }
  };

  const update = (key: number, patch: Partial<Card>) =>
    setCards((cs) => cs.map((c) => (c.key === key ? { ...c, ...patch } : c)));
  const setActivity = (key: number, patch: Partial<Activity>) =>
    setCards((cs) => cs.map((c) => (c.key === key && c.activity ? { ...c, activity: { ...c.activity, ...patch } } : c)));
  const setIngredient = (key: number, i: number, patch: Partial<Card["ingredients"][number]>) =>
    setCards((cs) => cs.map((c) => (c.key === key
      ? { ...c, ingredients: c.ingredients.map((x, j) => (j === i ? { ...x, ...patch } : x)) } : c)));

  const findAgain = async (key: number, i: number, q: string) => {
    if (q.trim().length < 2) return;
    setIngredient(key, i, { searching: true });
    try {
      const res = await nutrition.search(q.trim());
      setIngredient(key, i, { options: res.foods.slice(0, 5), pick: 0, searching: false });
    } catch {
      setIngredient(key, i, { searching: false });
    }
  };

  const saveAll = async () => {
    setSaving(true); setError(null);
    let count = 0;
    let food = false;
    let training = false;
    try {
      for (const c of cards) {
        if (c.draft.kind === "FOOD") {
          const t = totals(c);
          const parts = c.ingredients.filter((i) => i.options[i.pick]).map((i) => i.said);
          await nutrition.add({
            date: c.draft.date, meal: c.meal, name: c.dish.trim() || "Meal", servings: 1,
            servingLabel: parts.join(", ").slice(0, 60) || null,
            calories: Math.min(5000, Math.round(t.kcal)), proteinG: r1(t.p), carbsG: r1(t.c), fatG: r1(t.fat),
          } as never);
          food = true;
        } else if (c.activity) {
          if (c.activity.id) await api.update(c.activity.id, c.activity);
          else await api.create(c.activity);
          training = true;
        }
        count++;
        setCards((cs) => cs.filter((x) => x.key !== c.key));
      }
      setSaved({ count, food, training });
      setMessage(""); setNotes([]);
    } catch (err) {
      setError(`Saved ${count} of ${cards.length}. ${(err as Error).message}`);
    } finally {
      setSaving(false);
    }
  };

  const startOver = () => {
    setCards([]); setNotes([]); setError(null); setSaved(null);
    box.current?.focus();
  };

  return (
    <div className="tell">
      <header className="page-head">
        <div>
          <h1>Describe it</h1>
          <p>Write what you did or ate, the way you'd text a friend. You check everything before it's saved.</p>
        </div>
      </header>

      {enabled === false && (
        <p className="panel tell-off">Describing entries in words is turned off on this server. You can still log them the usual way.</p>
      )}

      <form className="tell-form" onSubmit={read}>
        <label className="visually-hidden" htmlFor="tell-message">What did you do or eat?</label>
        <textarea
          id="tell-message"
          ref={box}
          className="field"
          rows={3}
          maxLength={1000}
          placeholder={PLACEHOLDER}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) read();
          }}
          autoFocus
        />
        <div className="tell-actions">
          <button type="submit" className="btn primary" disabled={reading || !message.trim()}>
            {reading ? "Reading…" : "Read it"}
          </button>
          {reading && (
            <span className="muted small" role="status">
              {slow ? "Still reading. The first message after a quiet spell, or on a busy computer, can take up to a minute." : "Reading your message…"}
            </span>
          )}
        </div>
      </form>

      {error && <p className="form-error" role="alert">{error}</p>}

      {saved && (
        <p className="panel tell-saved" role="status">
          Saved {saved.count === 1 ? "1 entry" : `${saved.count} entries`}.{" "}
          {saved.training && <Link to="/journal">See the journal</Link>}
          {saved.training && saved.food && " · "}
          {saved.food && <Link to="/food">See today's food</Link>}
        </p>
      )}

      {notes.length > 0 && (
        <ul className="tell-notes">{notes.map((n) => <li key={n}>{n}</li>)}</ul>
      )}

      {cards.length === 0 && !reading && !saved && !message.trim() && enabled !== false && (
        <section className="tell-examples" aria-label="Examples">
          <h2>Things it understands</h2>
          <ul>
            {EXAMPLES.map((x) => (
              <li key={x.label}>
                <button type="button" onClick={() => { setMessage(x.text); box.current?.focus(); }}>
                  <b>{x.label}</b>
                  {x.text}
                </button>
              </li>
            ))}
          </ul>
          {model && modelLine(model) && <p className="muted tell-model">{modelLine(model)}</p>}
        </section>
      )}

      {cards.length > 0 && (
        <section className="tell-cards" aria-label="Drafts to check">
          {cards.map((c) => (
            <article key={c.key} className={`tell-card ${c.draft.kind.toLowerCase()}`}>
              <header>
                <span className={`mark ${c.draft.kind === "RUN" ? "run" : c.draft.kind === "LIFT" ? "strength" : "food"}`} />
                <h2>{heading(c)}</h2>
                <button type="button" className="btn ghost small" onClick={() => setCards((cs) => cs.filter((x) => x.key !== c.key))}>
                  Remove
                </button>
              </header>

              {c.draft.kind !== "FOOD" && (
                <ul className="tell-changes">{c.draft.changes.map((ch) => <li key={ch}>{ch}</li>)}</ul>
              )}

              {c.draft.kind === "RUN" && c.activity && (
                <div className="ff-grid">
                  <label className="ff">
                    <span>Miles</span>
                    <input className="field" inputMode="decimal" defaultValue={c.activity.distanceMi ?? ""}
                      onChange={(e) => setActivity(c.key, { distanceMi: Number(e.target.value) > 0 ? Number(e.target.value) : null })} />
                  </label>
                  <label className="ff">
                    <span>Time <small>h:mm:ss</small></span>
                    <input className="field" inputMode="numeric" defaultValue={c.activity.durationSec ? formatDuration(c.activity.durationSec) : ""}
                      onChange={(e) => setActivity(c.key, { durationSec: parseDuration(e.target.value) })} />
                  </label>
                  <label className="ff wide">
                    <span>Notes</span>
                    <input className="field" defaultValue={c.activity.notes ?? ""}
                      onChange={(e) => setActivity(c.key, { notes: e.target.value || null })} />
                  </label>
                </div>
              )}

              {c.draft.kind === "LIFT" && c.activity && (
                <div className="tell-lifts">
                  {c.activity.exercises.map((ex, i) => (
                    <div key={i} className="tell-exercise">
                      <b>{ex.name}</b>
                      <div className="tell-sets">
                        {ex.sets.map((s, k) => {
                          const setAt = (patch: Partial<typeof s>) => setActivity(c.key, {
                            exercises: c.activity!.exercises.map((x, j) => (j !== i ? x
                              : { ...x, sets: x.sets.map((y, m) => (m === k ? { ...y, ...patch } : y)) })),
                          });
                          return (
                            <span key={k} className="tell-set">
                              <input className="field" inputMode="numeric" aria-label={`${ex.name} set ${k + 1} reps`}
                                value={s.reps} onChange={(e) => setAt({ reps: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
                              <span aria-hidden="true">×</span>
                              <input className="field" inputMode="decimal" aria-label={`${ex.name} set ${k + 1} weight in pounds`}
                                value={s.weightLb || ""} placeholder="BW"
                                onChange={(e) => setAt({ weightLb: Math.max(0, Number(e.target.value) || 0) })} />
                            </span>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {c.draft.kind === "FOOD" && (
                <>
                  <div className="ff-grid">
                    <label className="ff wide">
                      <span>Name</span>
                      <input className="field" value={c.dish} onChange={(e) => update(c.key, { dish: e.target.value })} />
                    </label>
                    <label className="ff">
                      <span>Meal</span>
                      <select className="field" value={c.meal} onChange={(e) => update(c.key, { meal: e.target.value as Meal })}>
                        {MEALS.map((m) => <option key={m} value={m}>{mealName(m)}</option>)}
                      </select>
                    </label>
                  </div>
                  <ul className="tell-ingredients">
                    {c.ingredients.map((ing, i) => {
                      const m = macros(ing);
                      return (
                        <li key={i}>
                          <div className="tell-ing-top">
                            <input className="field" aria-label="Ingredient" value={ing.query}
                              onChange={(e) => setIngredient(c.key, i, { query: e.target.value })}
                              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); findAgain(c.key, i, ing.query); } }} />
                            <label className="tell-grams">
                              <input className="field" inputMode="decimal" aria-label={`Grams of ${ing.said}`} value={ing.grams || ""}
                                onChange={(e) => setIngredient(c.key, i, { grams: Math.max(0, Number(e.target.value) || 0), gramsGuessed: false })} />
                              <span>g</span>
                            </label>
                            <button type="button" className="btn ghost small" aria-label={`Remove ${ing.said}`}
                              onClick={() => update(c.key, { ingredients: c.ingredients.filter((_, j) => j !== i) })}>✕</button>
                          </div>
                          {ing.options.length > 0 ? (
                            <div className="tell-ing-match">
                              <select className="field" aria-label={`Match for ${ing.said}`} value={ing.pick}
                                onChange={(e) => setIngredient(c.key, i, { pick: Number(e.target.value) })}>
                                {ing.options.map((o, j) => (
                                  <option key={o.id + j} value={j}>{o.name}{o.brand ? `, ${o.brand}` : ""}</option>
                                ))}
                              </select>
                              <span className="num">{Math.round(m.kcal)} kcal</span>
                            </div>
                          ) : (
                            <div className="tell-ing-match missing">
                              <span>{ing.searching ? "Searching…" : "Not found. Change the name and search again, or remove it."}</span>
                              <button type="button" className="btn small" onClick={() => findAgain(c.key, i, ing.query)}>Search</button>
                            </div>
                          )}
                          {ing.gramsGuessed && ing.options.length > 0 && (
                            <small className="muted">Grams are a guess{ing.amount ? ` for “${ing.amount}”` : ""}. Check them.</small>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                  <FoodTotal card={c} />
                </>
              )}
            </article>
          ))}

          <div className="tell-save">
            {user.demo ? (
              <p className="muted">The demo can't save. <Link to="/settings">Make your own journal</Link> to keep entries.</p>
            ) : (
              <button type="button" className="btn primary" onClick={saveAll} disabled={saving}>
                {saving ? "Saving…" : cards.length === 1 ? "Save it" : `Save all ${cards.length}`}
              </button>
            )}
            <button type="button" className="btn ghost" onClick={startOver} disabled={saving}>Start over</button>
          </div>
        </section>
      )}
    </div>
  );
}

function heading(c: Card): string {
  const day = dayLabel(c.draft.date);
  if (c.draft.kind === "FOOD") return `${mealName(c.meal)}, ${day}`;
  if (c.draft.action === "UPDATE") return `Changes ${c.activity?.title ?? "an entry"}, ${day}`;
  return `${c.draft.kind === "RUN" ? "New run" : "New lifting session"}, ${day}`;
}

function FoodTotal({ card }: { card: Card }) {
  const t = totals(card);
  return (
    <p className="tell-total">
      <b>{Math.round(t.kcal)} kcal</b>
      <span><i className="macro-dot p" />{Math.round(t.p)} g protein</span>
      <span><i className="macro-dot c" />{Math.round(t.c)} g carbs</span>
      <span><i className="macro-dot f" />{Math.round(t.fat)} g fat</span>
    </p>
  );
}
