create table emissoes (
    id            uuid primary key,
    conta_id      uuid not null references contas(id),
    empresa_id    uuid not null references empresas(id),
    ambiente      varchar(10) not null,
    status        varchar(20) not null,
    chave_acesso  varchar(50),
    numero_nfse   varchar(20),
    xml_dps       text,
    motivo        varchar(400),
    criado_em     timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);
create index ix_emissoes_conta on emissoes(conta_id);
create index ix_emissoes_empresa on emissoes(empresa_id);
