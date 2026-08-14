alter table empresas add column op_simples_nacional varchar(20) not null default 'NAO_OPTANTE';
alter table empresas add column regime_especial_tributacao varchar(30) not null default 'NENHUM';
alter table empresas add column regime_apuracao_simples_nacional varchar(30);
