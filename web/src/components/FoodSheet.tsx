import { useCallback, useEffect, useState, type FormEvent } from "react";
import { nutrition } from "../lib/api";
import { MEALS, type FoodEntry, type FoodResult, type FoodSearchResult, type Meal } from "../lib/types";
import { mealName } from "../lib/food";
import BarcodeScanner from "./BarcodeScanner";

interface Props {
  date: string;
  meal: Meal;
  /** Editing an existing entry; otherwise adding. */
  entry?: FoodEntry;
  onClose(): void;
  onSaved(): void;
}

type Fields = { name: string; servings: string; calories: string; proteinG: string; carbsG: string; fatG: string };

const toFields = (f?: Partial<FoodEntry>): Fields => ({
  name: f?.name ?? "",
  servings: f?.servings != null ? String(f.servings) : "1",
  calories: f?.calories != null ? String(f.calories) : "",
  proteinG: f?.proteinG ? String(f.proteinG) : "",
  carbsG: f?.carbsG ? String(f.carbsG) : "",
  fatG: f?.fatG ? String(f.fatG) : "",
});

const num = (s: string) => (s.trim() === "" ? 0 : Number(s));
const r1 = (n: number) => String(Math.round(n * 10) / 10);

/** "g" means the amount is typed in grams; otherwise it's an index into the food's portions. */
type Unit = number | "100g" | "g";

