import type {
  ApiError,
  CreateReceivableRequest,
  Page,
  ReceivableResponse,
  SettlementResponse,
  SimulateRequest,
  SimulateResponse,
} from "../types/api";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public status: number,
    public fieldErrors: ApiError["fieldErrors"] = [],
  ) {
    super(message);
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiError | null;
    throw new ApiRequestError(
      body?.message ?? `Erro ${response.status} ao chamar ${path}`,
      response.status,
      body?.fieldErrors ?? [],
    );
  }

  // 204 No Content ou corpo vazio
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export const api = {
  simulate: (payload: SimulateRequest) =>
    request<SimulateResponse>("/pricing/simulate", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  acquireReceivable: (payload: CreateReceivableRequest) =>
    request<ReceivableResponse>("/receivables", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  settle: (receivableId: number, idempotencyKey: string) =>
    request<SettlementResponse>(`/receivables/${receivableId}/settlements`, {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
    }),

  extrato: (params: {
    cedente?: string;
    currency?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) => {
    const query = new URLSearchParams();
    if (params.cedente) query.set("cedente", params.cedente);
    if (params.currency) query.set("currency", params.currency);
    if (params.from) query.set("from", params.from);
    if (params.to) query.set("to", params.to);
    query.set("page", String(params.page ?? 0));
    query.set("size", String(params.size ?? 10));
    return request<Page<SettlementResponse>>(`/settlements?${query.toString()}`);
  },
};
