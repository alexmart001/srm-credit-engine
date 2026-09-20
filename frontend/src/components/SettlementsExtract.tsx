import { useEffect, useState } from "react";
import { api, ApiRequestError } from "../api/client";
import type { Currency, Page, SettlementResponse } from "../types/api";

function formatMoney(value: string, currency: Currency): string {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return n.toLocaleString("pt-BR", { style: "currency", currency });
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

const PAGE_SIZE = 10;

export function SettlementsExtract() {
  const [cedente, setCedente] = useState("");
  const [currency, setCurrency] = useState("");
  const [page, setPage] = useState(0);

  const [data, setData] = useState<Page<SettlementResponse> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    api
      .extrato({ cedente: cedente || undefined, currency: currency || undefined, page, size: PAGE_SIZE })
      .then(setData)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : "Falha ao carregar extrato"))
      .finally(() => setLoading(false));
  }

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(load, [page]);

  function handleSearch() {
    setPage(0);
    load();
  }

  return (
    <div className="card">
      <h2>Extrato de liquidações</h2>

      <div className="filters">
        <div className="field">
          <label htmlFor="f-cedente">Cedente</label>
          <input id="f-cedente" value={cedente} onChange={(e) => setCedente(e.target.value)} placeholder="Nome exato do cedente" />
        </div>
        <div className="field">
          <label htmlFor="f-currency">Moeda</label>
          <select id="f-currency" value={currency} onChange={(e) => setCurrency(e.target.value)}>
            <option value="">Todas</option>
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </select>
        </div>
        <button className="ghost" onClick={handleSearch}>Buscar</button>
      </div>

      {error && <div className="banner error">{error}</div>}

      {!error && data && data.content.length === 0 && (
        <div className="empty-state">Nenhuma liquidação encontrada para esse filtro.</div>
      )}

      {!error && data && data.content.length > 0 && (
        <>
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Recebível</th>
                <th className="num">Valor líquido</th>
                <th>Moeda</th>
                <th className="num">Taxa aplicada</th>
                <th>Liquidado em</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((s) => (
                <tr key={s.id}>
                  <td className="num">{s.id}</td>
                  <td className="num">{s.receivableId}</td>
                  <td className="num">{formatMoney(s.presentValue, s.currency)}</td>
                  <td>{s.currency}</td>
                  <td className="num">{(Number(s.effectiveRateApplied) * 100).toFixed(3)}%</td>
                  <td>{formatDate(s.settledAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="pagination">
            <span>
              Página {data.number + 1} de {Math.max(data.totalPages, 1)} · {data.totalElements} liquidações
            </span>
            <div className="controls">
              <button className="ghost" onClick={() => setPage((p) => p - 1)} disabled={page === 0 || loading}>
                ← Anterior
              </button>
              <button
                className="ghost"
                onClick={() => setPage((p) => p + 1)}
                disabled={data.number + 1 >= data.totalPages || loading}
              >
                Próxima →
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
