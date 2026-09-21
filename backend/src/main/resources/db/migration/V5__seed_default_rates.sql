-- Dados iniciais para o sistema ser utilizavel logo apos a subida (docker
-- compose up), sem exigir uma chamada manual aos endpoints /admin/* antes
-- de qualquer teste na interface. NAO sao usados pelos testes automatizados
-- (o profile de teste usa H2 com ddl-auto=create-drop e Flyway desabilitado
-- - ver backend/src/test/resources/application.yml - entao esta migration
-- nunca roda em teste, so' em dev/docker).
--
-- Valores escolhidos para bater exatamente com os golden cases do item 4.3
-- do desafio, para que testar a UI manualmente sirva tambem como conferencia
-- visual desses casos (C1, C2 em BRL; C3 usando a mesma taxa base + o
-- cambio abaixo para reproduzir o cenario cross-currency).
INSERT INTO base_rates (receivable_type, currency, rate, valid_from) VALUES
    ('DUPLICATA_MERCANTIL', 'BRL', 0.01000000, CURRENT_TIMESTAMP(6)),
    ('CHEQUE_PRE_DATADO',   'BRL', 0.01000000, CURRENT_TIMESTAMP(6)),
    ('DUPLICATA_MERCANTIL', 'USD', 0.01000000, CURRENT_TIMESTAMP(6)),
    ('CHEQUE_PRE_DATADO',   'USD', 0.01000000, CURRENT_TIMESTAMP(6));

INSERT INTO fx_rates (currency_pair, rate, valid_from) VALUES
    ('USD/BRL', 5.43210000, CURRENT_TIMESTAMP(6));
