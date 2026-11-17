/** 545 -> "9:05" */
export function formatPace(secPerMi: number | null | undefined): string {
  if (secPerMi == null || !isFinite(secPerMi) || secPerMi <= 0) return "–";
  const s = Math.round(secPerMi);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

/** 3725 -> "1:02:05", 1500 -> "25:00" */
export function formatDuration(sec: number | null | undefined): string {
  if (sec == null || sec <= 0) return "–";
  const s = Math.round(sec);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  const mm = h > 0 ? String(m).padStart(2, "0") : String(m);
  return `${h > 0 ? `${h}:` : ""}${mm}:${String(r).padStart(2, "0")}`;
}

/** Accepts "45", "45:30" or "1:02:05". Returns seconds, or null if it can't be read. */
export function parseDuration(text: string): number | null {
  const t = text.trim();
  if (!t) return null;
  if (!/^\d+(:\d{1,2}){0,2}$/.test(t)) return null;
  const parts = t.split(":").map(Number);
  if (parts.slice(1).some((p) => p >= 60)) return null;
  if (parts.length === 1) return parts[0] * 60; // plain number means minutes
  if (parts.length === 2) return parts[0] * 60 + parts[1];
  return parts[0] * 3600 + parts[1] * 60 + parts[2];
}

export function formatMiles(mi: number | null | undefined, digits = 1): string {
  if (mi == null) return "–";
  return mi.toFixed(digits);
}

export function formatLb(lb: number): string {
  return Math.round(lb).toLocaleString("en-US");
}

/** Parse yyyy-mm-dd as a local date (no UTC shift). */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function formatDate(iso: string, opts: Intl.DateTimeFormatOptions = { weekday: "short", month: "short", day: "numeric" }) {
  return parseDate(iso).toLocaleDateString("en-US", opts);
}

export function shortDate(iso: string) {
  return formatDate(iso, { month: "short", day: "numeric" });
}

/** "07:12:00" -> "7:12 AM" */
export function formatTime(t: string | null | undefined): string {
  if (!t) return "";
  const [h, m] = t.split(":").map(Number);
  const suffix = h >= 12 ? "PM" : "AM";
  return `${h % 12 || 12}:${String(m).padStart(2, "0")} ${suffix}`;
}

/** Monday of the week containing iso. */
export function weekStartOf(iso: string): string {
  const d = parseDate(iso);
  const offset = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - offset);
  return toIsoDate(d);
}

/** Epley estimated one-rep max. */
export function e1rm(reps: number, weightLb: number): number {
  if (reps <= 0 || weightLb <= 0) return 0;
  return reps === 1 ? weightLb : weightLb * (1 + reps / 30);
}

/** "3 × 5 @ 185" when every set matches, otherwise "5, 5, 4 @ 185". */
export function summarizeSets(sets: { reps: number; weightLb: number }[]): string {
  if (sets.length === 0) return "";
  const weights = new Set(sets.map((s) => s.weightLb));
  const reps = new Set(sets.map((s) => s.reps));
  const at = (w: number) => (w > 0 ? ` @ ${formatLb(w)}` : "");
  if (weights.size === 1 && reps.size === 1) return `${sets.length} × ${sets[0].reps}${at(sets[0].weightLb)}`;
  if (weights.size === 1) return `${sets.map((s) => s.reps).join(", ")}${at(sets[0].weightLb)}`;
  return sets.map((s) => `${s.reps}${at(s.weightLb)}`).join(", ");
}
