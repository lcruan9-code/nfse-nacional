# Decisões e fatos oficiais — NFS-e Nacional (SNNFSe)

> Registro das respostas às "perguntas em aberto" do spec (§15). Fonte: Portal Nacional NFS-e (gov.br).

## Versão do schema

- **Versão vigente:** v1.01
- **Publicação:** 2026-02-09
- **Pacote:** `NFSe-ESQUEMAS_XSD-v1.01-20260209.zip`
- **Origem oficial:** https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/
- **Link direto do ZIP:** https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/nfse-esquemas_xsd-v1-01-20260209.zip
- **Anexo I (leiaute DPS, xlsx):** https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/anexo_i-sefin_adn-dps_nfse-snnfse-v1-01-20260209.xlsx

## Endpoint (transmissão — passo futuro, fora do protótipo)

- **Produção:** `POST https://sefin.nfse.gov.br/SefinNacional/nfse`
- **Produção Restrita (homologação):** `https://sefin.producaorestrita.nfse.gov.br/SefinNacional/nfse`  ⏳ *confirmar path exato ao ler o manual da API*
- **Corpo:** XML da DPS **assinado → gzip → base64**
- **Resposta (síncrona):** chave de acesso de **50 posições** + NFS-e autorizada

## XSD raiz e elementos  (CONFIRMADO — pacote v1.01)

- **XSD raiz da DPS:** `DPS_v1.01.xsd`
- **Namespace alvo:** `http://www.sped.fazenda.gov.br/nfse`
- **Elemento raiz:** `DPS` (tipo `TCDPS`)
- **Estrutura:** `DPS` → `infDPS` (tipo `TCInfDPS`) + `ds:Signature` (sibling)
- **Includes/imports:** `DPS_v1.01.xsd` inclui `tiposComplexos_v1.01.xsd` (que puxa `tiposSimples_v1.01.xsd`)
  e importa `xmldsig-core-schema.xsd` (ns `http://www.w3.org/2000/09/xmldsig#`)
- Conjunto v1.01 completo copiado para esta pasta (10 arquivos `.xsd`).

## Assinatura digital da DPS  (parcialmente confirmado)

- ✅ **Tipo:** `enveloped` — `ds:Signature` é filho de `DPS`, irmão de `infDPS`.
- ✅ **Elemento âncora:** `infDPS`, atributo **`Id`** (required, tipo `TSIdDPS`).
- ✅ **Reference:** aponta para `#<Id do infDPS>`.
- ✅ **Assinatura presente no schema:** import do `xmldsig-core-schema.xsd` confirma XMLDSig.
- ⏳ **PENDENTE (não bloqueia o protótipo):** algoritmo exato (RSA-SHA1 × RSA-SHA256) e método
  de canonicalização (C14N inclusiva × exclusiva). Convenção SPED/Fazenda (NF-e/CT-e) =
  **RSA-SHA1 + C14N inclusiva + transform enveloped**; confirmar no manual da API antes da transmissão.
  Para o protótipo, o teste é assinar→verificar (round-trip), que passa com qualquer dos algoritmos padrão.

---
_Atualizar este arquivo conforme os artefatos oficiais forem lidos (Tasks 1.1 → 1.2)._
