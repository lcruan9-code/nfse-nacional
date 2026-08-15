#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gera V8__importar_provedores_acbr.sql a partir do ACBrNFSeXServicos.ini.
Fonte (dados = fatos; codigo Pascal do ACBr e LGPL, NAO copiado):
  https://raw.githubusercontent.com/frones/ACBr/master/Fontes/ACBrDFe/ACBrNFSeX/ACBrNFSeXServicos.ini
Uso: python gerar_seed_provedores.py <ACBrNFSeXServicos.ini> <saida.sql>
"""
import re
import sys

def esc(s):
    return s.replace("'", "''") if s else s

def parse(ini_path):
    linhas = open(ini_path, encoding="latin-1").read().splitlines()
    reg, atual = [], None
    for ln in linhas:
        m = re.match(r"^\[(\d{7})\]\s*$", ln.strip())
        if m:
            if atual:
                reg.append(atual)
            atual = {"ibge": m.group(1)}
            continue
        if atual is None:
            continue
        if "=" in ln and not ln.strip().startswith(";"):
            k, _, v = ln.partition("=")
            atual.setdefault(k.strip(), v.strip())
    if atual:
        reg.append(atual)
    return reg

def gerar(reg):
    linhas = []
    for r in reg:
        prov = r.get("Provedor", "").strip()
        if not prov:
            continue
        tipo = "ADN" if prov == "PadraoNacional" else "NAO_SUPORTADO"
        nome = esc((r.get("Nome", "") or "")[:120])
        uf = esc((r.get("UF", "") or "")[:2])
        provedor = esc(prov[:40])
        versao = esc((r.get("Versao", "") or "")[:6]) or None
        url_prod = esc((r.get("ProRecepcionar", "") or "")[:400]) or None
        url_hom = esc((r.get("HomRecepcionar", "") or "")[:400]) or None
        def q(v):
            return "'%s'" % v if v is not None else "NULL"
        linhas.append("('%s','%s','%s','%s',%s,%s,%s,%s,'NFSE_DADOS_MSG','SHA1')"
                      % (r["ibge"], nome, uf, tipo, q(provedor), q(versao), q(url_hom), q(url_prod)))
    return linhas

COLS = "  (codigo_ibge, nome, uf, tipo, provedor, versao_abrasf, url_homolog, url_prod, estilo_envelope, algoritmo)\nvalues\n"

def main():
    ini, saida = sys.argv[1], sys.argv[2]
    valores = gerar(parse(ini))
    with open(saida, "w", encoding="utf-8", newline="\n") as f:
        f.write("-- V8: import do mapa de provedores municipais (cadastro ACBr, ACBrNFSeXServicos.ini).\n")
        f.write("-- Dados = fatos. Fonte: github.com/frones/ACBr .../ACBrNFSeXServicos.ini\n")
        f.write("-- Gerado por scripts/gerar_seed_provedores.py. tipo: ADN (PadraoNacional) | NAO_SUPORTADO.\n\n")
        f.write("delete from provedores_municipais;\n\n")
        f.write("insert into provedores_municipais\n" + COLS)
        for i in range(0, len(valores), 500):
            f.write(",\n".join(valores[i:i + 500]))
            f.write(";\n" if i + 500 >= len(valores) else ";\n\ninsert into provedores_municipais\n" + COLS)
    adn = sum(1 for v in valores if "'ADN'" in v)
    print("cidades: %d | ADN: %d | NAO_SUPORTADO: %d" % (len(valores), adn, len(valores) - adn))

if __name__ == "__main__":
    main()
