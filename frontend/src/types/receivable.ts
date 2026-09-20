// Espelha os enums do backend (ReceivableType, Currency).
export type ReceivableType = "DUPLICATA_MERCANTIL" | "CHEQUE_PRE_DATADO";
export type Currency = "BRL" | "USD";

// Valores monetarios chegam da API como STRING (SPEC secao 3) - nunca
// desserializar como number, sob risco de perda de precisao decimal.
export interface SimulationRequest {
  faceValue: string;
  type: ReceivableType;
  termMonths: number;
  paymentCurrency: Currency;
}

export interface SimulationResult {
  presentValue: string;
  currency: Currency;
  effectiveRateApplied: string;
  fxRateApplied: string | null;
}
