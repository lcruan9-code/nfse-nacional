create table contas (
    id            uuid primary key,
    nome          varchar(200) not null,
    status        varchar(20)  not null default 'ATIVA',
    criado_em     timestamptz  not null default now(),
    atualizado_em timestamptz  not null default now()
);

create table empresas (
    id                  uuid primary key,
    conta_id            uuid not null references contas(id),
    cnpj                varchar(14) not null,
    razao_social        varchar(200) not null,
    inscricao_municipal varchar(30),
    cod_mun_ibge        varchar(7) not null,
    status              varchar(20) not null default 'ATIVA',
    criado_em           timestamptz not null default now(),
    atualizado_em       timestamptz not null default now(),
    constraint uq_empresa_conta_cnpj unique (conta_id, cnpj)
);
create index ix_empresas_conta on empresas(conta_id);

create table api_keys (
    id            uuid primary key,
    conta_id      uuid not null references contas(id),
    ambiente      varchar(10) not null,
    key_hash      varchar(64) not null,
    key_prefix    varchar(20) not null,
    status        varchar(20) not null default 'ATIVA',
    criado_em     timestamptz not null default now(),
    ultimo_uso_em timestamptz,
    constraint uq_api_key_hash unique (key_hash)
);
create index ix_api_keys_conta on api_keys(conta_id);
