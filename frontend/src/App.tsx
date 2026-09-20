import { useState } from "react";

/**
 * Painel do operador (item 4.2.1 do desafio) - esqueleto inicial.
 * TODO: ligar ao endpoint de simulacao do backend (POST /pricing/simulate),
 * tratando presentValue como string (nunca number) para preservar precisao.
 */
export default function App() {
  const [faceValue, setFaceValue] = useState("");

  return (
    <div style={{ fontFamily: "sans-serif", padding: "2rem", maxWidth: 480 }}>
      <h1>SRM Credit Engine</h1>
      <p>Painel do operador - simulacao de deságio (skeleton inicial)</p>

      <label>
        Valor de face (R$)
        <input
          value={faceValue}
          onChange={(e) => setFaceValue(e.target.value)}
          placeholder="100000.00"
        />
      </label>

      {/* TODO: campos de tipo, prazo (meses), moeda de pagamento */}
      {/* TODO: chamada à API e exibição do valor líquido simulado */}
    </div>
  );
}
