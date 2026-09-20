import { useEffect, useState } from "react";
import { api, ApiRequestError } from "../api/client";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import type {
  Currency,
  ReceivableResponse,
  ReceivableType,
  SettlementResponse,
  SimulateResponse,
} from "../types/api";
import { RECEIVABLE_TYPE_LABELS } from "../types/api";

function formatMoney(value: string, currency: Currency): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return n.toLocaleString("pt-BR", { style: "currency", currency });
}

function formatPercent(rate: string): string {
  const n = Number(rate);
  if (Number.isNaN(n)) return rate;
  return `${(n * 100).toLocaleString("pt-BR", { maximumFractionDigits: 3 })}%`;
}

interface FormState {
  cedente: string;
  type: ReceivableType;
  faceValue: string;
  faceCurrency: Currency;
  termMonths: string;
  paymentCurrency: Currency;
}

const initialForm: FormState = {
  cedente: "",
  type: "DUPLICATA_MERCANTIL",
  faceValue: "",
  faceCurrency: "BRL",
  termMonths: "",
  paymentCurrency: "BRL",
};

export function AcquisitionPanel() {
  const [form, setForm] = useState<FormState>(initialForm);
  const debouncedForm = useDebouncedValue(form, 400);

  const [simulation, setSimulation] = useState<SimulateResponse | null>(null);
  const [simulating, setSimulating] = useState(false);
  const [simError, setSimError] = useState<string | null>(null);

  const [acquiring, setAcquiring] = useState(false);
  const [acquired, setAcquired] = useState<ReceivableResponse | null>(null);
  const [acquireError, setAcquireError] = useState<ApiRequestError | null>(null);

  const [settling, setSettling] = useState(false);
  const [settlement, setSettlement] = useState<SettlementResponse | null>(null);
  const [settleError, setSettleError] = useState<string | null>(null);

  const isFormValid =
    debouncedForm.faceValue.trim() !== "" &&
    Number(debouncedForm.faceValue) > 0 &&
    debouncedForm.termMonths.trim() !== "" &&
    Number(debouncedForm.termMonths) > 0;

  // Simulacao em tempo real (item 4.2.1) - dispara a cada mudanca nos
  // campos que afetam o calculo, com debounce para nao chamar a API a
  // cada tecla digitada.
  useEffect(() => {
    if (!isFormValid) {
      setSimulation(null);
      setSimError(null);
      return;
    }

    let cancelled = false;
    setSimulating(true);
    setSimError(null);

    api
      .simulate({
        type: debouncedForm.type,
        faceValue: debouncedForm.faceValue,
        faceCurrency: debouncedForm.faceCurrency,
        termMonths: Number(debouncedForm.termMonths),
        paymentCurrency: debouncedForm.paymentCurrency,
      })
      .then((result) => {
        if (!cancelled) setSimulation(result);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setSimulation(null);
        setSimError(err instanceof ApiRequestError ? err.message : "Falha ao simular");
      })
      .finally(() => {
        if (!cancelled) setSimulating(false);
      });

    return () => {
      cancelled = true;
    };
  }, [debouncedForm, isFormValid]);

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    // Qualquer edicao invalida um resultado ja' adquirido/liquidado anterior -
    // evita mostrar um "Liquidar" para dados que nao correspondem mais ao formulario.
    setAcquired(null);
    setSettlement(null);
    setAcquireError(null);
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function handleAcquire() {
    setAcquiring(true);
    setAcquireError(null);
    try {
      const receivable = await api.acquireReceivable({
        cedente: form.cedente,
        type: form.type,
        faceValue: form.faceValue,
        faceCurrency: form.faceCurrency,
        termMonths: Number(form.termMonths),
        paymentCurrency: form.paymentCurrency,
      });
      setAcquired(receivable);
    } catch (err) {
      if (err instanceof ApiRequestError) setAcquireError(err);
    } finally {
      setAcquiring(false);
    }
  }

  async function handleSettle() {
    if (!acquired) return;
    setSettling(true);
    setSettleError(null);
    try {
      const idempotencyKey = crypto.randomUUID();
      const result = await api.settle(acquired.id, idempotencyKey);
      setSettlement(result);
    } catch (err) {
      setSettleError(err instanceof ApiRequestError ? err.message : "Falha ao liquidar");
    } finally {
      setSettling(false);
    }
  }

  function fieldError(field: string): string | undefined {
    return acquireError?.fieldErrors.find((f) => f.field === field)?.message;
  }

  return (
    <div className="panel-grid">
      <div className="card">
        <h2>Nova aquisição</h2>

        {acquired && !settlement && (
          <div className="banner success">
            Recebível #{acquired.id} adquirido ({acquired.status}).
            {acquired.lockedFxRate && (
              <> Câmbio travado: <span className="num" style={{ display: "inline" }}>{acquired.lockedFxRate}</span></>
            )}
          </div>
        )}

        {settlement && (
          <div className="banner success">
            Liquidação #{settlement.id} concluída — {formatMoney(settlement.presentValue, settlement.currency)}
          </div>
        )}

        {acquireError && (
          <div className="banner error">
            {acquireError.message}
            <div className="details">
              {acquireError.fieldErrors.map((fe) => (
                <div key={fe.field}>• {fe.field}: {fe.message}</div>
              ))}
            </div>
          </div>
        )}

        {settleError && <div className="banner error">{settleError}</div>}

        <div className="field">
          <label htmlFor="cedente">Cedente</label>
          <input
            id="cedente"
            value={form.cedente}
            onChange={(e) => updateField("cedente", e.target.value)}
            placeholder="Nome da empresa cedente"
          />
          {fieldError("cedente") && <div className="field-error">{fieldError("cedente")}</div>}
        </div>

        <div className="field">
          <label htmlFor="type">Tipo de recebível</label>
          <select id="type" value={form.type} onChange={(e) => updateField("type", e.target.value as ReceivableType)}>
            {Object.entries(RECEIVABLE_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="faceValue">Valor de face</label>
          <div className="field-row">
            <input
              id="faceValue"
              className="num-input"
              inputMode="decimal"
              value={form.faceValue}
              onChange={(e) => updateField("faceValue", e.target.value)}
              placeholder="100000.00"
            />
            <select value={form.faceCurrency} onChange={(e) => updateField("faceCurrency", e.target.value as Currency)}>
              <option value="BRL">BRL</option>
              <option value="USD">USD</option>
            </select>
          </div>
          {fieldError("faceValue") && <div className="field-error">{fieldError("faceValue")}</div>}
        </div>

        <div className="field">
          <label htmlFor="termMonths">Prazo (meses inteiros)</label>
          <input
            id="termMonths"
            className="num-input"
            inputMode="numeric"
            value={form.termMonths}
            onChange={(e) => updateField("termMonths", e.target.value)}
            placeholder="3"
          />
          {fieldError("termMonths") && <div className="field-error">{fieldError("termMonths")}</div>}
        </div>

        <div className="field">
          <label htmlFor="paymentCurrency">Moeda de pagamento</label>
          <select
            id="paymentCurrency"
            value={form.paymentCurrency}
            onChange={(e) => updateField("paymentCurrency", e.target.value as Currency)}
          >
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </select>
        </div>

        {!acquired ? (
          <button className="primary" onClick={handleAcquire} disabled={!isFormValid || acquiring || !form.cedente}>
            {acquiring ? "Adquirindo…" : "Adquirir recebível"}
          </button>
        ) : !settlement ? (
          <button className="secondary" onClick={handleSettle} disabled={settling}>
            {settling ? "Liquidando…" : "Liquidar agora"}
          </button>
        ) : (
          <button className="ghost" onClick={() => { setForm(initialForm); setAcquired(null); setSettlement(null); }}>
            Nova operação
          </button>
        )}
      </div>

      <div className="card">
        <h2>Simulação {simulating && "· calculando…"}</h2>

        {!isFormValid && <div className="sim-placeholder">Preencha valor e prazo para simular</div>}

        {isFormValid && simError && <div className="banner error">{simError}</div>}

        {isFormValid && simulation && (
          <div className="sim-result">
            <div className="sim-figure">
              <div className="label">Valor líquido</div>
              <div className="value">{formatMoney(simulation.presentValue, simulation.currency)}</div>
            </div>
            <div className="sim-figure">
              <div className="label">Deságio</div>
              <div className="value small">{formatMoney(simulation.discount, form.faceCurrency)}</div>
            </div>
            <div className="sim-meta">
              <div className="row"><span>Taxa base</span><span>{formatPercent(simulation.baseRateUsed)}</span></div>
              <div className="row"><span>Spread</span><span>{formatPercent(simulation.spreadUsed)}</span></div>
              <div className="row"><span>Taxa efetiva aplicada</span><span>{formatPercent(simulation.effectiveRateApplied)}</span></div>
              {simulation.fxRateUsed && (
                <div className="row"><span>Câmbio</span><span>{simulation.fxRateUsed}</span></div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
