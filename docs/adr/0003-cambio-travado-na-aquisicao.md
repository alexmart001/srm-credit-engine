# ADR 0003 — Câmbio travado na aquisição, não consultado na liquidação

## Status
Aceito (decisão do negócio, SPEC 1.3)

## Contexto
O enunciado original do desafio é propositalmente ambíguo sobre qual
câmbio vale na liquidação de um título cross-currency: o vigente na data
da operação, o mais recente, ou o do vencimento. A primeira versão deste
projeto assumiu "câmbio vigente no instante da liquidação" (ver histórico
no `SPEC.md`, seção 2, item 3) — o negócio depois decidiu travar o câmbio
no momento da aquisição (rate lock).

## Decisão
O câmbio é lido e persistido (`Receivable.lockedFxRate`) uma única vez, na
aquisição. A liquidação nunca consulta câmbio — só reutiliza o valor já
travado.

## Alternativas consideradas
- **Câmbio vigente na liquidação (premissa original, descartada):** mais
  simples de implementar (uma consulta a menos no momento da aquisição),
  mas expõe o fundo a variação cambial entre aquisição e liquidação — o
  negócio decidiu que essa exposição não é desejada.
- **Câmbio vigente no vencimento:** logicamente inconsistente quando a
  liquidação ocorre antes do vencimento (liquidação antecipada), e o
  enunciado não define esse fluxo — descartada por criar mais ambiguidade
  do que resolve.

## Consequências
Esta decisão teve um efeito arquitetural que só ficou claro ao implementar
a resiliência da integração de câmbio (ver `docs/c4-diagrams.md` e o
javadoc de `ReceivableService`): como o câmbio só é consultado na
aquisição, **a liquidação nunca depende da disponibilidade do provedor
externo de câmbio**. Isso resolve de graça a pergunta "o que acontece se o
provedor cair no meio de uma liquidação?" (item 6 do desafio, nível
sênior) — a resposta é "nada", porque a liquidação não o chama.

Efeito colateral que vale registrar: se o provedor de câmbio cair
justamente no momento de uma AQUISIÇÃO nova (não de uma liquidação), essa
aquisição específica de fato não pode prosseguir sem uma taxa —
`ReceivableService` lê a taxa da tabela local `fx_rates`, que é populada
de forma assíncrona/administrativa (`FxRateAdminService`) e não trava a
aquisição num provedor externo em tempo real; a janela de risco real é
apenas "a taxa local pode estar desatualizada", nunca "a aquisição trava
esperando o provedor responder".
