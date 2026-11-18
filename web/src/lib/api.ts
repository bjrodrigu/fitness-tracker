import type {
  Activity, ActivityType, AuthConfig, FoodEntry, GarminLinkResponse, GarminStatus, GoalProfile, GoalSuggestion, Me,
  NutritionGoal, NutritionReport, Stats, FoodResult, FoodSearchResult, ParseResult,
} from "./types";

/** Fired when the server says the session is gone, so the app can show the sign-in screen. */
export const SIGNED_OUT_EVENT = "journal:signed-out";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

function csrfToken(): string | null {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : null;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (method !== "GET") {
    // Any GET hands out the CSRF cookie; get one first if it's missing (cleared cookies, first visit).
    if (!csrfToken()) await fetch("/api/auth/config", { credentials: "same-origin" }).catch(() => undefined);
    const token = csrfToken();
    if (token) headers["X-XSRF-TOKEN"] = token;
  }
  const res = await fetch(path, { ...init, credentials: "same-origin", headers: { ...headers, ...init.headers } });
  if (!res.ok) {
    let detail = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      if (body?.detail) detail = body.detail;
      else if (res.status === 400 && body?.title) detail = body.title;
    } catch {
      /* body wasn't JSON */
    }
    if (res.status === 401 && !path.startsWith("/api/auth/")) {
      window.dispatchEvent(new Event(SIGNED_OUT_EVENT));
    }
    throw new ApiError(detail, res.status);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });

export interface ActivityFilter {
  type?: ActivityType;
  q?: string;
  tag?: string;
}

export const api = {
  list(filter: ActivityFilter = {}) {
    const params = new URLSearchParams();
    if (filter.type) params.set("type", filter.type);
    if (filter.q) params.set("q", filter.q);
    if (filter.tag) params.set("tag", filter.tag);
    const qs = params.toString();
    return request<Activity[]>(`/api/activities${qs ? `?${qs}` : ""}`);
  },
  get: (id: string) => request<Activity>(`/api/activities/${id}`),
  create: (a: Activity) => post<Activity>("/api/activities", a),
  update: (id: string, a: Activity) =>
    request<Activity>(`/api/activities/${id}`, { method: "PUT", body: JSON.stringify(a) }),
  remove: (id: string) => request<void>(`/api/activities/${id}`, { method: "DELETE" }),
  stats: (weeks = 16, days = 140) => request<Stats>(`/api/stats?weeks=${weeks}&days=${days}`),
};

export const auth = {
  config: () => request<AuthConfig>("/api/auth/config"),
  me: () => request<Me>("/api/auth/me"),
  login: (email: string, password: string) => post<Me>("/api/auth/login", { email, password }),
  signup: (name: string, email: string, password: string) => post<Me>("/api/auth/signup", { name, email, password }),
  demo: () => post<Me>("/api/auth/demo"),
  logout: () => post<void>("/api/auth/logout"),
};

export const garmin = {
  status: () => request<GarminStatus>("/api/garmin"),
  link: (email: string, password: string) => post<GarminLinkResponse>("/api/garmin/link", { email, password }),
  code: (code: string) => post<GarminLinkResponse>("/api/garmin/link/code", { code }),
  sync: () => post<GarminStatus>("/api/garmin/sync"),
  unlink: () => request<void>("/api/garmin/link", { method: "DELETE" }),
};

export const nutrition = {
  day: (date: string) => request<FoodEntry[]>(`/api/food?date=${date}`),
  recent: () => request<FoodEntry[]>("/api/food/recent"),
  add: (f: FoodEntry) => post<FoodEntry>("/api/food", f),
  update: (id: string, f: FoodEntry) => request<FoodEntry>(`/api/food/${id}`, { method: "PUT", body: JSON.stringify(f) }),
  remove: (id: string) => request<void>(`/api/food/${id}`, { method: "DELETE" }),
  weigh: (date: string, weightLb: number) =>
    request<unknown>(`/api/weights/${date}`, { method: "PUT", body: JSON.stringify({ weightLb }) }),
  unweigh: (date: string) => request<void>(`/api/weights/${date}`, { method: "DELETE" }),
  report: (days = 56) => request<NutritionReport>(`/api/nutrition?days=${days}`),
  saveGoal: (g: NutritionGoal) =>
    request<NutritionGoal>("/api/nutrition/goal", { method: "PUT", body: JSON.stringify(g) }),
  suggest: (p: GoalProfile) => post<GoalSuggestion>("/api/nutrition/suggest", p),
  search: (q: string, signal?: AbortSignal) =>
    request<FoodSearchResult>(`/api/foods/search?q=${encodeURIComponent(q)}`, { signal }),
  barcode: (code: string) => request<FoodResult>(`/api/foods/barcode/${code}`),
};

export const parser = {
  status: () => request<{ enabled: boolean; model: string }>("/api/parse/status"),
  read: (message: string, today: string, hour: number) => post<ParseResult>("/api/parse", { message, today, hour }),
};
