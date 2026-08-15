-- Registro de roteamento por código IBGE (7 dígitos).
create table provedores_municipais (
    codigo_ibge      varchar(7) primary key,
    nome             varchar(120) not null,
    uf               varchar(2)   not null,
    tipo             varchar(20)  not null,   -- ADN | ABRASF_2X
    versao_abrasf    varchar(6),
    url_homolog      varchar(400),
    url_prod         varchar(400),
    estilo_envelope  varchar(30)  not null default 'NFSE_DADOS_MSG',
    algoritmo        varchar(10)  not null default 'SHA1'
);

-- Semeadura de AMOSTRA para o protótipo. O import completo do ACBrNFSeXServicos.ini
-- (5.571 cidades por IBGE) é uma fatia seguinte de engenharia de dados.
insert into provedores_municipais
    (codigo_ibge, nome, uf, tipo, versao_abrasf, url_homolog, url_prod, estilo_envelope, algoritmo) values
    ('4204608', 'Criciuma', 'SC', 'ABRASF_2X', '2.04',
     'https://homologacao.exemplo.gov.br/nfse/ws', 'https://nfse.exemplo.gov.br/ws', 'NFSE_DADOS_MSG', 'SHA1'),
    ('1501808', 'Breves', 'PA', 'ADN', null, null, null, 'NFSE_DADOS_MSG', 'SHA1');
