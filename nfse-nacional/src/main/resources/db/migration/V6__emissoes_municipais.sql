create table emissoes_municipais (
    id                  uuid primary key,
    conta_id            uuid not null,
    empresa_id          uuid not null,
    codigo_ibge         varchar(7) not null,
    provedor            varchar(20) not null,
    status              varchar(20) not null,
    numero_nfse         varchar(30),
    codigo_verificacao  varchar(60),
    protocolo           varchar(60),
    mensagens           text,
    xml_enviado         text,
    xml_retorno         text,
    criado_em           timestamptz not null
);

create index idx_emissoes_municipais_conta on emissoes_municipais (conta_id);
