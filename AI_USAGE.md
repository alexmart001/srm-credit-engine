# AI_USAGE.md

Este documento não é um log de sessões — é a engenharia da colaboração
(item 7 do desafio). Fui o desenvolvedor: tomei toda decisão de negócio,
arquitetura e escopo; a IA (Claude) escreveu a maior parte do código sob
minha especificação, e eu revisei, testei e corrigi o que ela produziu.

## 1. Specs e prompts estratégicos dados à IA

Não colei o histórico completo — o que importa é a forma dos pedidos, que
foi sempre "aqui está a decisão de negócio já fechada, implemente refletindo
exatamente isso", nunca "decida por mim":

1. **Fase 0:** primeiro fechei o `SPEC.md` com as premissas e as perguntas
   ao negócio (item 3 do desafio) — só depois pedi qualquer código. Quando
   as respostas do negócio chegaram (seção 2 do SPEC), pedi que a IA
   *reescrevesse* o SPEC refletindo as novas decisões antes de tocar em
   qualquer linha de implementação.
2. **Skeleton inicial:** com o SPEC fechado, pedi a estrutura completa do
   projeto (entidades, migrations, motor de precificação com Strategy,
   teste dos golden cases) numa tacada, especificando a stack
   (Java 21 + Spring Boot + MariaDB) e depois a versão exata
   (3.5.16) que eu queria usar — não deixei a IA escolher a versão.
3. **Concorrência:** pedi o `SettlementService` especificando as DUAS
   garantias que ele precisava ter (idempotência E optimistic locking) e
   exigi um teste que demonstrasse o conflito de concorrência real com
   threads, não só um teste unitário com mocks.
4. **Review reverso:** pedi o `REVIEW.md` do Anexo A como se a IA fosse a
   revisora de um PR — ordenado por severidade, com impacto de produção e
   correção proposta para cada item, não uma lista solta de problemas.
5. **Operação:** pedi CI, observabilidade e resiliência como itens
   separados, cada um com o requisito específico do desafio citado (ex.:
   "timeout + retry ou circuit breaker na integração mockada de câmbio") -
   isso forçou a IA a justificar cada decisão de design contra um critério
   concreto, em vez de gerar algo genérico.

## 2. Casos concretos em que a IA errou (e como o processo detectou)

Quatro exemplos reais desta implementação, não hipotéticos:

### 2.1 Bug de concorrência latente em consultas JPA (`NonUniqueResultException`)

Ao pedir a feature de resiliência de câmbio, a IA gerou `findEffectiveRate`
em `BaseRateRepository`/`FxRateRepository` retornando `Optional<T>` sem
limitar a query a 1 resultado. Isso não falhava em teste algum na hora —
só falharia no dia em que existisse mais de uma linha "vigente"
(`validTo IS NULL`) para o mesmo tipo/moeda, o que a própria feature nova
(endpoint de refresh de câmbio) tornava possível pela primeira vez.

**Como foi detectado:** a própria IA identificou o problema por revisão de
código ao planejar a feature seguinte (antes de eu sequer rodar nada) —
não foi um teste que falhou, foi análise estática do que a nova feature
tornava possível. Corrigido com `Limit.of(1)` (defesa em profundidade) e
fechamento explícito da vigência da linha anterior antes de inserir uma
nova (a correção de fato). Documentei isso porque é exatamente o tipo de
bug que "passa no code review humano apressado" — não quebra nada até o
dado mudar de formato.

### 2.2 Escape indevido quebrando um teste (`\$` em vez de `$`)

Um teste de integração (`jsonPath("\$.lockedFxRate")`) foi gerado com uma
barra invertida sobrando antes do `$`, resultado de um hábito de
escape de shell vazando para dentro do conteúdo de um arquivo Java. Isso
teria quebrado a compilação do teste.

**Como foi detectado:** a própria IA rodou um `grep` de sanidade
(`grep '\\\$'`) nos arquivos recém-criados antes de me entregar o
resultado, e encontrou o problema antes de eu rodar qualquer coisa.

### 2.3 Rota inválida com `/` num `@PathVariable`