/** Add or edit one food: find it (search, barcode, recent), then check the amount. */
export default function FoodSheet({ date, meal: initialMeal, entry, onClose, onSaved }: Props) {
  const [step, setStep] = useState<"find" | "details">(entry ? "details" : "find");
  const [meal, setMeal] = useState<Meal>(entry?.meal ?? initialMeal);
  const [f, setF] = useState<Fields>(toFields(entry));
  const [servingLabel, setServingLabel] = useState<string | null>(entry?.servingLabel ?? null);

  // Picked from a database: amounts are worked out from per-100 g values.
  const [db, setDb] = useState<FoodResult | null>(null);
  const [unit, setUnit] = useState<Unit>("100g");
  const [amount, setAmount] = useState("1");

  const [query, setQuery] = useState("");
  const [results, setResults] = useState<FoodSearchResult | null>(null);
  const [searching, setSearching] = useState(false);
  const [recent, setRecent] = useState<FoodEntry[]>([]);
  const [scanning, setScanning] = useState(false);
  const [lookup, setLookup] = useState<string | null>(null);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!entry) nutrition.recent().then(setRecent, () => undefined);
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !scanning && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [entry, onClose, scanning]);

  // Search as you type, after a short pause.
  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setResults(null);
      return;
    }
    const ctl = new AbortController();
    const t = window.setTimeout(() => {
      setSearching(true);
      nutrition.search(q, ctl.signal)
        .then(setResults, (e: Error) => e.name !== "AbortError" && setResults({ foods: [], unavailable: ["search"] }))
        .finally(() => setSearching(false));
    }, 400);
    return () => {
      window.clearTimeout(t);
      ctl.abort();
    };
  }, [query]);

  /** Fill the form from a database food at the chosen amount. */
  const apply = (food: FoodResult, u: Unit, amt: string) => {
    const a = num(amt);
    const grams = u === "g" ? a : u === "100g" ? 100 : food.portions[u].grams;
    const per = (v: number) => (v * grams) / 100;
    setF({
      name: food.brand ? `${food.name} (${food.brand})` : food.name,
      servings: u === "g" ? "1" : amt,
      calories: String(Math.round(per(food.per100g.kcal))),
      proteinG: r1(per(food.per100g.proteinG)),
      carbsG: r1(per(food.per100g.carbsG)),
      fatG: r1(per(food.per100g.fatG)),
    });
    setServingLabel(u === "g" ? `${Math.round(a)} g` : u === "100g" ? "100 g" : food.portions[u].label);
  };

  const pick = (food: FoodResult) => {
    const u: Unit = food.portions.length ? 0 : "100g";
    setDb(food);
    setUnit(u);
    setAmount("1");
    apply(food, u, "1");
    setError(null);
    setStep("details");
  };

  const pickRecent = (r: FoodEntry) => {
    setDb(null);
    setF(toFields(r));
    setServingLabel(r.servingLabel ?? null);
    setStep("details");
  };

  const manual = () => {
    setDb(null);
    setF(toFields({ name: query.trim() }));
    setServingLabel(null);
    setStep("details");
  };

  const onCode = useCallback(async (code: string) => {
    setScanning(false);
    setLookup(code);
    setError(null);
    try {
      pick(await nutrition.barcode(code));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLookup(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const set = (k: keyof Fields) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  const total = num(f.servings) * num(f.calories);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    const body: FoodEntry = {
      date: entry?.date ?? date,
      meal,
      name: f.name.trim(),
      servings: num(f.servings),
      servingLabel,
      calories: Math.round(num(f.calories)),
      proteinG: num(f.proteinG),
      carbsG: num(f.carbsG),
      fatG: num(f.fatG),
    };
    if (!body.name) return setError("Add a name.");
    if (!(body.servings > 0)) return setError("The amount has to be more than 0.");
    if ([body.calories, body.proteinG, body.carbsG, body.fatG].some((v) => Number.isNaN(v) || v < 0)) {
      return setError("Use numbers of 0 or more.");
    }
    setBusy(true);
    setError(null);
    try {
      if (entry?.id) await nutrition.update(entry.id, body);
      else await nutrition.add(body);
      onSaved();
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!entry?.id) return;
    setBusy(true);
    try {
      await nutrition.remove(entry.id);
      onSaved();
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  };

  const recentMatches = recent.filter((r) => !query.trim() || r.name.toLowerCase().includes(query.trim().toLowerCase()));

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <form className="sheet food-sheet" role="dialog" aria-modal="true" aria-label={entry ? "Edit food" : "Add food"}
        onClick={(e) => e.stopPropagation()} onSubmit={save}>
        <div className="sheet-grip" aria-hidden="true" />
        <div className="food-sheet-head">
          {step === "details" && !entry ? (
            <button type="button" className="btn ghost back" onClick={() => setStep("find")}>‹ Search</button>
          ) : (
            <h2>{entry ? "Edit food" : "Add food"}</h2>
          )}
          <select className="field meal-select" value={meal} onChange={(e) => setMeal(e.target.value as Meal)}
            aria-label="Meal">
            {MEALS.map((m) => <option key={m} value={m}>{mealName(m)}</option>)}
          </select>
        </div>

        {step === "find" ? (
          <>
            <div className="search-row">
              <input className="field" type="search" value={query} onChange={(e) => setQuery(e.target.value)}
                placeholder="Search foods" aria-label="Search foods" autoComplete="off" enterKeyHint="search"
                onKeyDown={(e) => e.key === "Enter" && e.preventDefault()} />
              <button type="button" className="btn scan-btn" onClick={() => setScanning(true)} aria-label="Scan a barcode">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
                  strokeLinecap="round" aria-hidden="true">
                  <path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 8v8M10 8v8M13 8v8M17 8v8" />
                </svg>
                <span>Scan</span>
              </button>
            </div>

            {lookup && <p className="muted small" role="status">Looking up {lookup}…</p>}
            {error && <p className="form-error" role="alert">{error}</p>}

            <div className="results" aria-live="polite">
              {recentMatches.length > 0 && <div className="results-label">Logged before</div>}
              {recentMatches.slice(0, query.trim().length >= 2 ? 3 : 8).map((r) => (
                <button key={`r-${r.id}`} type="button" className="result" onClick={() => pickRecent(r)}>
                  <span className="result-name">{r.name}</span>
                  <span className="result-meta">
                    {r.calories} kcal{r.servingLabel ? ` per ${r.servingLabel}` : " per serving"}
                  </span>
                </button>
              ))}

              {query.trim().length >= 2 && (
                <>
                  <div className="results-label">
                    Food database{searching && <span className="muted"> · searching…</span>}
                  </div>
                  {results?.foods.map((food) => (
                    <button key={`${food.source}-${food.id}`} type="button" className="result" onClick={() => pick(food)}>
                      <span className="result-name">{food.name}</span>
                      <span className="result-meta">
                        {food.brand ? `${food.brand} · ` : ""}
                        {food.portions[0]
                          ? `${Math.round((food.per100g.kcal * food.portions[0].grams) / 100)} kcal per ${food.portions[0].label}`
                          : `${Math.round(food.per100g.kcal)} kcal per 100 g`}
                      </span>
                    </button>
                  ))}
                  {results && !searching && results.foods.length === 0 && (
                    <p className="muted small">No matches. Try fewer words, or enter it yourself.</p>
                  )}
                  {results && results.unavailable.length > 0 && (
                    <p className="muted small">{results.unavailable.join(" and ")} didn't answer, so some foods may be missing.</p>
                  )}
                </>
              )}
            </div>

            <div className="food-sheet-actions">
              <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
              <button type="button" className="btn" onClick={manual}>Enter it yourself</button>
            </div>
          </>
        ) : (
          <>
            {db && (
              <div className="picked">
                <div className="picked-name">{db.name}</div>
                <div className="muted small">
                  {db.brand ? `${db.brand} · ` : ""}{db.source === "usda" ? "USDA FoodData Central" : "Open Food Facts"}
                </div>
                <div className="ff-grid two">
                  <label className="ff">
                    <span>{unit === "g" ? "Grams" : "How many"}</span>
                    <input className="field" inputMode="decimal" value={amount} onChange={(e) => {
                      setAmount(e.target.value);
                      apply(db, unit, e.target.value);
                    }} />
                  </label>
                  <label className="ff">
                    <span>Of</span>
                    <select className="field" value={String(unit)} onChange={(e) => {
                      const u: Unit = e.target.value === "g" || e.target.value === "100g" ? e.target.value : Number(e.target.value);
                      const amt = u === "g" ? String(u === unit ? amount : 100) : "1";
                      setUnit(u);
                      setAmount(amt);
                      apply(db, u, amt);
                    }}>
                      {db.portions.map((p, i) => <option key={p.label} value={i}>{p.label}</option>)}
                      <option value="100g">100 g</option>
                      <option value="g">grams</option>
                    </select>
                  </label>
                </div>
              </div>
            )}

            <label className="ff">
              <span>Food</span>
              <input className="field" value={f.name} onChange={set("name")} placeholder="e.g. Greek yogurt"
                autoComplete="off" maxLength={120} autoFocus={!entry && !db && !f.name} />
            </label>

            <div className="ff-grid">
              {!db && (
                <label className="ff">
                  <span>Servings{servingLabel && <small> of {servingLabel}</small>}</span>
                  <input className="field" inputMode="decimal" value={f.servings} onChange={set("servings")} />
                </label>
              )}
              <label className="ff">
                <span>Calories <small>{db ? "each" : "per serving"}</small></span>
                <input className="field" inputMode="numeric" value={f.calories} onChange={set("calories")} placeholder="0" />
              </label>
              <label className="ff">
                <span><i className="macro-dot p" />Protein g</span>
                <input className="field" inputMode="decimal" value={f.proteinG} onChange={set("proteinG")} placeholder="0" />
              </label>
              <label className="ff">
                <span><i className="macro-dot c" />Carbs g</span>
                <input className="field" inputMode="decimal" value={f.carbsG} onChange={set("carbsG")} placeholder="0" />
              </label>
              <label className="ff">
                <span><i className="macro-dot f" />Fat g</span>
                <input className="field" inputMode="decimal" value={f.fatG} onChange={set("fatG")} placeholder="0" />
              </label>
              <div className="ff total">
                <span>Total</span>
                <b>{Number.isFinite(total) ? Math.round(total) : 0} kcal</b>
              </div>
            </div>

            {error && <p className="form-error" role="alert">{error}</p>}
            <div className="food-sheet-actions">
              {entry && <button type="button" className="btn ghost danger" onClick={remove} disabled={busy}>Delete</button>}
              <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
              <button type="submit" className="btn primary" disabled={busy}>{entry ? "Save" : "Add"}</button>
            </div>
          </>
        )}
      </form>
      {scanning && (
        <div onClick={(e) => e.stopPropagation()}>
          <BarcodeScanner onCode={onCode} onClose={() => setScanning(false)} />
        </div>
      )}
    </div>
  );
}
