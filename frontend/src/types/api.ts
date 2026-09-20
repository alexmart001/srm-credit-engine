// Espelha os DTOs do backend. Valores monetarios e taxas chegam como
// STRING (nunca number) - SPEC secao 3: nao arriscar perda de precisao
// decimal ao desserializar. So' convertemos para number no momento de
// FORMATAR para exibicao (Intl.NumberFormat), nunca para calcular.

export type ReceivableType = "DUPLICATA_MERCANTIL" | "CHEQUE_PRE_DATADO";
export type Currency = "BRL" | "USD";
export type ReceivableStatus = "PENDENTE" | "LIQUIDADO";

export interface SimulateRequest {
  type: ReceivableType;
  faceValue: string;
  faceCurrency: Currency;
  termMonths: number;
  paymentCurrency: Currency;
}

export interface SimulateResponse {
  presentValue: string;
  currency: Currency;
  discount: string;
  baseRateUsed: string;
  spreadUsed: string;
  effectiveRateApplied: string;
  fxRateUsed: string | null;
}

export interface CreateReceivableRequest {
  cedente: string;
  type: ReceivableType;
  faceValue: string;
  faceCurrency: Currency;
  termMonths: number;
  paymentCurrency: Currency;
}

export interface ReceivableResponse {
  id: number;
  cedente: string;
  type: ReceivableType;
  faceValue: string;
  faceCurrency: Currency;
  termMonths: number;
  paymentCurrency: Currency;
  lockedFxRate: string | null;
  status: ReceivableStatus;
  acquiredAt: string;
}

export interface SettlementResponse {
  id: number;
  receivableId: number;
  presentValue: string;
  currency: Currency;
  baseRateUsed: string;
  spreadUsed: string;
  effectiveRateRaw: string;
  effectiveRateApplied: string;
  fxRateUsed: string | null;
  settledAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // pagina atual (0-based)
  size: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors: { field: string; message: string }[];
}

export const RECEIVABLE_TYPE_LABELS: Record<ReceivableType, string> = {
  DUPLICATA_MERCANTIL: "Duplicata Mercantil",
  CHEQUE_PRE_DATADO: "Cheque Pré-datado",
};
