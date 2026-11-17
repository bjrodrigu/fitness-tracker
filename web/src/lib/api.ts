import type { Activity, ActivityType, Stats } from "./types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) {
    let detail = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      if (body?.detail) detail = body.detail;
    } catch {
      /* body wasn't JSON */
    }
    throw new Error(detail);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

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
  create: (a: Activity) => request<Activity>("/api/activities", { method: "POST", body: JSON.stringify(a) }),
  update: (id: string, a: Activity) =>
    request<Activity>(`/api/activities/${id}`, { method: "PUT", body: JSON.stringify(a) }),
  remove: (id: string) => request<void>(`/api/activities/${id}`, { method: "DELETE" }),
  stats: (weeks = 16, days = 140) => request<Stats>(`/api/stats?weeks=${weeks}&days=${days}`),
};
