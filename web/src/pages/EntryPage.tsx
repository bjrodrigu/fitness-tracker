import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import type { Activity, ActivityType, Exercise } from "../lib/types";
import { e1rm, formatDuration, formatLb, formatPace, parseDuration, toIsoDate } from "../lib/format";

/** Text versions of the number fields, so half-typed values like "8." don't get mangled. */
interface Draft {
  a: Activity;
  distance: string;
  duration: string;
  heartRate: string;
  tags: string;
}

function toDraft(a: Activity): Draft {
  return {
    a,
    distance: a.distanceMi != null ? String(a.distanceMi) : "",
    duration: a.durationSec ? formatDuration(a.durationSec) : "",
    heartRate: a.avgHeartRate != null ? String(a.avgHeartRate) : "",
    tags: a.tags.join(", "),
  };
}

function blank(type: ActivityType): Activity {
  return {
    type,
    date: toIsoDate(new Date()),
    title: "",
    notes: "",
    tags: [],
    exercises: type === "STRENGTH" ? [{ name: "", sets: [{ reps: 5, weightLb: 0 }] }] : [],
  };
}

/** Turns the draft into an API payload, or returns what's wrong with it. */
function build(d: Draft): { activity?: Activity; problem?: string } {
  const a = d.a;
  if (!a.title.trim()) return { problem: "Add a title to save" };
  if (!a.date) return { problem: "Pick a date to save" };
  const distance = d.distance.trim() ? Number(d.distance) : null;
  if (distance != null && (!isFinite(distance) || distance < 0)) return { problem: "Distance should be a number of miles" };
  const duration = d.duration.trim() ? parseDuration(d.duration) : null;
  if (d.duration.trim() && duration == null) return { problem: "Time should look like 45:30 or 1:02:05" };
  const hr = d.heartRate.trim() ? Number(d.heartRate) : null;
  if (hr != null && (!Number.isInteger(hr) || hr < 30 || hr > 250)) return { problem: "Heart rate should be between 30 and 250" };
  const tags = [...new Set(d.tags.split(",").map((t) => t.trim().toLowerCase()).filter(Boolean))];
  const exercises = a.exercises
    .filter((e) => e.name.trim())
    .map((e) => ({ name: e.name.trim(), sets: e.sets.filter((s) => s.reps > 0) }));
  return {
    activity: {
      ...a,
      title: a.title.trim(),
      distanceMi: a.type === "RUN" ? distance : null,
      avgHeartRate: a.type === "RUN" ? hr : null,
      durationSec: duration,
      tags,
      exercises: a.type === "STRENGTH" ? exercises : [],
    },
  };
}