O endpoint de refresh de câmbio foi inicialmente desenhado como
`POST /admin/fx-rates/{currencyPair}/refresh`, com `currencyPair` = "USD/BRL"
— um `@PathVariable` não pode conter `/`, o Spring MVC teria interpretado
como dois segmentos de rota distintos e a rota real nunca teria batido com
essa definição.

**Como foi detectado:** revisão da própria IA antes de eu rodar qualquer
requisição — não foi pego em teste (nenhum teste chamava essa rota
específica com esse formato ainda). Corrigido trocando para dois segmentos
(`/{base}/{quote}/refresh`).

### 2.4 Assinatura de método alterada sem atualizar os mocks do teste (só pego pelo `mvn test` real)

Ao corrigir o bug 2.1, o método `findEffectiveRate` ganhou um quarto
parâmetro (`Limit`) tanto em `BaseRateRepository` quanto em
`FxRateRepository`. Todos os pontos de PRODUÇÃO que chamavam esse método
foram atualizados corretamente na hora — mas os *stubs* do Mockito em
`SettlementServiceTest` (`when(baseRateRepository.findEffectiveRate(any(),
any(), any()))`, em quatro testes diferentes) continuaram com apenas três
`any()`, um a menos que a nova assinatura exige.

**Como foi detectado:** nenhuma revisão de código pegou este — nem a minha,
nem a da IA. Só apareceu quando rodei `mvn test` na minha máquina, como
erro de compilação (`method findEffectiveRate ... cannot be applied to
given types`). Este é o caso mais importante dos quatro para o argumento
da seção 3 abaixo: é exatamente o tipo de erro que "parece óbvio depois de
ver o stack trace", mas que review nenhum (humano ou de IA) tinha pego
antes — só a compilação real do projeto inteiro expôs. Corrigido
adicionando o quarto `any()` nos quatro pontos.

### O padrão comum aos quatro casos

Os três primeiros foram pegos por **verificação ativa da própria IA**
(grep de sanidade, análise do que uma mudança nova tornava possível) antes
mesmo de eu rodar qualquer coisa. O quarto só foi pego pela **compilação
real do projeto inteiro** — nem a IA, nem uma leitura cuidadosa minha do
diff, teriam pego uma referência a um método em um arquivo de teste que
não foi tocado na mesma alteração. Isso reforça a decisão mais importante
do meu processo: **nunca aceitei "compilei aqui e não deu erro" da IA como
prova de nada**, porque o ambiente onde ela gerava o código não tinha
acesso ao Maven Central e não conseguia rodar `mvn test` de verdade. Meu
processo real de verificação foi sempre rodar `mvn compile` e `mvn test`
na minha máquina antes de aceitar qualquer entrega como "pronta" —
inclusive quando a mudança parecia pequena e isolada (ex.: adicionar um
parâmetro a uma query). O caso 2.4 é a prova concreta de que essa
disciplina não foi excesso de cautela: sem ela, este bug teria ido para o
repositório.

## 3. O que eu decidi não delegar à IA, e por quê

- **As respostas às ambiguidades do SPEC.md** (prazo sempre em meses, taxa
  base multi-dimensional, câmbio travado na aquisição, liquidação apenas
  total, piso de taxa negativa em zero, sem validação de cedente na v1) —
  são decisões de negócio. Pedi à IA para *listar* as ambiguidades e
  *sugerir* premissas defensáveis, mas as respostas finais vieram de mim,
  simulando o que o negócio decidiria.
- **A escolha de stack e de versões exatas** (Java 21, Spring Boot 3.5.16,
  MariaDB) — pedi à IA para comparar opções, mas a decisão final e o
  travamento de versão foram meus.
- **Verificação de correção** — nunca aceitei a afirmação da IA de que algo
  "deveria funcionar" sem rodar `mvn test`/`mvn compile` eu mesmo. Esse é o
  motivo pelo qual entrego este projeto com confiança: cada rodada de
  código novo só foi considerada fechada depois da minha própria execução
  local confirmar, não depois da IA dizer que estava certo.
- **Priorização de escopo** (o que entrar no nível Sênior vs. o que ficar
  para Staff/TL, o que cortar e documentar em `DECISIONS.md`) — decisão de
  gestão de projeto, não técnica; fiz essas chamadas eu mesmo, usando a
  rubrica do desafio como guia, não a opinião da IA sobre "o que seria
  legal ter".