function AutoTextarea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const ref = useRef<HTMLTextAreaElement>(null);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${el.scrollHeight}px`;
  }, [props.value]);
  return <textarea ref={ref} rows={4} {...props} />;
}

type SaveState = "idle" | "pending" | "saving" | "saved" | "error";

export default function EntryPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const isNew = !id;
  const newType: ActivityType = params.get("type") === "STRENGTH" ? "STRENGTH" : "RUN";

  const [draft, setDraft] = useState<Draft | null>(isNew ? toDraft(blank(newType)) : null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [save, setSave] = useState<SaveState>("idle");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);

  // Load an existing entry, or reset when switching between "new run" and "new lift".
  useEffect(() => {
    setDirty(false);
    setSave("idle");
    if (isNew) {
      setDraft(toDraft(blank(newType)));
      return;
    }
    setDraft(null);
    api.get(id!).then((a) => setDraft(toDraft(a)), (e: Error) => setLoadError(e.message));
  }, [id, isNew, newType]);

  // Existing entries save themselves shortly after you stop typing.
  useEffect(() => {
    if (isNew || !dirty || !draft) return;
    const { activity, problem } = build(draft);
    if (problem) {
      setSave("error");
      setSaveError(problem);
      return;
    }
    setSave("pending");
    const t = setTimeout(() => {
      setSave("saving");
      api.update(id!, activity!).then(
        () => {
          setSave("saved");
          setSaveError(null);
        },
        (e: Error) => {
          setSave("error");
          setSaveError(e.message);
        },
      );
    }, 700);
    return () => clearTimeout(t);
  }, [draft, dirty, id, isNew]);

  if (loadError) return <p className="state error">Couldn't open this entry: {loadError}. <Link to="/journal">Back to the journal</Link></p>;
  if (!draft) return <p className="state">Loading…</p>;

  const a = draft.a;
  const edit = (patch: Partial<Omit<Draft, "a">> & { a?: Partial<Activity> }) => {
    setDraft((d) => (d ? { ...d, ...patch, a: { ...d.a, ...patch.a } } : d));
    setDirty(true);
  };
  const setExercises = (exercises: Exercise[]) => edit({ a: { exercises } });

  const built = build(draft);
  const pace = (() => {
    const mi = Number(draft.distance);
    const sec = parseDuration(draft.duration);
    return mi > 0 && sec ? sec / mi : null;
  })();

  const create = async () => {
    if (!built.activity) return;
    setSave("saving");
    try {
      const saved = await api.create(built.activity);
      navigate(`/entries/${saved.id}`, { replace: true });
    } catch (e) {
      setSave("error");
      setSaveError((e as Error).message);
    }
  };

  const remove = async () => {
    if (!window.confirm(`Delete "${a.title || "this entry"}"? This can't be undone.`)) return;
    try {
      await api.remove(id!);
      navigate("/journal");
    } catch (e) {
      setSave("error");
      setSaveError((e as Error).message);
    }
  };

  const status =
    isNew ? (built.problem ?? "Not saved yet")
    : save === "saving" || save === "pending" ? "Saving…"
    : save === "saved" ? "Saved"
    : save === "error" ? saveError
    : a.source === "garmin" ? "Imported from Garmin"
    : "";

  return (
    <article className="doc">
      <div className="doc-top">
        <Link to="/journal" className="crumb">Journal</Link>
        <span className={`save-state${save === "error" ? " error" : ""}`} role="status">{status}</span>
      </div>

      <div className="doc-type">
        <span className={`mark ${a.type === "RUN" ? "run" : "strength"}`} />
        {a.type === "RUN" ? "Run" : "Strength session"}
      </div>
      <label htmlFor="title" className="visually-hidden">Title</label>
      <input
        id="title"
        className="title-input"
        value={a.title}
        placeholder={a.type === "RUN" ? "Easy 5" : "Lower body"}
        onChange={(e) => edit({ a: { title: e.target.value } })}
        autoFocus={isNew}
      />

      <div className="props">
        <div>
          <label htmlFor="p-date">Date</label>
          <div className="v">
            <input id="p-date" className="prop-input" type="date" value={a.date} onChange={(e) => edit({ a: { date: e.target.value } })} />
          </div>
        </div>
        <div>
          <label htmlFor="p-start">Start time</label>
          <div className="v">
            <input id="p-start" className="prop-input" type="time" value={a.startTime?.slice(0, 5) ?? ""} onChange={(e) => edit({ a: { startTime: e.target.value || null } })} />
          </div>
        </div>
        {a.type === "RUN" && (
          <div>
            <label htmlFor="p-dist">Distance</label>
            <div className="v">
              <input id="p-dist" className="prop-input" inputMode="decimal" placeholder="Miles, e.g. 5.2" value={draft.distance} onChange={(e) => edit({ distance: e.target.value })} />
            </div>
          </div>
        )}
        <div>
          <label htmlFor="p-time">{a.type === "RUN" ? "Time" : "Duration"}</label>
          <div className="v">
            <input
              id="p-time"
              className={`prop-input${draft.duration && parseDuration(draft.duration) == null ? " invalid" : ""}`}
              placeholder="45:30 or 1:02:05"
              value={draft.duration}
              onChange={(e) => edit({ duration: e.target.value })}
            />
          </div>
        </div>
        {a.type === "RUN" && (
          <>
            <div>
              <span className="k">Pace</span>
              <div className="v"><span className="prop-computed">{pace ? `${formatPace(pace)} /mi` : "–"}</span></div>
            </div>
            <div>
              <label htmlFor="p-hr">Avg heart rate</label>
              <div className="v">
                <input id="p-hr" className="prop-input" inputMode="numeric" placeholder="bpm" value={draft.heartRate} onChange={(e) => edit({ heartRate: e.target.value })} />
              </div>
            </div>
          </>
        )}
        <div>
          <span className="k" id="effort-label">Effort</span>
          <div className="v">
            <div className="effort" role="group" aria-labelledby="effort-label">
              {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                <button
                  key={n}
                  type="button"
                  aria-pressed={a.effort === n}
                  aria-label={`Effort ${n} of 10`}
                  onClick={() => edit({ a: { effort: a.effort === n ? null : n } })}
                >
                  {n}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div>
          <label htmlFor="p-tags">Tags</label>
          <div className="v">
            <input id="p-tags" className="prop-input" style={{ maxWidth: "none" }} placeholder="easy, long, race" value={draft.tags} onChange={(e) => edit({ tags: e.target.value })} />
          </div>
        </div>
      </div>

      <label htmlFor="notes" className="visually-hidden">Notes</label>
      <AutoTextarea
        id="notes"
        className="notes"
        placeholder="How did it go? Weather, how your legs felt, what you'd change next time."
        value={a.notes ?? ""}
        onChange={(e) => edit({ a: { notes: e.target.value } })}
      />

      {a.type === "STRENGTH" && (
        <section className="exercises" aria-label="Exercises">
          {a.exercises.map((ex, i) => {
            const update = (patch: Partial<Exercise>) => setExercises(a.exercises.map((x, j) => (j === i ? { ...x, ...patch } : x)));
            return (
              <div className="exercise" key={i}>
                <div className="exercise-head">
                  <input
                    className="exercise-name"
                    aria-label={`Exercise ${i + 1} name`}
                    placeholder="Exercise, e.g. Back squat"
                    value={ex.name}
                    onChange={(e) => update({ name: e.target.value })}
                  />
                  <button type="button" className="btn ghost small" onClick={() => setExercises(a.exercises.filter((_, j) => j !== i))}>
                    Remove
                  </button>
                </div>
                <div className="sets">
                  <span className="h">Set</span>
                  <span className="h" style={{ textAlign: "right" }}>Reps</span>
                  <span />
                  <span className="h" style={{ textAlign: "right" }}>Weight (lb)</span>
                  <span className="h">Estimated max</span>
                  {ex.sets.map((s, k) => {
                    const setAt = (patch: Partial<typeof s>) => update({ sets: ex.sets.map((y, m) => (m === k ? { ...y, ...patch } : y)) });
                    const est = e1rm(s.reps, s.weightLb);
                    return (
                      <div key={k} style={{ display: "contents" }}>
                        <span className="muted">{k + 1}</span>
                        <input
                          aria-label={`Set ${k + 1} reps`}
                          inputMode="numeric"
                          value={s.reps || ""}
                          onChange={(e) => setAt({ reps: Math.max(0, Math.floor(Number(e.target.value) || 0)) })}
                        />
                        <span className="x">×</span>
                        <input
                          aria-label={`Set ${k + 1} weight in pounds`}
                          inputMode="decimal"
                          placeholder="body"
                          value={s.weightLb || ""}
                          onChange={(e) => setAt({ weightLb: Math.max(0, Number(e.target.value) || 0) })}
                        />
                        <span className="e1rm">
                          {est > 0 ? `${formatLb(est)} lb` : ""}
                          {ex.sets.length > 1 && (
                            <button
                              type="button"
                              className="btn ghost small"
                              aria-label={`Remove set ${k + 1}`}
                              onClick={() => update({ sets: ex.sets.filter((_, m) => m !== k) })}
                            >
                              ×
                            </button>
                          )}
                        </span>
                      </div>
                    );
                  })}
                </div>
                <button
                  type="button"
                  className="btn ghost small"
                  style={{ marginTop: 6 }}
                  onClick={() => update({ sets: [...ex.sets, ex.sets[ex.sets.length - 1] ?? { reps: 5, weightLb: 0 }] })}
                >
                  Add set
                </button>
              </div>
            );
          })}
          <button type="button" className="btn small" onClick={() => setExercises([...a.exercises, { name: "", sets: [{ reps: 5, weightLb: 0 }] }])}>
            Add exercise
          </button>
        </section>
      )}

      <div className="doc-foot">
        {isNew ? (
          <>
            <Link to="/journal" className="btn ghost">Cancel</Link>
            <button type="button" className="btn primary" disabled={!built.activity || save === "saving"} onClick={create}>
              Save entry
            </button>
          </>
        ) : (
          <>
            <span className="muted" style={{ fontSize: "var(--step--1)" }}>Changes save automatically.</span>
            <button type="button" className="btn danger" onClick={remove}>Delete entry</button>
          </>
        )}
      </div>
    </article>
  );
}
