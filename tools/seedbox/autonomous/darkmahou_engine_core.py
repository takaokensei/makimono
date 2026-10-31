from __future__ import annotations
"""DarkMahou & Nyaa P2P Ingestion Engine Core v12 (Standalone / Headless)"""

import sys

# Headless shim for running outside Jupyter / Colab
try:
    import ipywidgets as widgets
    from IPython.display import display
except ImportError:
    class _DummyWidget:
        def __init__(self, *args, **kwargs):
            self.value = kwargs.get("value", "")
            self.description = kwargs.get("description", "")
        def observe(self, *args, **kwargs): pass
        def display(self, *args, **kwargs): pass
    class _DummyDisplay:
        def display(self, *args, **kwargs): pass
        def clear_output(self, *args, **kwargs): pass
    widgets = _DummyWidget()
    display = print



# --- Registro de erros e avisos (antes eram engolidos em silêncio; nada muda no fluxo) ---
if "LOG_ERROS" not in globals():
    LOG_ERROS = []


def _registrar_erro(exc, ctx=""):
    """Guarda o erro sem interromper o fluxo. Veja tudo com mostrar_log()."""
    import time as _t
    LOG_ERROS.append((_t.strftime("%H:%M:%S"), "erro", ctx, f"{type(exc).__name__}: {exc}"[:300]))


def _registrar_info(msg, ctx=""):
    """Registra um aviso (ex.: arquivo já existente) sem interromper o fluxo."""
    import time as _t
    LOG_ERROS.append((_t.strftime("%H:%M:%S"), "info", ctx, str(msg)[:300]))


def mostrar_log(ultimos=40):
    """Mostra os últimos erros e avisos desta sessão."""
    for linha in LOG_ERROS[-ultimos:]:
        print(" ", *linha)
    if not LOG_ERROS:
        print("Nenhum erro registrado nesta sessão.")



# ==============================================================================
# 1. title_resolver.py
# ==============================================================================
"""
title_resolver.py
=================
Normalizador semântico de temporadas e integração resiliente com Jikan API (MyAnimeList v4).
Resolve mapeamentos como 'Tensei shitara Slime Datta Ken 4th season' -> 'Tensei shitara Slime Datta Ken 4ª Temporada'.
"""


import re
import html
import time
import unicodedata
import requests
from dataclasses import dataclass
from typing import Optional, List, Dict, Any, Set

_CONTROL_CHARS_RE = re.compile(r'[\x00-\x1f\x7f]')  # inclui \n, \r, \t, ESC, NUL etc.
_FORBIDDEN_FS_CHARS_RE = re.compile(r'[\\/*?:"<>|]')

def sanitizar_nome_arquivo(nome: str, max_len: int = 200) -> str:
    """
    Sanitizacao robusta e centralizada para nomes de arquivo/pasta usados em
    todo o pipeline (staging local, nomes finais e queries da Drive API).
    Cobre:
    - Caracteres proibidos no Windows/Linux (\\ / * ? : " < > |)
    - Caracteres de controle (\\n, \\r, \\t, ESC, NUL) que sobrevivem a um
      re.sub simples e podem corromper queries da Drive API ou a saida
      do terminal (sequencias ANSI, por exemplo).
    - Normalizacao Unicode (NFKC) e limite de tamanho, evitando paths
      absurdamente longos vindos de titulos maliciosos/mal formados.
    """
    if not nome:
        return "sem_titulo"
    nome = unicodedata.normalize("NFKC", nome)
    nome = _CONTROL_CHARS_RE.sub("", nome)
    nome = _FORBIDDEN_FS_CHARS_RE.sub("", nome)
    nome = re.sub(r"\s+", " ", nome).strip(" .")
    if not nome:
        nome = "sem_titulo"
    return nome[:max_len]

ROMAN_MAP = {
    'I': 1, 'II': 2, 'III': 3, 'IV': 4, 'V': 5,
    'VI': 6, 'VII': 7, 'VIII': 8, 'IX': 9, 'X': 10
}

SEASON_REGEXES = [
    # 4ª Temporada, 4° Temporada, 4º Temporada, 4a Temporada
    r'(?i)\b(\d+)\s*[ªº°a]\s*temporada\b',
    # Temporada 4, Temp 4, Temp. 4
    r'(?i)\btemporada\s*(\d+)\b',
    r'(?i)\btemp\.?\s*(\d+)\b',
    # 4 Temporada
    r'(?i)\b(\d+)\s*temporada\b',
    # 4th season, 1st season, 2nd season, 3rd season
    r'(?i)\b(\d+)(?:st|nd|rd|th)\s*season\b',
    # Season 4
    r'(?i)\bseason\s*(\d+)\b',
    # S4, S04
    r'(?i)\bs0*(\d+)\b',
    # Part 3, Parte 3, Cour 2
    r'(?i)\b(?:part|parte|cour)\s*(\d+)\b',
    # Roman numerals: IV Temporada, Season IV, or trailing IV
    r'(?i)\b(?:temporada|season)\s*(I|II|III|IV|V|VI|VII|VIII|IX|X)\b',
    r'(?i)\s+(I|II|III|IV|V|VI|VII|VIII|IX|X)$'
]

@dataclass
class SeasonInfo:
    season_number: Optional[int]
    base_title: str
    original_query: str

def normalize_text(text: str) -> str:
    """Remove acentos, pontuação desnecessária e múltiplos espaços."""
    text = html.unescape(text)
    nfkd = unicodedata.normalize('NFKD', text)
    text = ''.join([c for c in nfkd if not unicodedata.combining(c)])
    text = re.sub(r'[\-_.:;]+', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def parse_season(query: str) -> SeasonInfo:
    clean_q = query.strip()
    detected_season = None
    base_title = clean_q

    for pattern in SEASON_REGEXES:
        m = re.search(pattern, clean_q)
        if m:
            val = m.group(1)
            if val.upper() in ROMAN_MAP:
                detected_season = ROMAN_MAP[val.upper()]
            else:
                try:
                    detected_season = int(val)
                except ValueError:
                    continue
            base_title = clean_q[:m.start()] + clean_q[m.end():]
            base_title = re.sub(r'\s+', ' ', base_title).strip(' -:_')
            break

    return SeasonInfo(
        season_number=detected_season,
        base_title=base_title if base_title else query,
        original_query=query
    )

def generate_search_variants(query: str) -> List[str]:
    info = parse_season(query)
    variants = [query.strip()]

    if info.season_number is not None:
        s = info.season_number
        base = info.base_title
        pt_variants = [
            f"{base} {s}ª Temporada",
            f"{base} {s}a Temporada",
            f"{base} {s} Temporada",
            f"{base} {s}° Temporada",
            f"{base} Temporada {s}",
            f"{base} {s}nd Season" if s == 2 else (f"{base} {s}rd Season" if s == 3 else f"{base} {s}th Season"),
            f"{base} Season {s}",
            f"{base} S{s:02d}",
            f"{base} {s}",
            base
        ]
        for v in pt_variants:
            if v not in variants:
                variants.append(v)
    else:
        clean = normalize_text(query)
        if clean not in variants:
            variants.append(clean)

    return variants

class JikanResolver:
    # Tenrai (https://tenrai.org) e' um sucessor "drop-in" do Jikan v4: schema
    # de resposta compativel (mesmos campos "data", "title", "episodes" etc.),
    # mantido ativamente e recomendado pela propria documentacao do Tenrai como
    # substituto direto -- o Jikan classico segue existindo mas com relatos
    # frequentes de instabilidade/depreciacao. Usamos Tenrai como fonte
    # primaria e mantemos o Jikan classico como fallback (nao removido), caso
    # o Tenrai fique fora do ar -- mesmo padrao de resiliencia ja usado no
    # resto do notebook (ex.: Service Account como fallback do OAuth).
    BASE_URL = "https://api.tenrai.org/v1"
    JIKAN_FALLBACK_URL = "https://api.jikan.moe/v4"
    HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}

    def __init__(self, timeout: float = 3.0):
        self.timeout = timeout
        self._cache: Dict[str, List[Dict[str, Any]]] = {}

    def search_anime(self, query: str) -> List[Dict[str, Any]]:
        clean = query.lower().strip()
        if clean in self._cache:
            return self._cache[clean]

        # 1. Tentar Tenrai (sucessor do Jikan, schema compativel)
        try:
            resp = requests.get(
                f"{self.BASE_URL}/anime",
                params={"q": query, "limit": 4},
                headers=self.HEADERS,
                timeout=self.timeout
            )
            if resp.status_code == 200:
                data = resp.json().get("data", [])
                if data:
                    self._cache[clean] = data
                    return data
        except Exception as _exc:
            _registrar_erro(_exc)

        # 2. Fallback Jikan classico (caso o Tenrai esteja fora do ar)
        try:
            resp = requests.get(
                f"{self.JIKAN_FALLBACK_URL}/anime",
                params={"q": query, "limit": 4},
                headers=self.HEADERS,
                timeout=self.timeout
            )
            if resp.status_code == 200:
                data = resp.json().get("data", [])
                if data:
                    self._cache[clean] = data
                    return data
        except Exception as _exc:
            _registrar_erro(_exc)

        # 3. Fallback Kitsu API (resiliente quando Tenrai/Jikan estiverem fora)
        try:
            resp = requests.get(
                "https://kitsu.io/api/edge/anime",
                params={"filter[text]": query, "page[limit]": 4},
                headers=self.HEADERS,
                timeout=self.timeout + 1.5
            )
            if resp.status_code == 200:
                adapted = []
                for it in resp.json().get("data", []):
                    attr = it.get("attributes", {})
                    t_canon = attr.get("canonicalTitle")
                    t_en = (attr.get("titles") or {}).get("en") or t_canon
                    start = attr.get("startDate", "")
                    year = int(start.split("-")[0]) if (start and start.split("-")[0].isdigit()) else None
                    eps = attr.get("episodeCount")
                    score = float(attr.get("averageRating", 0)) / 10.0 if attr.get("averageRating") else None
                    adapted.append({
                        "title": t_canon,
                        "title_english": t_en,
                        "title_synonyms": attr.get("abbreviatedTitles", []),
                        "episodes": eps,
                        "year": year,
                        "score": round(score, 1) if score else None
                    })
                if adapted:
                    self._cache[clean] = adapted
                    return adapted
        except Exception as _exc:
            _registrar_erro(_exc)

        return []

    def get_romaji_and_synonyms(self, query: str) -> List[str]:
        results = self.search_anime(query)
        titles = []
        for item in results:
            if item.get("title"):
                titles.append(item["title"])
            if item.get("title_english") and item["title_english"] not in titles:
                titles.append(item["title_english"])
            for syn in item.get("title_synonyms", []):
                if syn and syn not in titles:
                    titles.append(syn)
        return titles

def get_anime_suggestions(query: str) -> List[Dict[str, Any]]:
    jikan = JikanResolver(timeout=3.0)
    items = jikan.search_anime(query)
    suggestions = []
    for it in items:
        t = it.get("title") or it.get("title_english")
        eps = it.get("episodes")
        year = it.get("year")
        score = it.get("score")
        eps_str = f"{eps} eps" if eps else "Em andamento"
        ano_str = f"{year}" if year else "Ano ?"
        score_str = f"⭐ {score}" if score else ""
        label = f"{t} ({ano_str} · {eps_str} {score_str})".strip()
        suggestions.append({
            "title": t,
            "label": label,
            "episodes": eps,
            "year": year,
            "score": score,
            "mal_id": it.get("mal_id")  # None quando resolvido via fallback Kitsu
        })
    return suggestions

def score_anime_match(candidate_name: str, target_base: str, target_season: Optional[int]) -> float:
    c_info = parse_season(candidate_name)
    cand_norm = normalize_text(candidate_name).lower()
    base_norm = normalize_text(target_base).lower()
    cand_base_norm = normalize_text(c_info.base_title).lower()

    if cand_base_norm == base_norm:
        score = 80.0
    else:
        base_tokens = set(base_norm.split())
        cand_tokens = set(cand_norm.split())
        union_len = len(base_tokens | cand_tokens)
        jaccard = len(base_tokens & cand_tokens) / max(union_len, 1)
        score = jaccard * 50.0

    unwanted_keywords = ['movie', 'film', 'filme', 'ova', 'live action', 'special', 'especial', 'heroines', 'serie', 'spin-off']
    for kw in unwanted_keywords:
        if kw in cand_norm and kw not in base_norm:
            score -= 25.0

    if target_season is not None:
        if c_info.season_number == target_season:
            score += 50.0
        elif c_info.season_number is not None and c_info.season_number != target_season:
            score -= 40.0
        else:
            if target_season == 1:
                score += 20.0
            else:
                score -= 30.0
    else:
        if c_info.season_number is None or c_info.season_number == 1:
            score += 20.0
        else:
            score -= 15.0

    return max(0.0, score)


# Tipos de relacao do Jikan/MyAnimeList considerados "mesma franquia" para fins
# de download. Deliberadamente EXCLUIDOS por padrao:
#   - "Adaptation": aponta para mangá/light novel (nao e' um anime baixavel)
#   - "Character": apenas compartilha um personagem, historia nao relacionada
#   - "Other": vago demais, alta taxa de falsos positivos
# "Summary" (recaps/compilacoes) fica de fora por padrao pois normalmente
# duplica conteudo ja coberto pela serie principal.
RELACOES_FRANQUIA_INCLUIR = {
    "Sequel", "Prequel", "Parent story", "Side story", "Full story",
    "Alternative version", "Alternative setting", "Spin-off"
}

def _jikan_rate_limit_sleep() -> None:
    """Pequena pausa entre chamadas ao Jikan para respeitar o limite documentado
    da API (historicamente citado como 3 req/s) com folga confortavel."""
    time.sleep(0.4)

# Tenrai (schema compativel com Jikan v4) como fonte primaria da travessia de
# franquia, com o Jikan classico como fallback caso o Tenrai esteja fora do ar
# -- mesmo racional aplicado em JikanResolver acima.
_TENRAI_BASE_URL = "https://api.tenrai.org/v1"
_JIKAN_FALLBACK_BASE_URL = "https://api.jikan.moe/v4"

def _get_com_fallback_tenrai_jikan(caminho: str, timeout: float) -> Optional[Dict[str, Any]]:
    """
    Faz um GET em `{BASE}/{caminho}`, tentando primeiro o Tenrai e caindo para
    o Jikan classico se o Tenrai falhar ou estiver fora do ar. Trata 429 com
    uma unica nova tentativa apos pequena pausa, em cada fonte.
    """
    for base_url in (_TENRAI_BASE_URL, _JIKAN_FALLBACK_BASE_URL):
        try:
            r = requests.get(f"{base_url}/{caminho}", timeout=timeout)
            if r.status_code == 429:
                time.sleep(2.0)
                r = requests.get(f"{base_url}/{caminho}", timeout=timeout)
            if r.status_code == 200:
                return r.json()
        except Exception:
            continue
    return None

def buscar_relacoes_jikan(mal_id: int, timeout: float = 6.0) -> List[Dict[str, Any]]:
    """
    Busca as relacoes DIRETAS (sequel, prequel, side story etc.) de um anime
    via Tenrai (com fallback para o Jikan classico). IMPORTANTE: este endpoint
    so' retorna os vizinhos imediatos da pagina consultada -- para descobrir
    uma franquia inteira (e nao so' o proximo/anterior mais perto), e' preciso
    percorrer o grafo em varios saltos (ver descobrir_franquia_jikan), ja que
    nenhuma das duas fontes faz essa travessia por conta propria.
    """
    _jikan_rate_limit_sleep()
    resposta = _get_com_fallback_tenrai_jikan(f"anime/{mal_id}/relations", timeout)
    if resposta is None:
        return []
    blocos = resposta.get("data", []) or []
    saida = []
    for bloco in blocos:
        relacao = bloco.get("relation", "")
        for entry in bloco.get("entry", []) or []:
            if entry.get("type") == "anime" and entry.get("mal_id"):
                saida.append({
                    "relation": relacao,
                    "mal_id": entry["mal_id"],
                    "name": entry.get("name", "")
                })
    return saida

def buscar_detalhes_anime_jikan(mal_id: int, timeout: float = 6.0) -> Optional[Dict[str, Any]]:
    """Busca titulo, tipo (TV/Movie/OVA/ONA/Special), episodios, data de
    exibicao e nota de um anime pelo seu mal_id via Tenrai (com fallback para
    o Jikan classico) -- usado tanto para o anime semente quanto para cada no
    descoberto na travessia da franquia."""
    _jikan_rate_limit_sleep()
    resposta = _get_com_fallback_tenrai_jikan(f"anime/{mal_id}", timeout)
    if resposta is None:
        return None
    d = resposta.get("data", {}) or {}
    aired_from = (d.get("aired") or {}).get("from")
    ano = d.get("year")
    if ano is None and aired_from:
        try:
            ano = int(aired_from[:4])
        except (ValueError, TypeError):
            ano = None
    return {
        "mal_id": mal_id,
        "title": d.get("title") or d.get("title_english") or f"MAL#{mal_id}",
        "type": d.get("type") or "?",
        "episodes": d.get("episodes"),
        "aired_from": aired_from,
        "year": ano,
        "score": d.get("score"),
    }

def descobrir_franquia_jikan(mal_id_inicial: int, max_nodes: int = 30) -> List[Dict[str, Any]]:
    """
    Percorre em largura (BFS) o grafo de relacoes do Jikan/MyAnimeList a partir
    de um anime inicial, descobrindo toda a franquia (temporadas, filmes, OVAs,
    side stories etc.) e retornando a lista ordenada CRONOLOGICAMENTE pela data
    de exibicao (aired.from), com fallback para o ano quando a data exata nao
    estiver disponivel.

    Por que BFS multi-salto e' necessario: o endpoint /relations do Jikan so'
    retorna os vizinhos diretos da pagina consultada (confirmado na
    documentacao/issues do proprio projeto) -- ele NAO expande a arvore
    inteira sozinho. Uma franquia de 3 temporadas pode exigir consultar as
    relacoes da Temporada 2 para descobrir a Temporada 3, que nao
    necessariamente aparece nas relacoes da Temporada 1.

    max_nodes limita o tamanho da travessia para nao gerar uma quantidade
    excessiva de chamadas em franquias gigantescas (ex: Gundam, Fate) --
    prioriza os nos descobertos primeiro (mais proximos do anime original).
    """
    visitados: Set[int] = {mal_id_inicial}
    fila: List[int] = [mal_id_inicial]
    detalhes: Dict[int, Dict[str, Any]] = {}

    inicial = buscar_detalhes_anime_jikan(mal_id_inicial)
    if inicial:
        detalhes[mal_id_inicial] = inicial

    while fila and len(visitados) < max_nodes:
        atual = fila.pop(0)
        for rel in buscar_relacoes_jikan(atual):
            if rel["relation"] not in RELACOES_FRANQUIA_INCLUIR:
                continue
            vizinho_id = rel["mal_id"]
            if vizinho_id in visitados:
                continue
            visitados.add(vizinho_id)
            fila.append(vizinho_id)
            if len(visitados) >= max_nodes:
                break

    for mid in visitados:
        if mid not in detalhes:
            info = buscar_detalhes_anime_jikan(mid)
            if info:
                detalhes[mid] = info

    lista = list(detalhes.values())

    def _chave_ordenacao(item: Dict[str, Any]) -> str:
        if item.get("aired_from"):
            return str(item["aired_from"])
        if item.get("year"):
            return f"{item['year']}-01-01"
        return "9999-99-99"  # sem data conhecida: joga pro fim da lista

    lista.sort(key=_chave_ordenacao)
    return lista


# ==============================================================================
# 2. aria2_engine.py (com Watchdog e parâmetros otimizados)
# ==============================================================================
"""
aria2_engine.py (P2P Ingestion Toolkit v6.5 - Seguro contra Kernel Termination)
================================================================================
Engine com staging isolado por job (job_<uuid>), cancelamento cooperativo via Event,
suporte cross-platform a processos (Windows/Linux) com sessões isoladas (start_new_session),
watchdog ativo anti-travamento (timeout inteligente para 0 seeds) e tradução de exit codes.
"""


import hashlib
import json
import logging
import os
import re
import secrets
import shutil
import signal
import subprocess
import sys
import threading
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Optional, Dict, Any, List

logger = logging.getLogger("aria2_engine")

PUBLIC_TRACKERS = (
    "http://tracker.opentrackr.org:1337/announce,"
    "udp://tracker.opentrackr.org:1337/announce,"
    "udp://open.stealth.si:80/announce,"
    "udp://tracker.torrent.eu.org:451/announce,"
    "udp://explodie.org:6969/announce,"
    "udp://tracker.moeking.me:6969/announce,"
    "http://nyaa.tracker.wf:7777/announce,"
    "udp://open.demonii.com:1337/announce,"
    "udp://tracker.coppersurfer.tk:6969/announce,"
    "udp://p4p.arenabg.com:1337/announce"
)

class Protocol(str, Enum):
    MAGNET = "magnet"
    HTTP = "http"
    GDRIVE = "gdrive"

@dataclass(frozen=True)
class DownloadOption:
    label: str
    url: str
    protocol: Protocol
    quality: Optional[str] = None
    filename_hint: Optional[str] = None
    collection: Optional[str] = None
    expected_sha256: Optional[str] = None
    metadata: dict = field(default_factory=dict)

    def stable_key(self) -> str:
        return self.metadata.get("manifest_key", self.url)

@dataclass
class Telemetry:
    raw_line: str
    percent: Optional[float] = None
    downloaded: Optional[str] = None
    total: Optional[str] = None
    speed: Optional[str] = None
    peers: Optional[int] = None
    eta: Optional[str] = None
    downloaded_bytes: int = 0
    status_warning: Optional[str] = None

@dataclass
class DownloadResult:
    option: DownloadOption
    success: bool
    final_path: Optional[Path] = None
    return_code: Optional[int] = None
    error: Optional[str] = None
    elapsed_seconds: float = 0.0
    cancelled: bool = False
    attempts: int = 1
    warning: Optional[str] = None

_ARIA2_ACTIVE_RE = re.compile(
    r"\[#\w+\s+(?:SIZE:)?(?P<downloaded>[\d.]+\w+)/(?P<total>[\d.]+\w+)"
    r"\((?P<percent>\d+)%\)\s+CN:(?P<peers>\d+)"
    r"(?:\s+(?:SD|SEED):(?P<seeds>\d+))?"
    r"(?:\s+[A-Za-z]+:\S+)*"
    r"\s+DL:(?P<speed>[\d.]+[A-Za-z]+(?:/[A-Za-z]+)?)"
    r"(?:\s+UL:[\d.]+[A-Za-z]+(?:/[A-Za-z]+)?(?:\([^)]*\))?)?"
    r"(?:\s+ETA:(?P<eta>[^\]\s]+))?"
)
_ARIA2_META_RE = re.compile(r"\[#\w+\s+(?:[\d.]+\w+/[\d.]+\w+)?\s*CN:(?P<peers>\d+)")

def _parse_size_bytes(size_str: Optional[str]) -> int:
    if not size_str:
        return 0
    m = re.match(r"([\d.]+)\s*([KMGTkmgt]?[iI]?[Bb]?)", size_str.strip())
    if not m:
        return 0
    try:
        val = float(m.group(1))
        unit = m.group(2).upper()
        mult = 1
        if "K" in unit:
            mult = 1024
        elif "M" in unit:
            mult = 1024 * 1024
        elif "G" in unit:
            mult = 1024 * 1024 * 1024
        elif "T" in unit:
            mult = 1024 * 1024 * 1024 * 1024
        return int(val * mult)
    except Exception:
        return 0

def parse_aria2_line(line: str) -> Optional[Telemetry]:
    m = _ARIA2_ACTIVE_RE.search(line)
    if m:
        d_bytes = _parse_size_bytes(m.group("downloaded"))
        seeds_cnt = int(m.group("seeds")) if m.group("seeds") else None
        peers_cnt = int(m.group("peers")) if m.group("peers") else None
        total_p = peers_cnt
        if seeds_cnt and peers_cnt is not None:
            total_p = max(peers_cnt, seeds_cnt)
        return Telemetry(
            raw_line=line.strip(),
            percent=float(m.group("percent")) if m.group("percent") else None,
            downloaded=m.group("downloaded"),
            total=m.group("total"),
            speed=m.group("speed"),
            peers=total_p,
            eta=m.group("eta"),
            downloaded_bytes=d_bytes,
        )
    m_meta = _ARIA2_META_RE.search(line)
    if m_meta:
        peers_cnt = int(m_meta.group("peers"))
        return Telemetry(
            raw_line=line.strip(),
            percent=0.0,
            peers=peers_cnt,
            speed="0B/s",
            eta=f"Buscando peers ({peers_cnt})...",
            downloaded_bytes=0,
            status_warning="Buscando peers..." if peers_cnt > 0 else "0 peers conectados",
        )
    return None

ARIA2_EXIT_MESSAGES: dict = {
    0: "Sucesso", 1: "Erro generico", 2: "Timeout",
    3: "Recurso nao encontrado", 6: "Falha de rede",
    7: "Sem seeds/peers suficientes (timeout de busca)",
    9: "Espaco em disco insuficiente", 12: "Checksum SHA256 nao confere",
    19: "Falha DNS", 23: "Erro HTTP retornado pelo servidor",
    24: "Rede intermitente", 27: "Erro no magnet link",
}
DEFAULT_RETRYABLE_CODES = {1, 2, 6, 7, 19, 23, 24}

def describe_exit_code(code: Optional[int]) -> str:
    if code is None:
        return "sem codigo de saida"
    return ARIA2_EXIT_MESSAGES.get(code, f"codigo desconhecido ({code})")

_VIDEO_EXTS_PARA_INSPECAO = {".mkv", ".mp4", ".avi", ".webm", ".mov"}
_CODIGOS_PTBR_FFPROBE = {"por", "pt", "pt-br", "ptb", "pob"}

def _escrever_atomico(caminho, dados) -> None:
    """Grava em arquivo temporário e troca de uma vez: uma queda no meio da escrita não corrompe o original."""
    caminho = Path(caminho)
    caminho.parent.mkdir(parents=True, exist_ok=True)
    tmp = caminho.with_name(f".{caminho.name}.{os.getpid()}.{uuid.uuid4().hex[:6]}.tmp")
    try:
        if isinstance(dados, str):
            tmp.write_text(dados, encoding="utf-8")
        else:
            tmp.write_bytes(dados)
        tmp.replace(caminho)
    except BaseException:
        tmp.unlink(missing_ok=True)
        raise


def verificar_midia(caminho, timeout: float = 60.0):
    """Confere com ffprobe se o arquivo é mídia legível (duração > 0 e pelo menos um fluxo).
    Retorna (ok, motivo). Sem ffprobe instalado, não bloqueia o fluxo."""
    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        return True, "ffprobe ausente: verificação pulada"
    try:
        proc = subprocess.run(
            [ffprobe, "-v", "error", "-print_format", "json", "-show_format", "-show_streams", str(caminho)],
            capture_output=True, text=True, timeout=timeout,
        )
    except subprocess.TimeoutExpired:
        return False, "ffprobe excedeu o tempo limite"
    except OSError as exc:
        return False, f"ffprobe não executou: {exc}"
    if proc.returncode != 0:
        return False, f"ffprobe falhou: {(proc.stderr or '').strip()[:160]}"
    try:
        dados = json.loads(proc.stdout or "{}")
    except json.JSONDecodeError:
        return False, "saída do ffprobe inválida"
    duracao = float((dados.get("format") or {}).get("duration") or 0)
    if not dados.get("streams") or duracao <= 0:
        return False, "sem fluxos de mídia ou duração zero"
    return True, "ok"


def verificar_legendas_arquivo(caminho_arquivo: Path, timeout: float = 20.0) -> Dict[str, Any]:
    """
    Inspeciona as trilhas de legenda REAIS embutidas no arquivo de video via
    ffprobe (parte do ffmpeg). Esta e' a UNICA forma de saber com certeza quais
    idiomas de legenda um arquivo baixado realmente contem -- ao contrario da
    deteccao por titulo/metadado da release (usada antes do download, em
    _confianca_ptbr), que e' sempre uma estimativa, nunca uma garantia.

    Motivo desta funcao existir: releases anunciadas como "Multi-Sub" no titulo
    podem nao incluir portugues (ou nenhuma legenda) sem que isso fique evidente
    ate' o arquivo ja' estar baixado e o usuario tentar assistir.

    Retorna um dict:
        verificado: bool     -- False se o ffprobe nao pode ser executado (nao
                                 instalado, arquivo corrompido, formato nao
                                 suportado para inspecao etc.) -- neste caso a
                                 ausencia de confirmacao NAO deve ser lida como
                                 "sem legenda", apenas como "nao verificado".
        tem_legenda: bool    -- ao menos uma trilha de legenda foi encontrada.
        idiomas: List[str]   -- codigos de idioma das trilhas (ex: ["por","eng"]).
        tem_ptbr: bool       -- True se portugues esta entre os idiomas.
        erro: Optional[str]  -- motivo da falha, quando verificado=False.
    """
    resultado: Dict[str, Any] = {
        "verificado": False, "tem_legenda": False,
        "idiomas": [], "tem_ptbr": False, "erro": None
    }

    caminho_arquivo = Path(caminho_arquivo)

    if caminho_arquivo.suffix.lower() not in _VIDEO_EXTS_PARA_INSPECAO:
        resultado["erro"] = f"formato '{caminho_arquivo.suffix}' nao suportado para inspecao de legendas"
        return resultado

    if not caminho_arquivo.exists():
        resultado["erro"] = "arquivo nao encontrado no caminho informado"
        return resultado

    ffprobe_bin = shutil.which("ffprobe")
    if not ffprobe_bin:
        resultado["erro"] = "ffprobe nao encontrado no PATH (pacote ffmpeg nao instalado)"
        return resultado

    try:
        proc = subprocess.run(
            [
                ffprobe_bin, "-v", "quiet", "-print_format", "json",
                "-show_streams", "-select_streams", "s",
                str(caminho_arquivo)
            ],
            capture_output=True, text=True, timeout=timeout
        )
    except subprocess.TimeoutExpired:
        resultado["erro"] = f"ffprobe excedeu o timeout de {timeout}s"
        return resultado
    except Exception as e:
        resultado["erro"] = f"falha ao executar ffprobe: {e}"
        return resultado

    if proc.returncode != 0:
        resultado["erro"] = f"ffprobe retornou codigo {proc.returncode}: {(proc.stderr or '').strip()[:200]}"
        return resultado

    try:
        data = json.loads(proc.stdout or "{}")
    except json.JSONDecodeError as e:
        resultado["erro"] = f"saida do ffprobe nao e' JSON valido: {e}"
        return resultado

    streams = data.get("streams", []) or []
    idiomas: List[str] = []
    for s in streams:
        tags = s.get("tags", {}) or {}
        lang = str(tags.get("language", "")).lower().strip()
        if lang and lang not in idiomas:
            idiomas.append(lang)

    resultado["verificado"] = True
    resultado["tem_legenda"] = len(streams) > 0
    resultado["idiomas"] = idiomas
    resultado["tem_ptbr"] = any(l in _CODIGOS_PTBR_FFPROBE for l in idiomas)
    return resultado

class Aria2Engine:
    def __init__(
        self,
        staging_dir: Optional[str | Path] = None,
        aria2c_bin: Optional[str] = None,
        extra_trackers: str = PUBLIC_TRACKERS,
        bt_stop_timeout: int = 60,
        metadata_timeout: float = 75.0,
        stall_timeout_com_peers: float = 180.0,
        stall_timeout_sem_peers: float = 45.0,
        stall_aviso_fracao: float = 0.6,
        stall_timeout: Optional[float] = None
    ):
        """
        stall_timeout_com_peers: tolerancia (segundos) sem crescimento de bytes
            quando o enxame ainda tem peers ativos -- comportamento normal de
            P2P inclui platos de dezenas de segundos (peca rara, poucos
            seeders trocando entre si), especialmente perto do fim do arquivo.
            Matar cedo demais aqui derruba downloads saudaveis.
        stall_timeout_sem_peers: tolerancia bem mais curta quando os peers
            ativos caem a zero durante o download -- ai sim o enxame morreu
            de verdade e nao faz sentido esperar tanto.
        stall_aviso_fracao: fracao do limite aplicavel em que um aviso e'
            emitido via on_progress ANTES de matar o processo, para a UI
            poder sinalizar "aguardando peers..." em vez de o download morrer
            do nada sem nenhum indicio previo.
        stall_timeout: parametro legado (compatibilidade). Se fornecido,
            sobrescreve os dois limiares acima com o mesmo valor fixo,
            reproduzindo o comportamento antigo caso seja explicitamente
            solicitado.
        """
        if staging_dir is None:
            staging_dir = Path("/content/downloads_temp") if (sys.platform != "win32" and os.path.exists("/content")) else Path("./downloads_temp")
        self.staging_dir = Path(staging_dir)
        self.staging_dir.mkdir(parents=True, exist_ok=True)

        if aria2c_bin is None:
            found = shutil.which("aria2c")
            if not found and sys.platform == "win32":
                cands = [
                    Path("C:/darkmahou/.venv/Scripts/aria2c.exe"),
                    Path(sys.prefix) / "Scripts" / "aria2c.exe"
                ]
                for c in cands:
                    if c.exists():
                        found = str(c)
                        break
            self.aria2c_bin = found or "aria2c"
        else:
            self.aria2c_bin = aria2c_bin

        self.extra_trackers = extra_trackers
        self.bt_stop_timeout = bt_stop_timeout
        self.metadata_timeout = metadata_timeout
        if stall_timeout is not None:
            self.stall_timeout_com_peers = stall_timeout
            self.stall_timeout_sem_peers = stall_timeout
        else:
            self.stall_timeout_com_peers = stall_timeout_com_peers
            self.stall_timeout_sem_peers = stall_timeout_sem_peers
        self.stall_aviso_fracao = stall_aviso_fracao

    def _build_command(self, option: DownloadOption, job_dir: Path) -> list:
        cmd = [
            self.aria2c_bin,
            "--dir", str(job_dir),
            "--file-allocation=none",
            "--enable-dht=true",
            "--enable-dht6=false",
            "--dht-listen-port=6881-6999",
            "--listen-port=6881-6999",
            "--bt-enable-lpd=true",
            f"--bt-tracker={self.extra_trackers}",
            f"--bt-stop-timeout={self.bt_stop_timeout}",
            "--bt-tracker-connect-timeout=5",
            "--bt-tracker-timeout=5",
            "--bt-max-peers=100",
            "--bt-request-peer-speed-limit=50K",
            "--seed-time=0",
            "--max-connection-per-server=16",
            "--summary-interval=1",
            "--show-console-readout=true",
            "--console-log-level=notice",
            "--auto-file-renaming=false",
            "--allow-overwrite=true",
            "--peer-id-prefix=-TR2940-",
            "--user-agent=Transmission/2.94",
            "--bt-prioritize-piece=head,tail",
            "--max-overall-upload-limit=15K",
            "--bt-remove-unselected-file=true",
        ]
        is_batch = option.metadata.get("episodio") == "Batch" or option.metadata.get("is_batch", False)
        if option.filename_hint and not is_batch:
            # NOTA: para torrents BitTorrent com multiplos arquivos internos (.mkv + .nfo +
            # pasta de legendas), o aria2c NAO renomeia de forma confiavel via --out; ele so
            # e' aplicado com garantia em downloads HTTP/FTP/GDrive de arquivo unico. Para
            # magnet/torrent multi-arquivo a selecao correta do video e' feita depois, em
            # _resolve_downloaded_file, que agora tambem sinaliza ambiguidade quando ha mais
            # de um candidato de video plausivel (ver campo DownloadResult.error com aviso).
            cmd += ["--out", option.filename_hint]
        cmd.append(option.url)
        return cmd

    def download(
        self,
        option: DownloadOption,
        dest_dir: str | Path,
        on_progress: Optional[Callable[[Telemetry], None]] = None,
        cancel_event: Optional[threading.Event] = None,
    ) -> DownloadResult:
        dest_dir = Path(dest_dir)
        dest_dir.mkdir(parents=True, exist_ok=True)

        job_dir = self.staging_dir / f"job_{uuid.uuid4().hex[:8]}"
        job_dir.mkdir(parents=True, exist_ok=True)
        start = time.monotonic()

        if option.protocol == Protocol.GDRIVE:
            try:
                import gdown
                out_path = dest_dir / (option.filename_hint or f"{option.label}.mp4")
                gdown.download(option.url, str(out_path), quiet=False, fuzzy=True)
                shutil.rmtree(job_dir, ignore_errors=True)
                elapsed = time.monotonic() - start
                if out_path.exists() and out_path.stat().st_size > 1024:
                    return DownloadResult(option, True, final_path=out_path, return_code=0, elapsed_seconds=elapsed)
            except Exception as ge:
                shutil.rmtree(job_dir, ignore_errors=True)
                return DownloadResult(option, False, error=f"Erro gdown: {ge}", elapsed_seconds=time.monotonic() - start)

        cmd = self._build_command(option, job_dir)

        creationflags = 0
        if sys.platform == "win32":
            creationflags = subprocess.CREATE_NEW_PROCESS_GROUP

        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            start_new_session=(sys.platform != "win32"),
            creationflags=creationflags
        )

        last_progress_time = time.monotonic()
        last_downloaded = 0
        last_peers = 0
        last_telem_time = time.monotonic()
        aviso_estagnacao_emitido = False

        try:
            assert process.stdout is not None
            while True:
                if cancel_event is not None and cancel_event.is_set():
                    self._terminate(process)
                    shutil.rmtree(job_dir, ignore_errors=True)
                    return DownloadResult(
                        option, False, error="Cancelado pelo usuario",
                        cancelled=True, elapsed_seconds=time.monotonic() - start
                    )

                line = process.stdout.readline()
                if line == "":
                    try:
                        process.wait(timeout=2)
                    except subprocess.TimeoutExpired:
                        continue
                    break

                telem = parse_aria2_line(line)
                now = time.monotonic()
                if telem:
                    last_telem_time = now
                    if telem.peers is not None:
                        last_peers = telem.peers

                    if telem.downloaded_bytes > last_downloaded:
                        last_downloaded = telem.downloaded_bytes
                        last_progress_time = now
                        aviso_estagnacao_emitido = False

                    # Watchdog 1: Sem seeds apos limite_espera (75s para 0 peers, +20s se peers > 0 negociando)
                    limite_espera = self.metadata_timeout if last_peers == 0 else (self.metadata_timeout + 20.0)
                    if last_downloaded == 0 and (now - start) > limite_espera:
                        self._terminate(process)
                        shutil.rmtree(job_dir, ignore_errors=True)
                        return DownloadResult(
                            option, False,
                            error=f"Timeout: Sem seeds/dados ativos apos {int(limite_espera)}s (swarm inativo)",
                            elapsed_seconds=now - start
                        )

                    # Watchdog 2: Estagnado apos ja ter comecado a baixar. O limiar e'
                    # adaptativo: se ainda ha peers ativos, o enxame esta vivo (so lento
                    # -- comportamento normal de P2P, especialmente perto do fim do
                    # arquivo ou com poucos seeders), entao a tolerancia e' bem maior do
                    # que quando os peers cairam a zero (ai sim o enxame morreu de verdade).
                    if last_downloaded > 0:
                        limite_estagnacao = self.stall_timeout_com_peers if last_peers > 0 else self.stall_timeout_sem_peers
                        tempo_parado = now - last_progress_time

                        if tempo_parado > limite_estagnacao:
                            self._terminate(process)
                            shutil.rmtree(job_dir, ignore_errors=True)
                            motivo_peers = f"{last_peers} peer(s) ativos" if last_peers > 0 else "0 peers ativos"
                            return DownloadResult(
                                option, False,
                                error=f"Download estagnado por mais de {int(limite_estagnacao)}s sem novos dados ({motivo_peers})",
                                elapsed_seconds=now - start
                            )

                        # Aviso previo: ao cruzar a fracao configurada do limite, sinaliza
                        # via on_progress ANTES de matar, sobrescrevendo o campo 'eta' (e' o
                        # unico campo de texto livre realmente renderizado no card de estado
                        # "downloading" do JobBoard) para a UI mostrar que o download esta em
                        # risco em vez de simplesmente morrer sem nenhum indicio previo.
                        if tempo_parado > (limite_estagnacao * self.stall_aviso_fracao) and not aviso_estagnacao_emitido:
                            aviso_estagnacao_emitido = True
                            restante_estagnacao = max(0, int(limite_estagnacao - tempo_parado))
                            if on_progress:
                                on_progress(Telemetry(
                                    raw_line=telem.raw_line,
                                    percent=telem.percent,
                                    downloaded=telem.downloaded,
                                    total=telem.total,
                                    speed=telem.speed,
                                    peers=last_peers,
                                    downloaded_bytes=telem.downloaded_bytes,
                                    eta=f"⚠️ Sem dados há {int(tempo_parado)}s (encerra em {restante_estagnacao}s)"
                                ))

                    if last_downloaded == 0:
                        restante = max(0, int(limite_espera - (now - start)))
                        telem.eta = f"Procurando seeds ({restante}s)..." if last_peers == 0 else f"Negociando metadados ({restante}s)..."

                    if on_progress:
                        on_progress(telem)
                else:
                    limite_espera = self.metadata_timeout if last_peers == 0 else (self.metadata_timeout + 20.0)
                    if last_downloaded == 0 and (now - start) > limite_espera:
                        self._terminate(process)
                        shutil.rmtree(job_dir, ignore_errors=True)
                        return DownloadResult(
                            option, False,
                            error=f"Timeout: Sem resposta dos trackers apos {int(limite_espera)}s",
                            elapsed_seconds=now - start
                        )

            process.wait()
        except KeyboardInterrupt:
            self._terminate(process)
            shutil.rmtree(job_dir, ignore_errors=True)
            raise

        elapsed = time.monotonic() - start

        if process.returncode != 0:
            shutil.rmtree(job_dir, ignore_errors=True)
            err_msg = describe_exit_code(process.returncode)
            return DownloadResult(option, False, return_code=process.returncode, error=err_msg, elapsed_seconds=elapsed)

        is_batch = option.metadata.get("episodio") == "Batch" or option.metadata.get("is_batch", False)
        final_local, aviso_ambiguidade = self._resolve_downloaded_file_ex(job_dir, is_batch=is_batch)
        if final_local is None:
            shutil.rmtree(job_dir, ignore_errors=True)
            return DownloadResult(option, False, return_code=process.returncode, error="Nenhum arquivo de video completo localizado.", elapsed_seconds=elapsed)

        try:
            moved_path = self._move_to_destination(final_local, dest_dir, is_batch=is_batch)
            shutil.rmtree(job_dir, ignore_errors=True)

            # Verificacao pos-download das trilhas de legenda REAIS do arquivo (via
            # ffprobe) -- a unica forma de saber com certeza o que foi de fato
            # baixado, ja que a estimativa de PT-BR feita antes do download (rotulo
            # "confirmado"/"provavel"/"desconhecido") e' sempre uma inferencia sobre
            # o titulo/metadado da release, nunca uma garantia sobre o arquivo final.
            # So' e' aplicada a episodios individuais (nao a pastas de batch).
            aviso_legendas = None
            if not is_batch:
                verificacao = verificar_legendas_arquivo(moved_path)
                if verificacao["verificado"]:
                    if not verificacao["tem_legenda"]:
                        aviso_legendas = "Arquivo baixado não contém nenhuma trilha de legenda embutida."
                    elif not verificacao["tem_ptbr"]:
                        idiomas_str = ", ".join(verificacao["idiomas"]) if verificacao["idiomas"] else "nenhum identificado"
                        aviso_legendas = f"Arquivo não contém legenda em português (idiomas encontrados: {idiomas_str})."
                # Se verificado=False (ffprobe indisponivel, formato nao suportado etc.),
                # nao geramos aviso: ausencia de confirmacao nao deve ser lida como "sem
                # legenda", apenas como "nao foi possivel verificar".

            avisos = [a for a in (aviso_ambiguidade, aviso_legendas) if a]
            aviso_final = " | ".join(avisos) if avisos else None

            return DownloadResult(
                option, True, final_path=moved_path, return_code=0,
                elapsed_seconds=elapsed, warning=aviso_final
            )
        except Exception as me:
            shutil.rmtree(job_dir, ignore_errors=True)
            return DownloadResult(option, False, return_code=0, error=f"Erro ao salvar arquivo no destino: {me}", elapsed_seconds=elapsed)

    def download_with_retry(
        self,
        option: DownloadOption,
        dest_dir: str | Path,
        max_retries: int = 2,
        base_delay_seconds: float = 3.0,
        cancel_event: Optional[threading.Event] = None,
        on_progress: Optional[Callable[[Telemetry], None]] = None
    ) -> DownloadResult:
        last_res = None
        for attempt in range(1, max_retries + 2):
            if cancel_event and cancel_event.is_set():
                break
            res = self.download(option, dest_dir=dest_dir, on_progress=on_progress, cancel_event=cancel_event)
            res.attempts = attempt
            if res.success or res.cancelled:
                return res
            last_res = res
            if attempt <= max_retries and res.return_code in DEFAULT_RETRYABLE_CODES:
                delay = base_delay_seconds * (2 ** (attempt - 1))
                time.sleep(delay)
            else:
                break
        return last_res or DownloadResult(option, False, error="Falha em todas as tentativas")

    @staticmethod
    def _resolve_downloaded_file(
        job_dir: Path, is_batch: bool = False
    ) -> Optional[Path]:
        result, _ = Aria2Engine._resolve_downloaded_file_ex(job_dir, is_batch=is_batch)
        return result

    @staticmethod
    def _resolve_downloaded_file_ex(
        job_dir: Path, is_batch: bool = False
    ) -> tuple[Optional[Path], Optional[str]]:
        """Retorna (arquivo_escolhido, aviso_ou_None). O aviso e' preenchido quando
        o torrent trouxe mais de um arquivo de video plausivel (ex: episodio + trailer,
        ou dual-audio em arquivos separados) — cenario em que --out nao teve efeito
        confiavel porque o torrent tinha multiplos arquivos internos."""
        candidates = []
        for root, _, files in os.walk(job_dir):
            for f in files:
                p = Path(root) / f
                if not p.name.endswith(".aria2") and not p.name.endswith(".torrent"):
                    control = p.with_suffix(p.suffix + ".aria2")
                    if not control.exists() and p.stat().st_size > 1024 * 1024:
                        candidates.append(p)
        if not candidates:
            return None, None
        if is_batch:
            return job_dir, None

        VIDEO_EXTS = {".mkv", ".mp4", ".avi", ".webm", ".mov"}
        video_candidates = [c for c in candidates if c.suffix.lower() in VIDEO_EXTS]
        pool = video_candidates if video_candidates else candidates
        pool.sort(key=lambda p: p.stat().st_size, reverse=True)

        aviso = None
        if len(video_candidates) > 1:
            maior = pool[0].stat().st_size
            segundo = pool[1].stat().st_size
            # Se o segundo maior candidato tem tamanho comparavel ao escolhido (>40%),
            # a escolha automatica e' ambigua o suficiente para avisar o usuario.
            if maior > 0 and (segundo / maior) > 0.4:
                nomes = ", ".join(c.name for c in pool[:3])
                aviso = (
                    f"Torrent continha {len(video_candidates)} arquivos de video "
                    f"de tamanho comparavel ({nomes}); selecionado automaticamente "
                    f"o maior ({pool[0].name}). Verifique se e' o episodio correto."
                )
        return pool[0], aviso

    @staticmethod
    def _move_to_destination(src: Path, dest_dir: Path, is_batch: bool = False) -> Path:
        dest_dir.mkdir(parents=True, exist_ok=True)
        if is_batch:
            lote_dir = dest_dir / f"batch_{uuid.uuid4().hex[:6]}"
            lote_dir.mkdir(parents=True, exist_ok=True)
            for root, _, files in os.walk(src):
                for f in files:
                    p = Path(root) / f
                    if not p.name.endswith(".aria2") and not p.name.endswith(".torrent") and p.stat().st_size > 1024 * 1024:
                        final_dest = lote_dir / p.name
                        shutil.copy2(p, final_dest)
                        try:
                            p.unlink(missing_ok=True)
                        except Exception as _exc:
                            _registrar_erro(_exc)
            return lote_dir
        else:
            final_dest = dest_dir / src.name
            shutil.copy2(src, final_dest)
            try:
                src.unlink(missing_ok=True)
            except Exception as _exc:
                _registrar_erro(_exc)
            return final_dest

    @staticmethod
    def _terminate(process: subprocess.Popen) -> None:
        """Encerra o processo aria2c e toda a sua árvore (grupo de processos).
        Usa os.killpg (Linux/Colab) para garantir que nenhum processo filho
        fique orfao consumindo CPU/disco/rede na VM. IMPORTANTE: o processo
        pai pode morrer com SIGTERM enquanto outros membros do grupo (ex: um
        subprocesso que ignora SIGTERM) continuam vivos -- por isso o SIGKILL
        do grupo e' sempre enviado como confirmacao final, mesmo que wait()
        do processo principal já tenha retornado com sucesso."""
        if process.poll() is not None:
            return
        if sys.platform != "win32":
            try:
                pgid = os.getpgid(process.pid)
            except (ProcessLookupError, OSError):
                pgid = None

            if pgid is not None:
                try:
                    os.killpg(pgid, signal.SIGTERM)
                except (ProcessLookupError, PermissionError, OSError):
                    pass

            try:
                process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                pass

            # Escala para SIGKILL no GRUPO INTEIRO independentemente do resultado
            # acima: o processo raiz pode ja' ter morrido enquanto um descendente
            # que ignora SIGTERM continua rodando no mesmo grupo.
            if pgid is not None:
                try:
                    os.killpg(pgid, signal.SIGKILL)
                except (ProcessLookupError, PermissionError, OSError):
                    pass

            try:
                process.wait(timeout=2)
            except Exception:
                logger.warning(f"Nao foi possivel confirmar encerramento do PID {process.pid}")
        else:
            try:
                process.terminate()
                process.wait(timeout=2)
            except Exception:
                try:
                    process.kill()
                    process.wait(timeout=2)
                except Exception as _exc:
                    _registrar_erro(_exc)


# ==============================================================================
# 2b. aria2_rpc_client.py (Cliente JSON-RPC para controle real de downloads)
# ==============================================================================
"""
Cliente RPC leve para o aria2c, habilitando pausar/retomar/cancelar por download
individual e monitoramento eficiente via chamadas em lote -- algo que o modelo
anterior (um subprocess.Popen por download, com parsing de texto de stdout) nao
permite, ja que o unico "controle" possivel ali e' matar o processo inteiro.

Arquitetura: em vez de N processos aria2c (um por episodio), sobe-se UM unico
daemon aria2c em modo `--enable-rpc` para o lote inteiro. Cada download vira
uma chamada `aria2.addUri`/`addTorrent`, que retorna um GID (identificador
unico). Progresso, pausa, retomada e cancelamento passam a ser chamadas RPC
sobre esse GID, e nao mais sinais de processo.

Todos os metodos e formatos de resposta usados aqui foram validados empiricamente
contra um daemon aria2c 1.37.0 real (o mesmo binario que roda no Colab) antes de
serem integrados ao pipeline -- incluindo o ciclo completo pause/unpause/
forceRemove, consultas em lote (tellActive/tellWaiting/tellStopped) e a
persistencia de fila entre reinicios completos do processo via
saveSession + --input-file (o GID e' preservado identico apos matar e religar
o daemon do zero, o que sustenta a retomada apos desconexao do Colab).

Referencia oficial: https://aria2.github.io/manual/en/html/aria2c.html#rpc-interface
"""

import json as _json_rpc
import secrets as _secrets_rpc
import threading as _threading_rpc
import urllib.request as _urlreq_rpc
import urllib.error as _urlerr_rpc
from typing import Optional as _Optional_rpc, Dict as _Dict_rpc, Any as _Any_rpc, List as _List_rpc


class Aria2RPCError(Exception):
    """Erro retornado pelo daemon aria2c via JSON-RPC (campo 'error' da resposta).
    Ex: token invalido ('Unauthorized'), GID inexistente, parametro invalido etc."""
    def __init__(self, code: int, message: str):
        self.code = code
        self.message = message
        super().__init__(f"aria2 RPC error {code}: {message}")


class Aria2RPCClient:
    """
    Cliente JSON-RPC minimo para o aria2c, usando apenas urllib da stdlib (sem
    dependencia nova). Cada instancia fala com UM daemon aria2c especifico
    (host:port + secret). Thread-safe para chamadas concorrentes (cada chamada
    e' uma requisicao HTTP independente; o unico estado compartilhado e' o
    contador de ids, protegido por lock).
    """

    def __init__(self, host: str = "127.0.0.1", port: int = 6800, secret: str = "", timeout: float = 10.0):
        self.url = f"http://{host}:{port}/jsonrpc"
        self.secret = secret
        self.timeout = timeout
        self._id_counter = 0
        self._lock = _threading_rpc.Lock()

    def _next_id(self) -> str:
        with self._lock:
            self._id_counter += 1
            return str(self._id_counter)

    def call(self, method: str, params: _Optional_rpc[_List_rpc[_Any_rpc]] = None) -> _Any_rpc:
        full_params = [f"token:{self.secret}"] + (params or [])
        payload = {
            "jsonrpc": "2.0",
            "id": self._next_id(),
            "method": method,
            "params": full_params
        }
        data = _json_rpc.dumps(payload).encode("utf-8")
        req = _urlreq_rpc.Request(
            self.url, data=data,
            headers={"Content-Type": "application/json"}
        )
        try:
            with _urlreq_rpc.urlopen(req, timeout=self.timeout) as resp:
                body = _json_rpc.loads(resp.read().decode("utf-8"))
        except _urlerr_rpc.HTTPError as e:
            # aria2c retorna status HTTP nao-2xx (ex: 400) para erros RPC como token
            # invalido, mas o CORPO da resposta ainda e' o JSON-RPC estruturado com
            # o campo 'error' -- precisa ser lido antes de desistir, ou perdemos a
            # mensagem real (ex: "Unauthorized") e reportamos um erro generico de
            # conexao em vez do motivo verdadeiro.
            try:
                body = _json_rpc.loads(e.read().decode("utf-8"))
            except Exception:
                raise ConnectionError(f"Erro HTTP {e.code} do daemon aria2c RPC em {self.url}: {e.reason}")
        except _urlerr_rpc.URLError as e:
            raise ConnectionError(f"Nao foi possivel conectar ao daemon aria2c RPC em {self.url}: {e}")
        except TimeoutError as e:
            raise ConnectionError(f"Timeout ao falar com o daemon aria2c RPC em {self.url}: {e}")

        if "error" in body:
            err = body["error"]
            raise Aria2RPCError(err.get("code", -1), err.get("message", "erro desconhecido"))
        return body.get("result")

    # --- metodos de alto nivel, mapeados 1:1 nos metodos aria2.* documentados ---

    def add_uri(self, uris: _List_rpc[str], options: _Optional_rpc[_Dict_rpc[str, str]] = None) -> str:
        params: _List_rpc[_Any_rpc] = [uris]
        if options:
            params.append(options)
        return self.call("aria2.addUri", params)

    def tell_status(self, gid: str, keys: _Optional_rpc[_List_rpc[str]] = None) -> _Dict_rpc[str, _Any_rpc]:
        params: _List_rpc[_Any_rpc] = [gid]
        if keys:
            params.append(keys)
        return self.call("aria2.tellStatus", params)

    def tell_active(self, keys: _Optional_rpc[_List_rpc[str]] = None) -> _List_rpc[_Dict_rpc[str, _Any_rpc]]:
        return self.call("aria2.tellActive", [keys] if keys else None)

    def tell_waiting(self, offset: int = 0, num: int = 1000, keys: _Optional_rpc[_List_rpc[str]] = None) -> _List_rpc[_Dict_rpc[str, _Any_rpc]]:
        params: _List_rpc[_Any_rpc] = [offset, num]
        if keys:
            params.append(keys)
        return self.call("aria2.tellWaiting", params)

    def tell_stopped(self, offset: int = 0, num: int = 1000, keys: _Optional_rpc[_List_rpc[str]] = None) -> _List_rpc[_Dict_rpc[str, _Any_rpc]]:
        params: _List_rpc[_Any_rpc] = [offset, num]
        if keys:
            params.append(keys)
        return self.call("aria2.tellStopped", params)

    def pause(self, gid: str) -> str:
        return self.call("aria2.pause", [gid])

    def force_pause(self, gid: str) -> str:
        return self.call("aria2.forcePause", [gid])

    def unpause(self, gid: str) -> str:
        return self.call("aria2.unpause", [gid])

    def remove(self, gid: str) -> str:
        return self.call("aria2.remove", [gid])

    def force_remove(self, gid: str) -> str:
        return self.call("aria2.forceRemove", [gid])

    def save_session(self) -> str:
        return self.call("aria2.saveSession")

    def get_version(self) -> _Dict_rpc[str, _Any_rpc]:
        return self.call("aria2.getVersion")

    def shutdown(self) -> str:
        return self.call("aria2.forceShutdown")


def _human_bytes(n: int) -> str:
    """Formata uma contagem de bytes em texto legivel (ex: 1536 -> '1.5KB')."""
    n = float(n)
    for unidade in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024.0:
            return f"{n:.1f}{unidade}" if unidade != "B" else f"{int(n)}{unidade}"
        n /= 1024.0
    return f"{n:.1f}PB"


class Aria2RPCEngine:
    """
    Motor de download baseado no modo RPC do aria2c: UM unico daemon gerencia
    TODOS os downloads do lote (em vez de um subprocess.Popen por download,
    modelo usado por Aria2Engine). Isso habilita pausar/retomar/cancelar POR
    ITEM INDIVIDUAL via GID -- algo impossivel no modelo anterior, onde o
    unico controle disponivel era matar o processo inteiro.

    Tambem habilita retomar a fila apos uma desconexao/reinicio completo do
    Colab: a sessao e' salva periodicamente em disco (session_file) e recarregada
    automaticamente se o arquivo ja existir ao iniciar um novo daemon -- os GIDs
    sao preservados identicos, validado empiricamente contra um daemon real.

    Uso tipico (ver run_batch_rpc para o fluxo completo com UI e upload):
        engine = Aria2RPCEngine(staging_dir=..., extra_trackers=...)
        engine.start_daemon()
        gid = engine.add_download(option, dest_dir=...)
        ... loop chamando engine.poll_once() ...
        engine.pause_download(gid) / resume_download(gid) / cancel_download(gid)
        engine.stop_daemon()
    """

    def __init__(
        self,
        staging_dir: Optional[str | Path] = None,
        aria2c_bin: Optional[str] = None,
        extra_trackers: str = PUBLIC_TRACKERS,
        rpc_port: int = 6800,
        max_concurrent_downloads: int = 3,
        stall_timeout_com_peers: float = 180.0,
        stall_timeout_sem_peers: float = 45.0,
        stall_aviso_fracao: float = 0.6,
        metadata_timeout: float = 90.0,
        session_file: Optional[str | Path] = None,
    ):
        if staging_dir is None:
            staging_dir = Path("/content/downloads_temp") if (sys.platform != "win32" and os.path.exists("/content")) else Path("./downloads_temp")
        self.staging_dir = Path(staging_dir)
        self.staging_dir.mkdir(parents=True, exist_ok=True)

        if aria2c_bin is None:
            self.aria2c_bin = shutil.which("aria2c") or "aria2c"
        else:
            self.aria2c_bin = aria2c_bin

        self.extra_trackers = extra_trackers
        self.rpc_port = rpc_port
        self.max_concurrent_downloads = max_concurrent_downloads
        self.stall_timeout_com_peers = stall_timeout_com_peers
        self.stall_timeout_sem_peers = stall_timeout_sem_peers
        self.stall_aviso_fracao = stall_aviso_fracao
        self.metadata_timeout = metadata_timeout
        # O arquivo de sessao e' o que sustenta a retomada apos desconexao: se ele
        # estiver num diretorio que sobrevive a reciclagem da VM (ex: Google Drive
        # montado), uma nova execucao consegue recarregar a fila inteira.
        self.session_file = Path(session_file) if session_file else (self.staging_dir / "aria2_session.txt")

        self.secret = secrets.token_hex(16)
        self.client: Optional["Aria2RPCClient"] = None
        self._daemon_proc: Optional[subprocess.Popen] = None
        self._gid_state: Dict[str, Dict[str, Any]] = {}

    def start_daemon(self, timeout: float = 8.0) -> None:
        """Sobe o daemon aria2c em modo RPC. Se um arquivo de sessao anterior
        existir (ex: de uma execucao interrompida por desconexao do Colab), a
        fila e' recarregada automaticamente com os mesmos GIDs de antes."""
        if self.client is not None:
            return

        cmd = [
            self.aria2c_bin,
            "--enable-rpc",
            "--rpc-listen-all=false",
            f"--rpc-listen-port={self.rpc_port}",
            f"--rpc-secret={self.secret}",
            f"--dir={self.staging_dir}",
            "--file-allocation=none",
            "--enable-dht=true",
            "--enable-dht6=false",
            "--bt-enable-lpd=true",
            f"--bt-tracker={self.extra_trackers}",
            "--bt-tracker-connect-timeout=5",
            "--bt-tracker-timeout=5",
            "--bt-max-peers=100",
            "--seed-time=0",
            "--max-connection-per-server=16",
            f"--max-concurrent-downloads={self.max_concurrent_downloads}",
            "--auto-file-renaming=false",
            "--allow-overwrite=true",
            "--peer-id-prefix=-TR2940-",
            "--user-agent=Transmission/2.94",
            "--bt-remove-unselected-file=true",
            "--bt-prioritize-piece=head,tail",
            "--max-overall-upload-limit=15K",
            f"--save-session={self.session_file}",
            "--save-session-interval=20",
        ]
        if self.session_file.exists() and self.session_file.stat().st_size > 0:
            cmd.append(f"--input-file={self.session_file}")

        self._daemon_proc = subprocess.Popen(
            cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            start_new_session=(sys.platform != "win32")
        )

        client = Aria2RPCClient(port=self.rpc_port, secret=self.secret)
        deadline = time.monotonic() + timeout
        ultimo_erro = None
        while time.monotonic() < deadline:
            try:
                client.get_version()
                self.client = client
                return
            except Exception as e:
                ultimo_erro = e
                time.sleep(0.15)
        raise RuntimeError(f"Daemon aria2c RPC nao respondeu apos {timeout}s de espera: {ultimo_erro}")

    def stop_daemon(self, save_session: bool = True) -> None:
        """Encerra o daemon de forma limpa, salvando a sessao (fila atual) antes
        de matar o processo, para que uma proxima execucao possa retomar."""
        if self.client is not None:
            try:
                if save_session:
                    self.client.save_session()
            except Exception as _exc:
                _registrar_erro(_exc)
            try:
                self.client.shutdown()
            except Exception as _exc:
                _registrar_erro(_exc)
        if self._daemon_proc is not None:
            try:
                self._daemon_proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                Aria2Engine._terminate(self._daemon_proc)
            self._daemon_proc = None
        self.client = None

    def add_download(self, option: "DownloadOption", dest_dir: str | Path) -> str:
        """Registra um novo download no daemon e retorna o GID que o identifica
        de forma unica -- e' esse GID que os botoes de pausar/retomar/excluir da
        interface usam para agir sobre ESTE item especifico, sem afetar os demais."""
        assert self.client is not None, "chame start_daemon() antes de add_download()"

        job_dir = self.staging_dir / f"job_{uuid.uuid4().hex[:8]}"
        job_dir.mkdir(parents=True, exist_ok=True)

        is_batch = option.metadata.get("episodio") == "Batch" or option.metadata.get("is_batch", False)
        opts: Dict[str, str] = {"dir": str(job_dir)}
        if option.filename_hint and not is_batch:
            opts["out"] = option.filename_hint

        gid = self.client.add_uri([option.url], options=opts)

        self._gid_state[gid] = {
            "option": option,
            "dest_dir": Path(dest_dir),
            "job_dir": job_dir,
            "is_batch": is_batch,
            "start_time": time.monotonic(),
            "last_progress_time": time.monotonic(),
            "last_downloaded": 0,
            "last_peers": 0,
            "aviso_emitido": False,
            "status": "pending",
            "result": None,
            "_reported": False,
        }
        return gid

    def pause_download(self, gid: str) -> None:
        """Pausa um download especifico usando forcePause (nao o 'pause' gracioso).
        Validado empiricamente: com varios trackers publicos configurados
        (--bt-tracker), o 'pause' gracioso pode ser revertido pelo ciclo interno
        de retry de anuncio a trackers lentos/inacessiveis -- o download volta
        para 'active' sozinho pouco depois. O forcePause nao sofre disso."""
        if not self.client:
            return
        try:
            self.client.force_pause(gid)
        except Aria2RPCError:
            pass  # ja pausado, ja completo, gid desconhecido etc -- nao e' fatal

    def resume_download(self, gid: str) -> None:
        if self.client:
            try:
                self.client.unpause(gid)
            except Aria2RPCError:
                pass

    def cancel_download(self, gid: str) -> None:
        state = self._gid_state.get(gid)
        if state is not None:
            state["status"] = "cancelled"
        if self.client:
            try:
                self.client.force_remove(gid)
            except Aria2RPCError:
                pass

    def poll_once(self) -> Dict[str, Dict[str, Any]]:
        """
        Consulta o estado de TODOS os downloads rastreados em uma unica rodada
        de chamadas em lote (tellActive/tellWaiting/tellStopped) -- muito mais
        eficiente que N chamadas tellStatus individuais -- aplica o watchdog
        adaptativo (mesma logica de duas camadas usada em Aria2Engine.download:
        tolerancia maior com peers ativos, menor sem peers) e faz o
        pos-processamento (mover arquivo, verificar legendas via ffprobe) de
        qualquer GID que tenha acabado de completar.

        Retorna {gid: {"telemetry": Telemetry, "finished": bool, "result": ...}}
        apenas para os GIDs rastreados por esta instancia.
        """
        if self.client is None or not self._gid_state:
            return {}

        updates: Dict[str, Dict[str, Any]] = {}
        keys = ["gid", "status", "completedLength", "totalLength",
                "downloadSpeed", "connections", "errorMessage", "files"]
        try:
            ativos = self.client.tell_active(keys=keys)
            esperando = self.client.tell_waiting(0, 1000, keys=keys)
            parados = self.client.tell_stopped(0, 1000, keys=keys)
        except (ConnectionError, Aria2RPCError) as e:
            logger.warning(f"Falha ao consultar status do daemon RPC: {e}")
            return updates

        todos = {d["gid"]: d for d in (ativos + esperando + parados) if d.get("gid")}
        now = time.monotonic()

        for gid, state in list(self._gid_state.items()):
            if state.get("_reported"):
                continue

            d = todos.get(gid)
            if d is None:
                continue

            aria2_status = d.get("status", "")
            completed = int(d.get("completedLength", 0) or 0)
            total = int(d.get("totalLength", 0) or 0)
            speed = int(d.get("downloadSpeed", 0) or 0)
            connections = int(d.get("connections", 0) or 0)

            if completed > state["last_downloaded"]:
                state["last_downloaded"] = completed
                state["last_progress_time"] = now
                state["aviso_emitido"] = False
            state["last_peers"] = connections

            telem = Telemetry(
                raw_line="",
                percent=(completed / total * 100.0) if total else 0.0,
                downloaded=_human_bytes(completed),
                total=_human_bytes(total) if total else "?",
                speed=f"{_human_bytes(speed)}/s",
                peers=connections,
                downloaded_bytes=completed,
            )

            if aria2_status == "paused":
                telem.eta = "⏸ Pausado pelo usuário"
                updates[gid] = {"telemetry": telem, "finished": False, "result": None}
                continue

            if aria2_status in ("active", "waiting"):
                if state["last_downloaded"] > 0:
                    limite = self.stall_timeout_com_peers if connections > 0 else self.stall_timeout_sem_peers
                    parado_ha = now - state["last_progress_time"]
                    if parado_ha > limite:
                        motivo_peers = f"{connections} peer(s) ativos" if connections > 0 else "0 peers ativos"
                        self.cancel_download(gid)
                        result = DownloadResult(
                            state["option"], False,
                            error=f"Download estagnado por mais de {int(limite)}s sem novos dados ({motivo_peers})",
                            elapsed_seconds=now - state["start_time"]
                        )
                        shutil.rmtree(state["job_dir"], ignore_errors=True)
                        state["result"] = result
                        state["_reported"] = True
                        updates[gid] = {"telemetry": telem, "finished": True, "result": result}
                        continue
                    if parado_ha > limite * self.stall_aviso_fracao and not state["aviso_emitido"]:
                        state["aviso_emitido"] = True
                        restante = max(0, int(limite - parado_ha))
                        telem.eta = f"⚠️ Sem dados há {int(parado_ha)}s (encerra em {restante}s)"
                elif (now - state["start_time"]) > self.metadata_timeout:
                    self.cancel_download(gid)
                    result = DownloadResult(
                        state["option"], False,
                        error=f"Timeout: sem seeds/dados ativos apos {int(self.metadata_timeout)}s (swarm inativo)",
                        elapsed_seconds=now - state["start_time"]
                    )
                    shutil.rmtree(state["job_dir"], ignore_errors=True)
                    state["result"] = result
                    state["_reported"] = True
                    updates[gid] = {"telemetry": telem, "finished": True, "result": result}
                    continue
                else:
                    restante = max(0, int(self.metadata_timeout - (now - state["start_time"])))
                    telem.eta = f"Procurando seeds ({restante}s)..." if connections == 0 else f"Negociando metadados ({restante}s)..."

                updates[gid] = {"telemetry": telem, "finished": False, "result": None}
                continue

            if aria2_status == "complete":
                result = self._finalizar_sucesso(state, now, d)
                state["result"] = result
                state["_reported"] = True
                updates[gid] = {"telemetry": telem, "finished": True, "result": result}
                continue

            if aria2_status in ("error", "removed"):
                if state["status"] == "cancelled":
                    result = DownloadResult(
                        state["option"], False, error="Cancelado pelo usuário",
                        cancelled=True, elapsed_seconds=now - state["start_time"]
                    )
                else:
                    msg = d.get("errorMessage") or "Falha no download (aria2)"
                    result = DownloadResult(state["option"], False, error=msg, elapsed_seconds=now - state["start_time"])
                shutil.rmtree(state["job_dir"], ignore_errors=True)
                state["result"] = result
                state["_reported"] = True
                updates[gid] = {"telemetry": telem, "finished": True, "result": result}
                continue

            updates[gid] = {"telemetry": telem, "finished": False, "result": None}

        return updates

    @staticmethod
    def _resolver_arquivo_rpc(files_raw: List[Dict[str, Any]], is_batch: bool) -> tuple[Optional[Path], Optional[str]]:
        """
        Resolve qual arquivo e' o video final a partir do campo 'files' que o
        proprio aria2 retorna via RPC -- mais preciso que escanear o diretorio
        e inferir por presenca/ausencia do arquivo de controle '.aria2', que
        com --force-save (necessario para persistencia de sessao) permanece
        no disco mesmo apos o download ja estar 100% completo.
        """
        candidatos = []
        for f in files_raw:
            path = Path(f.get("path", ""))
            length = int(f.get("length", 0) or 0)
            if path.name and length > 1024 * 1024:
                candidatos.append((path, length))

        if not candidatos:
            return None, None
        if is_batch:
            # Para batch, o "arquivo" retornado e' o diretorio pai comum de tudo.
            return candidatos[0][0].parent, None

        VIDEO_EXTS = {".mkv", ".mp4", ".avi", ".webm", ".mov"}
        video_candidatos = [(p, sz) for p, sz in candidatos if p.suffix.lower() in VIDEO_EXTS]
        pool = video_candidatos if video_candidatos else candidatos
        pool.sort(key=lambda item: item[1], reverse=True)

        aviso = None
        if len(video_candidatos) > 1:
            maior = pool[0][1]
            segundo = pool[1][1]
            if maior > 0 and (segundo / maior) > 0.4:
                nomes = ", ".join(p.name for p, _ in pool[:3])
                aviso = (
                    f"Torrent continha {len(video_candidatos)} arquivos de video "
                    f"de tamanho comparavel ({nomes}); selecionado automaticamente "
                    f"o maior ({pool[0][0].name}). Verifique se e' o episodio correto."
                )
        return pool[0][0], aviso

    def _finalizar_sucesso(self, state: Dict[str, Any], now: float, status_raw: Dict[str, Any]) -> "DownloadResult":
        """Usa o campo 'files' retornado pelo proprio aria2 (autoritativo) para
        localizar o arquivo final, depois reaproveita a movimentacao e a
        verificacao de legendas ja validadas em Aria2Engine/verificar_legendas_arquivo."""
        option = state["option"]
        job_dir = state["job_dir"]
        dest_dir = state["dest_dir"]
        is_batch = state["is_batch"]
        elapsed = now - state["start_time"]

        files_raw = status_raw.get("files", []) or []
        final_local, aviso_ambiguidade = self._resolver_arquivo_rpc(files_raw, is_batch)
        if final_local is None or not final_local.exists():
            shutil.rmtree(job_dir, ignore_errors=True)
            return DownloadResult(option, False, error="Nenhum arquivo de video completo localizado.", elapsed_seconds=elapsed)

        try:
            moved_path = Aria2Engine._move_to_destination(final_local, dest_dir, is_batch=is_batch)
            shutil.rmtree(job_dir, ignore_errors=True)

            aviso_legendas = None
            if not is_batch:
                verificacao = verificar_legendas_arquivo(moved_path)
                if verificacao["verificado"]:
                    if not verificacao["tem_legenda"]:
                        aviso_legendas = "Arquivo baixado não contém nenhuma trilha de legenda embutida."
                    elif not verificacao["tem_ptbr"]:
                        idiomas_str = ", ".join(verificacao["idiomas"]) if verificacao["idiomas"] else "nenhum identificado"
                        aviso_legendas = f"Arquivo não contém legenda em português (idiomas encontrados: {idiomas_str})."

            avisos = [a for a in (aviso_ambiguidade, aviso_legendas) if a]
            aviso_final = " | ".join(avisos) if avisos else None

            return DownloadResult(
                option, True, final_path=moved_path, return_code=0,
                elapsed_seconds=elapsed, warning=aviso_final
            )
        except Exception as me:
            shutil.rmtree(job_dir, ignore_errors=True)
            return DownloadResult(option, False, return_code=0, error=f"Erro ao salvar arquivo no destino: {me}", elapsed_seconds=elapsed)


# ==============================================================================
# 3. nyaa_scraper.py (Agrupamento por Fansub / Release Group, anitopy e filtro de episódios)
# ==============================================================================
"""
nyaa_scraper.py (P2P Ingestion Toolkit v6.5 - Busca Direcionada e Agrupamento Resiliente)
==========================================================================================
Scraper resiliente para Nyaa.si com suporte a intervalos de episódios (ex: 20-30),
buscas direcionadas para episódios antigos, preservação de episódios como 1080
e agrupamento inteligente por Fansub / Release Group (Erai-raws, Judas, SubsPlease, etc).
"""


import re
import urllib.parse
import urllib.request
import urllib.error
from typing import Any, Dict, List, Optional, Set, Tuple
from bs4 import BeautifulSoup

try:
    import anitopy
except ImportError:
    anitopy = None


NYAA_BASE = "https://nyaa.si"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
}

PTBR_KEYWORDS_ALTA_CONFIANCA = [
    "pt-br", "ptbr", "pt_br", "[pt-br]", "(pt-br)",
    "portuguese", "portugues", "legendado", "legenda pt",
]
PTBR_KEYWORDS_BAIXA_CONFIANCA = [
    "multi-sub", "multi sub", "multisub", "erai-raws"
]
# Mantida por compatibilidade com código legado que ainda referencia a lista antiga.
PTBR_KEYWORDS = PTBR_KEYWORDS_ALTA_CONFIANCA + PTBR_KEYWORDS_BAIXA_CONFIANCA
GRUPOS_SEMPRE_PTBR = {"punch", "malkav", "iceblue", "unianime"}

def parse_episode_selection(range_str: str) -> Optional[Set[int]]:
    """
    Converte strings como '20-30', '1, 2, 5, 8', '1-5, 8, 20-25', 'all'
    em um conjunto de números inteiros correspondentes. Retorna None se for 'all' ou vazio.
    """
    if not range_str:
        return None
    s = range_str.strip().lower()
    if s in ("all", "todos", "*", "qualquer", "none", ""):
        return None

    selected = set()
    tokens = re.split(r"[,;\s]+", s)
    for token in tokens:
        if not token:
            continue
        if "-" in token:
            m = re.match(r"^(\d+)-(\d+)$", token)
            if m:
                start, end = int(m.group(1)), int(m.group(2))
                if start <= end:
                    selected.update(range(start, end + 1))
                else:
                    selected.update(range(end, start + 1))
        elif token.isdigit():
            selected.add(int(token))

    return selected if selected else None

_GRUPO_PREFIXO_RE = re.compile(r"^\[([a-zA-Z0-9_\-\.\s]+)\]")

def _confianca_ptbr(titulo: str) -> str:
    """
    Retorna o nivel de confianca de que a release contem legenda em PT-BR:
    - "confirmado": idioma explicitamente mencionado (ex: 'PT-BR', 'Legendado')
      ou o grupo de fansub e' um dos que sempre incluem PT-BR em suas releases
      (Punch, Malkav, IceBlue, UniAnime).
    - "provavel": o titulo so' contem uma alegacao GENERICA de multiplos idiomas
      (ex: 'Multi-Sub'), que NAO prova que o portugues esteja entre eles -- um
      release Multi-Sub pode ter apenas {JP, EN, ES, FR} e nenhum PT-BR. Esta e'
      a causa raiz confirmada de releases sem legenda em portugues (ou ate' sem
      legenda nenhuma) passarem pelo filtro antigo, que tratava esse caso como
      prova suficiente.
    - "desconhecido": nenhum indicio de idioma no titulo.

    IMPORTANTE: nenhum destes niveis e' garantia absoluta -- a unica forma
    100% confiavel e' inspecionar as trilhas reais do arquivo apos o download
    (ver verificar_legendas_arquivo). Este nivel de confianca serve apenas
    para ordenar/rotular as opcoes ANTES de baixar qualquer coisa.
    """
    t_lower = titulo.lower()

    if any(kw in t_lower for kw in PTBR_KEYWORDS_ALTA_CONFIANCA):
        return "confirmado"

    m = _GRUPO_PREFIXO_RE.match(titulo)
    if m:
        rg_low = m.group(1).strip().lower()
        if any(g in rg_low for g in GRUPOS_SEMPRE_PTBR):
            return "confirmado"

    if any(kw in t_lower for kw in PTBR_KEYWORDS_BAIXA_CONFIANCA):
        return "provavel"

    return "desconhecido"

def _contem_ptbr(titulo: str) -> bool:
    """Mantida por compatibilidade: True para confianca 'confirmado' ou 'provavel'.
    Prefira _confianca_ptbr quando for possivel rotular a diferenca para o usuario."""
    return _confianca_ptbr(titulo) in ("confirmado", "provavel")






def _formatar_episodio(ep) -> str:
    """Formata numero de episodio com zero-padding, suportando tanto int
    (Ep 05 -> '05') quanto float de episodios especiais (Ep 13.5 -> '13.5')."""
    if isinstance(ep, float) and ep != int(ep):
        parte_int, parte_dec = str(ep).split(".")
        return f"{int(parte_int):02d}.{parte_dec}"
    return f"{int(ep):02d}"


def _identificar_grupo(titulo: str) -> str:
    m = re.match(r"^\[([a-zA-Z0-9_\-\.\s]+)\]", titulo)
    if m:
        raw_group = m.group(1).strip()
        rg_low = raw_group.lower()
        if "erai" in rg_low:
            return "Erai-raws (Multi-Sub PT-BR)"
        elif "judas" in rg_low:
            return "Judas (HEVC x265 Leve)"
        elif "subsplease" in rg_low:
            return "SubsPlease (Multi/CR)"
        elif "punch" in rg_low:
            return "Punch Fansub (PT-BR)"
        elif "malkav" in rg_low:
            return "Malkav Fansub (PT-BR)"
        elif "iceblue" in rg_low:
            return "IceBlue (BD x265 PT-BR)"
        elif "tsundere" in rg_low:
            return "Tsundere (Fansub)"
        elif "unianime" in rg_low:
            return "UniAnime (PT-BR)"
        return f"[{raw_group}]"

    if _contem_ptbr(titulo):
        return "Fansubs Brasileiras / PT-BR"
    return "Comunidade / Outros"

def buscar_torrents_nyaa_rss(query, timeout=8):
    """Plano B da busca: feed RSS do Nyaa. Devolve o mesmo formato da busca HTML."""
    import xml.etree.ElementTree as _ET
    url = f"{NYAA_BASE}/?" + urllib.parse.urlencode({"page": "rss", "q": query})
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=timeout) as resp:
            dados = resp.read()
        raiz = _ET.fromstring(dados)
    except Exception as exc:
        _registrar_erro(exc, "busca RSS do Nyaa")
        return []
    resultados = []
    for item in raiz.iter("item"):
        campos = {}
        for el in item:
            campos.setdefault(el.tag.split("}")[-1], (el.text or "").strip())
        titulo = campos.get("title", "")
        infohash = campos.get("infoHash", "").lower()
        if not titulo or not re.fullmatch(r"[0-9a-f]{40}", infohash):
            continue
        try:
            seeders = int(campos.get("seeders") or 0)
        except ValueError:
            seeders = 0
        resultados.append({
            "titulo": titulo,
            "magnet": f"magnet:?xt=urn:btih:{infohash}&dn={urllib.parse.quote(titulo)}",
            "tamanho": campos.get("size", "?"),
            "seeders": seeders,
            "fonte": "nyaa.si (RSS)",
        })
    return resultados


def buscar_torrents_nyaa(
    query: str,
    categoria: str = "1_0",
    max_paginas: int = 1,
    max_retries: int = 2
) -> List[Dict[str, Any]]:
    """
    Busca torrents no Nyaa.si com resiliencia a bloqueios HTTP:
    - HTTP 429 (rate limit): respeita Retry-After (ou backoff exponencial) e tenta novamente.
    - HTTP 403/503 (Cloudflare/bloqueio): aborta a pagina atual sem derrubar o restante
      da varredura, e sinaliza a falha via retorno silencioso (a chamada superior ja'
      trata fontes vazias como "nenhum release encontrado").
    - HTML inesperado (ex: pagina de CAPTCHA sem a tabela de resultados): tratado como
      "sem resultados" para esta pagina, sem lancar excecao para o chamador.
    """
    import time as _time

    resultados = []
    for pag in range(1, max_paginas + 1):
        params = {
            "f": "0",
            "c": categoria,
            "q": query,
            "s": "seeders",
            "o": "desc",
            "p": str(pag)
        }
        url = f"{NYAA_BASE}/?{urllib.parse.urlencode(params)}"

        pagina_ok = False
        for tentativa in range(max_retries + 1):
            req = urllib.request.Request(url, headers=HEADERS)
            try:
                with urllib.request.urlopen(req, timeout=6) as resp:
                    status = getattr(resp, "status", 200)
                    if status == 429:
                        retry_after = resp.headers.get("Retry-After")
                        try:
                            espera = float(retry_after) if retry_after else 2.0 * (tentativa + 1)
                        except ValueError:
                            espera = 2.0 * (tentativa + 1)
                        if tentativa < max_retries:
                            _time.sleep(min(espera, 10.0))
                            continue
                        break
                    if status in (403, 503):
                        # Bloqueio (Cloudflare) ou servico indisponivel: nao adianta
                        # insistir imediatamente, aborta esta pagina.
                        break

                    html_bytes = resp.read()
                    soup = BeautifulSoup(html_bytes, "html.parser")
                    rows = soup.select("table.torrent-list tbody tr")
                    if not rows and b"<table" not in html_bytes[:20000]:
                        # HTML sem a estrutura esperada (possivel pagina de bloqueio/CAPTCHA
                        # disfarcada de HTTP 200) — trata como "sem resultados" nesta pagina.
                        break

                    for row in rows:
                        links = row.select("td:nth-child(2) a")
                        title_elem = links[-1] if links else None
                        if not title_elem:
                            continue
                        titulo = title_elem.get_text(strip=True)

                        magnet_elem = row.select_one('a[href^="magnet:"]')
                        if not magnet_elem:
                            continue
                        magnet = magnet_elem["href"]

                        size_elem = row.select_one("td:nth-child(4)")
                        size = size_elem.get_text(strip=True) if size_elem else "?"

                        seeders_elem = row.select_one("td:nth-child(6)")
                        seeders = 0
                        if seeders_elem:
                            try:
                                seeders = int(seeders_elem.get_text(strip=True).replace(",", ""))
                            except ValueError:
                                pass

                        resultados.append({
                            "titulo": titulo,
                            "magnet": magnet,
                            "tamanho": size,
                            "seeders": seeders,
                            "fonte": "nyaa.si"
                        })
                    pagina_ok = True
                    break
            except urllib.error.HTTPError as he:
                if he.code == 429 and tentativa < max_retries:
                    _time.sleep(2.0 * (tentativa + 1))
                    continue
                break
            except Exception:
                # Timeout, erro de DNS, conexao recusada etc. Nao insiste mais nesta pagina;
                # a varredura de outras queries/fontes continua normalmente.
                break

        if not pagina_ok:
            # Falha ou bloqueio nesta pagina especifica: nao faz sentido tentar as
            # paginas seguintes da mesma query (o bloqueio tende a persistir).
            break

    if not resultados:
        resultados = buscar_torrents_nyaa_rss(query)
    return resultados

def search_nyaa_grouped(
    anime_nome: str,
    qualidade_preferida: str = "1080p",
    apenas_ptbr: bool = False,
    episodios_filtro: Optional[Set[int]] = None,
    max_paginas: int = 1
) -> Dict[str, List[DownloadOption]]:
    """
    Busca no Nyaa.si otimizada com varredura ágil e consultas direcionadas.
    Evita queries redundantes para acelerar o retorno do catálogo.
    """
    import time
    torrents_brutos: List[Dict[str, Any]] = []
    vistos = set()

    def adicionar_torrents(itens: List[Dict[str, Any]]):
        for it in itens:
            if it["magnet"] not in vistos:
                vistos.add(it["magnet"])
                torrents_brutos.append(it)

    # 1. Varredura dos termos principais (1 página rápida por termo)
    queries_iniciais = [
        f"{anime_nome} PT-BR",
        f"{anime_nome} Erai-raws",
        f"{anime_nome} Judas",
        anime_nome
    ]

    print("    • Mapeando catálogo principal (PT-BR, Erai, Judas)...", end=" ", flush=True)
    t0 = time.time()
    for q in queries_iniciais:
        adicionar_torrents(buscar_torrents_nyaa(q, categoria="1_0", max_paginas=max_paginas))

    if apenas_ptbr:
        adicionar_torrents(buscar_torrents_nyaa(f"{anime_nome} PT-BR", categoria="1_3", max_paginas=1))
    print(f"({len(torrents_brutos)} itens em {time.time() - t0:.1f}s)", flush=True)

    # 2. Se houver filtro de episódios, checar se precisamos de buscas direcionadas
    if episodios_filtro is not None:
        eps_encontrados = set()
        for t in torrents_brutos:
            ep = _extrair_numero_episodio(t["titulo"])
            if ep is not None:
                eps_encontrados.add(ep)

        faltantes = [ep for ep in sorted(episodios_filtro) if ep not in eps_encontrados]

        if faltantes:
            print(f"    • Verificando batches e episódios específicos faltantes ({len(faltantes)})...", end=" ", flush=True)
            t1 = time.time()
            adicionar_torrents(buscar_torrents_nyaa(f"{anime_nome} batch", categoria="1_0", max_paginas=1))
            for ep_falta in faltantes[:4]:
                q_direcionada = f"{anime_nome} {ep_falta:02d}"
                adicionar_torrents(buscar_torrents_nyaa(q_direcionada, categoria="1_0", max_paginas=1))
            print(f"({time.time() - t1:.1f}s)", flush=True)

    # 3. Agrupar por Release Group
    grupos: Dict[str, Dict[int, Dict[str, Any]]] = {}
    batches_por_grupo: Dict[str, List[Dict[str, Any]]] = {}

    for t in torrents_brutos:
        if apenas_ptbr and not _contem_ptbr(t["titulo"]):
            continue

        grupo_nome = _identificar_grupo(t["titulo"])

        # Checar se é batch
        intervalo_batch = _extrair_intervalo_batch(t["titulo"])
        if intervalo_batch:
            ini, fim = intervalo_batch
            # Se bate com o filtro de episódios do usuário
            if episodios_filtro is None or any(ep in episodios_filtro for ep in range(ini, fim + 1)):
                batches_por_grupo.setdefault(grupo_nome, []).append(t)

        ep_chave = _extrair_chave_episodio(t["titulo"])
        if ep_chave is None:
            continue

        # O filtro do usuario ("20-30") compara sempre contra a parte inteira,
        # entao um episodio 20.5 (especial) ainda e' considerado se 20 ou 21
        # estiverem no filtro.
        if episodios_filtro is not None and int(ep_chave) not in episodios_filtro:
            continue

        grupo_eps = grupos.setdefault(grupo_nome, {})

        if ep_chave not in grupo_eps:
            grupo_eps[ep_chave] = t
        else:
            existente = grupo_eps[ep_chave]
            pref_low = qualidade_preferida.lower()
            if pref_low in t["titulo"].lower() and pref_low not in existente["titulo"].lower():
                grupo_eps[ep_chave] = t
            elif t["seeders"] > existente["seeders"]:
                grupo_eps[ep_chave] = t

    # 4. Montar lista de DownloadOption final
    resultado_final: Dict[str, List[DownloadOption]] = {}

    for g_nome, eps_dict in grupos.items():
        if not eps_dict:
            continue
        lista_options = []
        for ep_num in sorted(eps_dict.keys()):
            item = eps_dict[ep_num]
            qual = "1080p" if "1080p" in item["titulo"].lower() else ("720p" if "720p" in item["titulo"].lower() else "HD")
            ep_fmt = _formatar_episodio(ep_num)
            filename = sanitizar_nome_arquivo(f"{anime_nome} - E{ep_fmt}.mkv")
            confianca = _confianca_ptbr(item["titulo"])
            selo_ptbr = {
                "confirmado": "🇧🇷 PT-BR",
                "provavel": "🇧🇷? Multi-Sub (idioma não confirmado)",
                "desconhecido": ""
            }[confianca]
            label_final = f"Ep. {ep_fmt} · {filename}" + (f" [{selo_ptbr}]" if selo_ptbr else "")

            opt = DownloadOption(
                label=label_final,
                url=item["magnet"],
                protocol=Protocol.MAGNET,
                quality=qual,
                filename_hint=filename,
                metadata={
                    "episodio": ep_num,
                    "seeders": item["seeders"],
                    "tamanho": item["tamanho"],
                    "grupo": g_nome,
                    "titulo_completo": item["titulo"],
                    "ptbr_confianca": confianca,
                    "manifest_key": f"nyaa_{g_nome}_{anime_nome}_E{ep_fmt}"
                }
            )
            lista_options.append(opt)

        if lista_options:
            resultado_final[f"{g_nome} ({len(lista_options)} eps)"] = lista_options

    # Adicionar batches encontrados se houver
    for g_nome, b_list in batches_por_grupo.items():
        b_list.sort(key=lambda x: x["seeders"], reverse=True)
        for b in b_list[:3]:
            b_qual = "1080p" if "1080p" in b["titulo"].lower() else "HD"
            intervalo = _extrair_intervalo_batch(b["titulo"])
            confianca_batch = _confianca_ptbr(b["titulo"])
            selo_batch = {
                "confirmado": " [🇧🇷 PT-BR]",
                "provavel": " [🇧🇷? idioma não confirmado]",
                "desconhecido": ""
            }[confianca_batch]
            if intervalo:
                ini, fim = intervalo
                total_eps = fim - ini + 1
                batch_desc = f"Contém {total_eps} episódios (Eps. {ini} ao {fim})"
                label_batch = f"[Batch] {anime_nome} · {batch_desc}{selo_batch}"
            else:
                total_eps = None
                batch_desc = "Pacote completo de episódios"
                label_batch = f"[Batch] {b['titulo'][:55]}...{selo_batch}"

            opt_batch = DownloadOption(
                label=label_batch,
                url=b["magnet"],
                protocol=Protocol.MAGNET,
                quality=b_qual,
                filename_hint=f"{anime_nome} - Batch.mkv",
                metadata={
                    "episodio": "Batch",
                    "is_batch": True,
                    "seeders": b["seeders"],
                    "tamanho": b["tamanho"],
                    "grupo": f"{g_nome} (Batch)",
                    "batch_desc": batch_desc,
                    "batch_range": intervalo,
                    "batch_total_eps": total_eps,
                    "ptbr_confianca": confianca_batch,
                    "manifest_key": f"nyaa_batch_{b['magnet']}"
                }
            )
            chave_batch = f"📦 {g_nome} [Batch: {batch_desc}] ({b['seeders']} seeds)"
            resultado_final.setdefault(chave_batch, []).append(opt_batch)

    return resultado_final

def _score_opcao_mix(opt: DownloadOption, qualidade_pref: str) -> float:
    score = 0.0
    qual = (opt.quality or "").lower()
    lbl = (opt.filename_hint or opt.label).lower()

    if qualidade_pref.lower() in qual:
        score += 50.0
    elif "1080p" in lbl:
        score += 40.0
    elif "720p" in lbl:
        score += 20.0

    g_nome = opt.metadata.get("grupo", "").lower()
    if "erai" in g_nome:
        score += 35.0
    elif "judas" in g_nome:
        score += 30.0
    elif "subsplease" in g_nome:
        score += 25.0
    elif "darkmahou" in g_nome:
        score += 20.0
    else:
        score += 10.0

    seeds = opt.metadata.get("seeders", 0)
    if isinstance(seeds, int):
        score += min(seeds, 100) * 0.2
    elif seeds == "--":
        score += 15.0

    tam_str = opt.metadata.get("tamanho")
    if tam_str:
        m = re.match(r"([\d.,]+)\s*([KMGT]?i?B)", str(tam_str).strip(), re.IGNORECASE)
        if m:
            try:
                val = float(m.group(1).replace(",", "."))
                unit = m.group(2).upper()
                mult = {"B": 1, "KIB": 1024, "KB": 1000, "MIB": 1024**2, "MB": 1000**2, "GIB": 1024**3, "GB": 1000**3, "TIB": 1024**4, "TB": 1000**4}.get(unit, 1024**2)
                if val * mult > 4.5 * (1024**3):
                    score -= 300.0
            except Exception:
                pass

    return score

def gerar_auto_mix_consolidado(
    catalogo_grupos: Dict[str, List[DownloadOption]],
    episodios_desejados: Optional[Set[int]] = None,
    qualidade_preferida: str = "1080p"
) -> List[DownloadOption]:
    """
    Consolida o melhor mix multi-tracker/fansub para cobrir todos os episódios solicitados.
    Se Erai-raws tiver 7, Judas tiver 3 e outro tracker tiver 1, une os 11 episódios
    selecionando a melhor release individual para cada episódio.
    """
    episodes_map: Dict[Any, List[DownloadOption]] = {}
    for g_nome, opts in catalogo_grupos.items():
        if "batch" in g_nome.lower() or "auto-mix" in g_nome.lower():
            continue
        for opt in opts:
            ep = opt.metadata.get("episodio")
            # Aceita tanto episodios inteiros quanto fracionarios (13.5 = OVA/especial);
            # o filtro do usuario ainda compara contra a parte inteira do episodio.
            if isinstance(ep, (int, float)):
                if episodios_desejados is None or int(ep) in episodios_desejados:
                    episodes_map.setdefault(ep, []).append(opt)

    mix_final: List[DownloadOption] = []
    for ep in sorted(episodes_map.keys()):
        candidatos = episodes_map[ep]
        melhor = max(candidatos, key=lambda o: _score_opcao_mix(o, qualidade_preferida))
        origem = melhor.metadata.get("grupo", "Tracker")

        opt_mix = DownloadOption(
            label=f"{melhor.label} [{origem}]",
            url=melhor.url,
            protocol=melhor.protocol,
            quality=melhor.quality,
            filename_hint=melhor.filename_hint,
            metadata={
                **melhor.metadata,
                "manifest_key": melhor.stable_key(),
                "origem_mix": origem
            }
        )
        mix_final.append(opt_mix)

    return mix_final

def smart_search_nyaa(
    anime_nome: str,
    qualidade_preferida: str = "1080p",
    apenas_ptbr: bool = True,
    max_episodios: int = 12,
    episodios_filtro: Optional[Set[int]] = None
) -> List[DownloadOption]:
    grupos = search_nyaa_grouped(
        anime_nome=anime_nome,
        qualidade_preferida=qualidade_preferida,
        apenas_ptbr=apenas_ptbr,
        episodios_filtro=episodios_filtro
    )
    if not grupos:
        return []
    # Tenta obter o auto-mix completo primeiro
    mix = gerar_auto_mix_consolidado(grupos, episodios_filtro, qualidade_preferida)
    if mix:
        return mix[:max_episodios]
    melhor_grupo = max(grupos.values(), key=len)
    return melhor_grupo[:max_episodios]


# ==============================================================================
# 4. erai_raws_scraper.py (RSS Oficial Autenticado do Erai-raws)
# ==============================================================================
"""
erai_raws_scraper.py (P2P Ingestion Toolkit v6.5 - Erai-raws RSS Direct Ingestion)
==================================================================================
Scraper de alta performance para o feed RSS autenticado do Erai-raws (erai-raws.info).
Permite buscar episódios por anime com filtragem por qualidade (1080p, 720p, SD),
tipo de release (AVC vs HEVC), e legendas (suporte prioritário a [br] / PT-BR).
"""


import os
import re
import time
import urllib.parse
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET
from typing import Any, Dict, List, Optional, Set, Tuple

try:
    import anitopy
except ImportError:
    anitopy = None


ERAI_BASE_FEED = "https://www.erai-raws.info/episodes/feed/"
ERAI_BATCHES_FEED = "https://www.erai-raws.info/batches/feed/"
DEFAULT_ERAI_TOKEN = "690e1a51c6abda5c9930daa432d581a7"
ERAI_NS = "{https://www.erai-raws.info/rss-page/}"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
}

PTBR_ERAI_KEYWORDS = ["[br]", "[pt-br]", "[pt]", "portuguese", "portugues", "multi-sub", "multisub"]


def _extrair_numero_episodio_legado(titulo: str) -> Optional[int]:
    """Heurística manual regex de fallback para extração do número do episódio."""
    ANOS_IGNORADOS = {2020, 2021, 2022, 2023, 2024, 2025, 2026, 2027}
    RESOLUCOES = {480, 720, 1080, 2160}

    def _e_resolucao_local(num: int, t: str, fim_match: int) -> bool:
        if num not in RESOLUCOES:
            return False
        trecho_seguinte = t[fim_match:fim_match + 1]
        return trecho_seguinte.lower() == "p"

    padroes_alta_confianca = [
        r"(?:[\s_\-\[])(?:EP|E|Epis[oó]dio|Cap[ií]tulo)\s*0*([0-9]{1,4})(?:\.[0-9]+)?(?:[\s_\-\]\.\)]|$)",
        r"(?:S\d+E)\s*0*([0-9]{1,4})(?:\.[0-9]+)?",
        r"(?:[\s_]+)-\s*0*([0-9]{1,4})(?:\.[0-9]+)?(?:v\d+)?(?:[\s_\[\.\(]|$)",
        r"#\s*0*([0-9]{1,4})(?:[\s_\-\]\.\)]|$)",
    ]
    for p in padroes_alta_confianca:
        m = re.search(p, titulo, re.IGNORECASE)
        if m:
            num = int(m.group(1))
            if num in ANOS_IGNORADOS or _e_resolucao_local(num, titulo, m.end(1)):
                continue
            return num

    candidatos = []
    for m in re.finditer(r"(?:[\s_\[\(])0*([0-9]{1,4})(?:\.[0-9]+)?(?:[\s_\]\)])", titulo):
        num = int(m.group(1))
        if num in ANOS_IGNORADOS or _e_resolucao_local(num, titulo, m.end(1)):
            continue
        candidatos.append((m.start(), num))

    if not candidatos:
        return None

    candidatos.sort(key=lambda c: c[0], reverse=True)
    return candidatos[0][1]


def _extrair_numero_episodio_float(titulo: str) -> Optional[float]:
    """Extrai o numero completo do episodio (preservando decimais, ex: 13.5 para
    OVAs/especiais) com anitopy (e fallback legado). Evita que 'Ep 13.5' colida
    com 'Ep 13' na mesma chave inteira do agrupamento por episodio."""
    if not titulo or not titulo.strip():
        return None

    if anitopy is None:
        legado = _extrair_numero_episodio_legado(titulo)
        return float(legado) if legado is not None else None

    try:
        resultado = anitopy.parse(titulo)
    except Exception:
        legado = _extrair_numero_episodio_legado(titulo)
        return float(legado) if legado is not None else None

    if not resultado:
        return None

    ep = resultado.get("episode_number")
    if ep is None:
        return None

    if isinstance(ep, list):
        if not ep:
            return None
        ep = ep[0]

    try:
        return float(ep)
    except (ValueError, TypeError):
        return None


def _extrair_numero_episodio(titulo: str) -> Optional[int]:
    """Extrai número do episódio (parte inteira) com anitopy (e fallback legado).
    Para logica de agrupamento que precisa diferenciar episodios fracionarios,
    use _extrair_chave_episodio."""
    ep_float = _extrair_numero_episodio_float(titulo)
    if ep_float is None:
        return None
    return int(ep_float)


def _extrair_chave_episodio(titulo: str):
    """Retorna int quando o episodio e' inteiro, ou float quando fracionario
    (ex: 13.5), evitando colisao com o episodio regular de mesmo numero base."""
    ep_float = _extrair_numero_episodio_float(titulo)
    if ep_float is None:
        return None
    return int(ep_float) if ep_float == int(ep_float) else ep_float


def _extrair_intervalo_batch(titulo: str) -> Optional[Tuple[int, int]]:
    """Identifica se o título representa um lote/batch (ex: 01-12 ou [0001-0130])."""
    m = re.search(r"[\[\(\s_]0*([0-9]{1,4})\s*[-~]\s*0*([0-9]{1,4})[\]\)\s_]", titulo)
    if m:
        s, e = int(m.group(1)), int(m.group(2))
        if s < e and (e - s) < 500:
            return s, e
    return None


def _sanitizar_url(url: str) -> str:
    """Garante que caminhos de URLs com espaços e caracteres especiais sejam codificados."""
    if not url or url.startswith("magnet:"):
        return url
    parts = urllib.parse.urlsplit(url)
    clean_path = urllib.parse.quote(parts.path, safe="/")
    return urllib.parse.urlunsplit((parts.scheme, parts.netloc, clean_path, parts.query, parts.fragment))


ERAI_DEBUG = False  # Erai-raws desativado no pipeline (Célula 3) — logs de diagnóstico silenciados

def _log_erai_debug(msg: str) -> None:
    if ERAI_DEBUG:
        print(f"    🔧 [erai-debug] {msg}")

def _redact_token(url: str) -> str:
    """Remove o valor do token da URL antes de imprimir, para nao vazar
    credenciais pessoais em logs que podem ser compartilhados."""
    return re.sub(r"(token=)[^&]+", r"\1***REDACTED***", url)

def _fetch_rss_bytes_uma_tentativa(url: str, timeout: int = 12) -> bytes:
    """Obtém o XML do feed RSS com cascata resiliente: requests -> curl -> urllib.
    Cada etapa e' logada (metodo, tempo, resultado) via _log_erai_debug, para que
    uma falha silenciosa deixe de ser um misterio -- fica visivel exatamente qual
    metodo foi tentado, quanto tempo levou e por que falhou (excecao, status HTTP,
    corpo sem a tag <rss>/<item> esperada etc.)."""
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9,pt-BR;q=0.8,pt;q=0.7",
        "Sec-Ch-Ua": '"Chromium";v="124", "Google Chrome";v="124"',
        "Sec-Ch-Ua-Mobile": "?0",
        "Sec-Ch-Ua-Platform": '"Windows"',
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none",
        "Sec-Fetch-User": "?1",
        "Upgrade-Insecure-Requests": "1"
    }

    url_log = _redact_token(url)
    _log_erai_debug(f"URL: {url_log}")

    # 1. Tenta requests (suporta TLS moderno e gzip automático)
    t0 = time.monotonic()
    try:
        import requests
        r = requests.get(url, headers=headers, timeout=timeout)
        dt = time.monotonic() - t0
        tem_rss = b"<rss" in r.content or b"<item" in r.content
        _log_erai_debug(f"[requests] status={r.status_code} bytes={len(r.content)} tem_tag_rss={tem_rss} tempo={dt:.2f}s")
        if r.status_code == 200 and tem_rss:
            return r.content
        if r.status_code != 200:
            _log_erai_debug(f"[requests] corpo da resposta (primeiros 200 chars): {r.text[:200]!r}")
    except Exception as e:
        dt = time.monotonic() - t0
        _log_erai_debug(f"[requests] EXCEÇÃO {type(e).__name__}: {e} (apos {dt:.2f}s)")

    # 2. Tenta curl (libcurl do Linux no Colab tem alta resiliência a CF)
    t0 = time.monotonic()
    try:
        import subprocess
        cmd = ["curl", "-s", "-L", "-A", headers["User-Agent"], "-w", "\n%{http_code}", url]
        p = subprocess.run(cmd, capture_output=True, timeout=timeout)
        dt = time.monotonic() - t0
        corpo, _, http_code = p.stdout.rpartition(b"\n")
        tem_rss = b"<rss" in corpo or b"<item" in corpo
        _log_erai_debug(f"[curl] returncode={p.returncode} http={http_code.decode(errors='replace')} bytes={len(corpo)} tem_tag_rss={tem_rss} tempo={dt:.2f}s")
        if p.returncode != 0:
            _log_erai_debug(f"[curl] stderr: {p.stderr[:200]!r}")
        if p.returncode == 0 and tem_rss:
            return corpo
    except Exception as e:
        dt = time.monotonic() - t0
        _log_erai_debug(f"[curl] EXCEÇÃO {type(e).__name__}: {e} (apos {dt:.2f}s)")

    # 3. Fallback urllib
    t0 = time.monotonic()
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            corpo = resp.read()
            dt = time.monotonic() - t0
            _log_erai_debug(f"[urllib] status={resp.status} bytes={len(corpo)} tempo={dt:.2f}s")
            return corpo
    except Exception as e:
        dt = time.monotonic() - t0
        _log_erai_debug(f"[urllib] EXCEÇÃO {type(e).__name__}: {e} (apos {dt:.2f}s) -- todos os 3 metodos falharam")
        raise


_ULTIMA_CHAMADA_ERAI: List[float] = [0.0]
_ERAI_INTERVALO_MINIMO = 1.5  # segundos entre requisicoes sucessivas ao mesmo host

def _espacar_chamadas_erai() -> None:
    """Garante um intervalo minimo entre requisicoes sucessivas ao erai-raws.info.

    Motivo: um lote de busca dispara ate' 8 requisicoes (2 feeds x 2 variacoes
    de titulo x 2 variacoes de filtro de idioma) em rajada, quase simultaneas.
    Um erro HTTP 502 sob esse padrao de rajada, com uma requisicao MANUAL isolada
    do mesmo usuario funcionando normalmente, e' consistente com o backend do
    site (provavelmente nginx + PHP-FPM, como a maioria dos sites WordPress)
    esgotando seu pool de processos (pm.max_children) sob rajada -- uma causa
    documentada e comum de 502 que nao tem relacao com bloqueio de rede/IP.
    """
    agora = time.monotonic()
    decorrido = agora - _ULTIMA_CHAMADA_ERAI[0]
    if decorrido < _ERAI_INTERVALO_MINIMO:
        time.sleep(_ERAI_INTERVALO_MINIMO - decorrido)
    _ULTIMA_CHAMADA_ERAI[0] = time.monotonic()

def _fetch_rss_bytes(url: str, timeout: int = 12, max_retries: int = 2) -> bytes:
    """Wrapper de _fetch_rss_bytes_uma_tentativa com duas camadas de resiliencia:
    (1) espaçamento minimo entre chamadas sucessivas (ver _espacar_chamadas_erai)
    e (2) retry com backoff especificamente para 502/503/504 -- erros tipicamente
    TRANSITORIOS de sobrecarga/instabilidade do backend, diferente de 401/403
    (credencial invalida) ou 404, que sao definitivos e nao adianta retentar."""
    ultima_excecao: Optional[Exception] = None
    for tentativa in range(max_retries + 1):
        _espacar_chamadas_erai()
        try:
            return _fetch_rss_bytes_uma_tentativa(url, timeout=timeout)
        except urllib.error.HTTPError as e:
            ultima_excecao = e
            if e.code in (502, 503, 504) and tentativa < max_retries:
                espera = 2.0 * (tentativa + 1)
                _log_erai_debug(f"HTTP {e.code} (provavel instabilidade transitoria do backend) -- aguardando {espera:.0f}s antes de tentar de novo ({tentativa + 1}/{max_retries})")
                time.sleep(espera)
                continue
            raise
        except Exception as e:
            ultima_excecao = e
            raise
    if ultima_excecao:
        raise ultima_excecao
    return b""


def buscar_items_erai_rss(
    query: str,
    token: Optional[str] = None,
    qualidade: str = "1080p",
    feed_type: str = "magnet",
    subs: Optional[str] = None,
    timeout: int = 15,
    base_feed_url: str = ERAI_BASE_FEED
) -> List[Dict[str, Any]]:
    """
    Consulta um endpoint RSS do Erai-raws e extrai itens estruturados.

    IMPORTANTE: o endpoint 'episodes/feed/' tende a cobrir majoritariamente
    releases "Airing" (temporada atual), enquanto 'batches/feed/' (ver
    ERAI_BATCHES_FEED), na pratica, responde a busca por texto (parametro 's')
    com o catalogo mais amplo do anime buscado -- incluindo animes ja
    encerrados ha' anos, cada episodio em varias resolucoes. Por isso
    search_erai_grouped consulta os dois e mescla os resultados.
    """
    auth_token = token or os.environ.get("ERAI_TOKEN") or DEFAULT_ERAI_TOKEN

    # Normalização de qualidade para os padrões do Erai: 1080p, 720p, SD
    res_val = "1080p"
    q_low = qualidade.lower()
    if "720" in q_low:
        res_val = "720p"
    elif "sd" in q_low or "480" in q_low:
        res_val = "SD"
    elif "qualquer" in q_low or "all" in q_low:
        res_val = ""

    params: List[Tuple[str, str]] = []
    if res_val:
        params.append(("res", res_val))
    params.append(("type", feed_type))
    params.append(("token", auth_token))
    if query:
        params.append(("s", query))
    if subs:
        # Formato subs[0]=xx confirmado por integracoes de terceiros que tambem
        # consultam este feed (Jackett/Prowlarr); subs[]=xx (notacao de array
        # vazio) tambem e' aceito pelo gerador de link do proprio site, mas o
        # formato indexado e' o documentado de forma mais consistente.
        params.append(("subs[0]", subs))

    url = f"{base_feed_url}?{urllib.parse.urlencode(params)}"
    _log_erai_debug(f"Consultando feed '{base_feed_url.rstrip('/').rsplit('/', 1)[-1]}' com query={query!r} qualidade={qualidade!r} subs={subs!r}")

    itens: List[Dict[str, Any]] = []
    try:
        data = _fetch_rss_bytes(url, timeout=timeout)
        if not data:
            _log_erai_debug("Resposta vazia (0 bytes) -- nenhum item para processar")
            return itens

        try:
            root = ET.fromstring(data)
        except ET.ParseError as pe:
            _log_erai_debug(f"XML invalido/malformado: {pe} -- primeiros 300 chars da resposta: {data[:300]!r}")
            return itens

        items_xml = root.findall(".//item")
        _log_erai_debug(f"XML parseado com sucesso: {len(items_xml)} elemento(s) <item> encontrado(s) no feed bruto")

        for item in items_xml:
            title = item.findtext("title") or ""
            link = item.findtext("link") or ""
            pub_date = item.findtext("pubDate") or ""

            # Tags de namespace customizadas do Erai
            res = item.findtext(f"{ERAI_NS}resolution") or ""
            codec = item.findtext(f"{ERAI_NS}infomkv") or ""
            linktype = item.findtext(f"{ERAI_NS}linktype") or ""
            size = item.findtext(f"{ERAI_NS}size") or ""
            infohash = item.findtext(f"{ERAI_NS}infohash") or ""
            subtitles = item.findtext(f"{ERAI_NS}subtitles") or ""
            category = item.findtext(f"{ERAI_NS}category") or ""

            # Se for magnet e não vier no link mas houver infohash:
            if (not link or not link.startswith("magnet:")) and infohash:
                encoded_dn = urllib.parse.quote(title)
                tr = (
                    "&tr=http%3A%2F%2Fnyaa.tracker.wf%3A7777%2Fannounce"
                    "&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce"
                    "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
                    "&tr=udp%3A%2F%2Fexodus.desync.com%3A6969%2Fannounce"
                    "&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"
                )
                magnet_url = f"magnet:?xt=urn:btih:{infohash}&dn={encoded_dn}{tr}"
            else:
                magnet_url = link if link.startswith("magnet:") else None

            t_low = title.lower()
            s_low = subtitles.lower()
            if s_low.strip():
                # O campo erai:subtitles vem do proprio grupo (metadado oficial da
                # release) -- e' a fonte mais confiavel que existe neste pipeline.
                # Quando presente, ele MANDA: nao deixamos uma keyword generica do
                # titulo (ex: "Multi-Sub") sobrepor um "sem PT-BR" ja confirmado
                # pela propria fonte oficial.
                has_ptbr = "[br]" in s_low or "[pt]" in s_low or "br" in s_low or "pt" in s_low
                ptbr_confianca = "confirmado" if has_ptbr else "desconhecido"
            else:
                # Sem o campo oficial disponivel: cai no heuristico generico de
                # titulo, que ja distingue "confirmado" (idioma explicito) de
                # "provavel" (alegacao generica tipo Multi-Sub, que NAO prova que
                # o portugues esteja incluso).
                ptbr_confianca = _confianca_ptbr(title)
                has_ptbr = ptbr_confianca in ("confirmado", "provavel")
            is_hevc = "hevc" in t_low or "hevc" in codec.lower() or "x265" in t_low

            itens.append({
                "title": title,
                "link": link,
                "magnet": magnet_url or link,
                "is_magnet": link.startswith("magnet:") or bool(magnet_url),
                "torrent_url": _sanitizar_url(link) if not link.startswith("magnet:") else None,
                "pubDate": pub_date,
                "resolution": res,
                "codec": codec,
                "linktype": linktype,
                "size": size,
                "infohash": infohash,
                "subtitles": subtitles,
                "category": category,
                "has_ptbr": has_ptbr,
                "ptbr_confianca": ptbr_confianca,
                "is_hevc": is_hevc,
                "ep_num": _extrair_numero_episodio(title),
                "ep_chave": _extrair_chave_episodio(title),
                "batch_range": _extrair_intervalo_batch(title)
            })
    except Exception as e:
        _log_erai_debug(f"EXCEÇÃO ao processar feed: {type(e).__name__}: {e}")

    _log_erai_debug(f"Retornando {len(itens)} item(ns) estruturado(s) deste feed")
    return itens


def search_erai_grouped(
    anime_nome: str,
    qualidade_preferida: str = "1080p",
    apenas_ptbr: bool = False,
    episodios_filtro: Optional[Set[int]] = None,
    token: Optional[str] = None
) -> Dict[str, List[DownloadOption]]:
    """
    Busca episódios no feed do Erai-raws e agrupa por grupo/codec (HEVC vs AVC)
    em conformidade com o formato DownloadOption aceito pelo pipeline.

    Consulta DOIS endpoints do Erai-raws e mescla os resultados (deduplicando
    por infohash/link):
    - episodes/feed/: cobre majoritariamente releases "Airing" (temporada atual)
    - batches/feed/: na pratica, responde a busca por texto ('s=') com um
      catalogo bem mais amplo, incluindo animes ja encerrados ha' anos
      (confirmado empiricamente: um anime de 2022 retornou seus 12 episodios
      neste endpoint mesmo sem estar mais "Airing").
    Consultar so' um dos dois deixa de fora releases que só aparecem no outro.
    """
    def _merge_sem_duplicar(*listas: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        vistos = set()
        mesclado: List[Dict[str, Any]] = []
        for lista in listas:
            for item in lista:
                chave = item.get("infohash") or item.get("link") or item.get("title")
                if chave and chave in vistos:
                    continue
                if chave:
                    vistos.add(chave)
                mesclado.append(item)
        return mesclado

    def _buscar_nos_dois_feeds(query: str, subs: Optional[str]) -> List[Dict[str, Any]]:
        itens_episodes = buscar_items_erai_rss(
            query=query, token=token, qualidade=qualidade_preferida,
            feed_type="magnet", subs=subs, base_feed_url=ERAI_BASE_FEED
        )
        itens_batches = buscar_items_erai_rss(
            query=query, token=token, qualidade=qualidade_preferida,
            feed_type="magnet", subs=subs, base_feed_url=ERAI_BATCHES_FEED
        )
        mesclado = _merge_sem_duplicar(itens_episodes, itens_batches)
        _log_erai_debug(
            f"Tentativa query={query!r} subs={subs!r}: "
            f"episodes/feed={len(itens_episodes)} batches/feed={len(itens_batches)} "
            f"apos mesclar/deduplicar={len(mesclado)}"
        )
        return mesclado

    raw_items = _buscar_nos_dois_feeds(anime_nome, subs="br" if apenas_ptbr else None)

    # Fallback: se não encontrou com query exata, tenta buscar sem filtros de legenda
    if not raw_items and apenas_ptbr:
        _log_erai_debug("0 itens com subs[]=br -- tentando novamente sem filtro de idioma no servidor")
        raw_items = _buscar_nos_dois_feeds(anime_nome, subs=None)

    if not raw_items:
        # Tenta variação separando nomes colados (ex: DanDaDan -> Dan Da Dan)
        spaced_title = re.sub(r"([a-z])([A-Z])", r"\1 \2", anime_nome)
        if spaced_title != anime_nome:
            _log_erai_debug(f"0 itens com título original -- tentando variação espaçada: {spaced_title!r}")
            raw_items = _buscar_nos_dois_feeds(spaced_title, subs=None)

    if not raw_items:
        _log_erai_debug(f"Nenhum item encontrado em nenhuma tentativa para {anime_nome!r} -- retornando catálogo vazio")
        return {}

    grupos: Dict[str, Dict[int, Dict[str, Any]]] = {
        "Erai-raws [HEVC Leve] (Multi-Sub PT-BR)": {},
        "Erai-raws [AVC Original] (Multi-Sub PT-BR)": {}
    }
    batches: List[Dict[str, Any]] = []

    for item in raw_items:
        if apenas_ptbr and not item["has_ptbr"]:
            continue

        if item["batch_range"] or "batch" in item["category"].lower():
            batches.append(item)
            continue

        ep_chave = item["ep_chave"]
        if ep_chave is None:
            continue

        # O filtro do usuario compara sempre contra a parte inteira do episodio,
        # entao um especial 20.5 ainda aparece se 20 ou 21 estiverem no filtro.
        if episodios_filtro is not None and int(ep_chave) not in episodios_filtro:
            continue

        g_key = "Erai-raws [HEVC Leve] (Multi-Sub)" if item["is_hevc"] else "Erai-raws [AVC Original] (Multi-Sub)"

        # Evita duplicatas para o mesmo episódio no mesmo codec (mantém o mais recente)
        if ep_chave not in grupos[g_key]:
            grupos[g_key][ep_chave] = item

    resultado_final: Dict[str, List[DownloadOption]] = {}

    for g_nome, eps_dict in grupos.items():
        if not eps_dict:
            continue
        lista_options: List[DownloadOption] = []
        for ep_chave in sorted(eps_dict.keys()):
            item = eps_dict[ep_chave]
            res_label = item["resolution"] or ("1080p" if "1080p" in item["title"].lower() else "HD")
            safe_anime = sanitizar_nome_arquivo(anime_nome, max_len=100)
            codec_tag = "HEVC" if item["is_hevc"] else "AVC"
            ep_fmt = _formatar_episodio(ep_chave)
            filename = f"{safe_anime} - E{ep_fmt} [{codec_tag}].mkv"

            target_url = item["magnet"] or item["link"]
            proto = Protocol.MAGNET if target_url.startswith("magnet:") else Protocol.HTTP

            confianca = item.get("ptbr_confianca", "desconhecido")
            selo_ptbr = {
                "confirmado": "🇧🇷 PT-BR",
                "provavel": "🇧🇷? Multi-Sub (idioma não confirmado)",
                "desconhecido": "⚠️ sem PT-BR confirmado"
            }[confianca]

            opt = DownloadOption(
                label=f"Ep. {ep_fmt} · {filename} [{selo_ptbr}]",
                url=target_url,
                protocol=proto,
                quality=res_label,
                filename_hint=filename,
                metadata={
                    "episodio": ep_chave,
                    "seeders": 15,  # Sementes padrão da swarm Erai-raws
                    "tamanho": item["size"] or "Desconhecido",
                    "grupo": g_nome,
                    "codec": item["codec"],
                    "subtitles": item["subtitles"],
                    "titulo_completo": item["title"],
                    "ptbr_confianca": confianca,
                    "manifest_key": f"erai_{codec_tag}_{safe_anime}_E{ep_fmt}"
                }
            )
            lista_options.append(opt)

        if lista_options:
            resultado_final[f"{g_nome} ({len(lista_options)} eps)"] = lista_options

    if batches:
        for b in batches[:3]:
            intervalo = b["batch_range"]
            batch_desc = f"Lote ({intervalo[0]}-{intervalo[1]})" if intervalo else "Pacote Completo"
            label_batch = f"[Batch] {anime_nome} · {batch_desc}"
            safe_anime = sanitizar_nome_arquivo(anime_nome, max_len=100)
            target_url = b["magnet"] or b["link"]
            proto = Protocol.MAGNET if target_url.startswith("magnet:") else Protocol.HTTP

            opt_batch = DownloadOption(
                label=label_batch,
                url=target_url,
                protocol=proto,
                quality=b["resolution"] or "1080p",
                filename_hint=f"{safe_anime} - Batch.mkv",
                metadata={
                    "episodio": "Batch",
                    "is_batch": True,
                    "seeders": 15,
                    "tamanho": b["size"] or "Desconhecido",
                    "grupo": "Erai-raws (Batch)",
                    "batch_desc": batch_desc,
                    "batch_range": intervalo,
                    "manifest_key": f"erai_batch_{b['infohash'] or b['title']}"
                }
            )
            chave_batch = f"📦 Erai-raws [Batch: {batch_desc}]"
            resultado_final.setdefault(chave_batch, []).append(opt_batch)

    return resultado_final


# ==============================================================================
# 5. darkmahou_scraper.py (com filtro de episódios)
# ==============================================================================
"""
darkmahou_scraper.py
====================
Scraper para darkmahou.io com busca inteligente, normalização de temporada,
suporte a filtro de episódios específicos e extração de opções de download.
"""


import re
import html
import time
import requests
import urllib3
from bs4 import BeautifulSoup
from typing import List, Dict, Any, Optional, Set



urllib3.disable_warnings()

BASE_URL = "https://darkmahou.io"
HEADERS = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

def _dm_get(url, params=None, session=None):
    """GET no DarkMahou com TLS verificado. Só se o certificado falhar, usa conexão sem verificação (com aviso)."""
    alvo = session or requests
    try:
        return alvo.get(url, params=params, headers=HEADERS, timeout=15, verify=True)
    except requests.exceptions.SSLError as exc:
        _registrar_erro(exc, "DarkMahou: certificado não validou")
        print("⚠️ DarkMahou: o certificado não validou; usando conexão sem verificação só para este site.")
        return alvo.get(url, params=params, headers=HEADERS, timeout=15, verify=False)


def get_session() -> requests.Session:
    s = requests.Session()
    s.headers.update(HEADERS)
    s.verify = True
    return s

def buscar_animes_raw(termo: str, session: Optional[requests.Session] = None) -> List[Dict[str, str]]:
    s = session or get_session()
    try:
        r = _dm_get(f"{BASE_URL}/", params={'s': termo}, session=s)
        if r.status_code == 429:
            try:
                retry_after = float(r.headers.get("Retry-After", 2))
            except (TypeError, ValueError):
                retry_after = 2.0
            time.sleep(min(retry_after, 10.0))
            r = _dm_get(f"{BASE_URL}/", params={'s': termo}, session=s)
        if r.status_code != 200:
            return []
        soup = BeautifulSoup(r.text, 'html.parser')
        res = []
        seen = set()
        for article in soup.find_all('article'):
            a_tag = article.find('a', href=True)
            if not a_tag:
                continue
            href = a_tag['href']
            if href in seen or 'darkmahou.io' not in href:
                continue
            h_tag = article.find(['h1', 'h2', 'h3', 'h4'])
            titulo = h_tag.get_text(strip=True) if h_tag else a_tag.get('title', '').strip()
            if not titulo:
                titulo = a_tag.get_text(strip=True)
            if not titulo:
                titulo = href.rstrip('/').split('/')[-1].replace('-', ' ').title()
            res.append({'nome': html.unescape(titulo), 'url': href})
            seen.add(href)
        return res
    except Exception:
        return []

def smart_search_darkmahou(termo_busca: str, max_resultados: int = 8) -> List[Dict[str, Any]]:
    info = parse_season(termo_busca)
    session = get_session()
    candidatos_encontrados: Dict[str, Dict[str, Any]] = {}

    # 1. Tenta busca direta primeiro para evitar chamadas de rede desnecessárias (< 1s)
    res_direta = buscar_animes_raw(termo_busca, session=session)
    for anime in res_direta:
        url = anime['url']
        score = score_anime_match(anime['nome'], info.base_title, info.season_number)
        candidatos_encontrados[url] = {
            'nome': anime['nome'],
            'url': url,
            'score': score,
            'encontrado_por': termo_busca
        }
    if any(c['score'] >= 80 for c in candidatos_encontrados.values()):
        lista_ranqueada = sorted(candidatos_encontrados.values(), key=lambda x: x['score'], reverse=True)
        return lista_ranqueada[:max_resultados]

    # 2. Fallback para expansão via variantes e Jikan se a busca direta não encontrou alta pontuação
    variantes = generate_search_variants(termo_busca)
    try:
        jikan = JikanResolver()
        jikan_titles = jikan.get_romaji_and_synonyms(info.base_title)
        for jt in jikan_titles:
            if info.season_number:
                v = f"{jt} {info.season_number}ª Temporada"
                if v not in variantes:
                    variantes.append(v)
                if jt not in variantes:
                    variantes.append(jt)
            elif jt not in variantes:
                variantes.append(jt)
    except Exception as _exc:
        _registrar_erro(_exc)

    for v in variantes:
        if v == termo_busca:
            continue
        res = buscar_animes_raw(v, session=session)
        for anime in res:
            url = anime['url']
            if url not in candidatos_encontrados:
                score = score_anime_match(anime['nome'], info.base_title, info.season_number)
                candidatos_encontrados[url] = {
                    'nome': anime['nome'],
                    'url': url,
                    'score': score,
                    'encontrado_por': v
                }
        if any(c['score'] >= 80 for c in candidatos_encontrados.values()):
            break

    lista_ranqueada = sorted(candidatos_encontrados.values(), key=lambda x: x['score'], reverse=True)
    return lista_ranqueada[:max_resultados]

def extrair_catalogo_darkmahou(
    url_anime: str,
    qualidade_preferida: str = "1080p",
    max_episodios: int = 12,
    episodios_filtro: Optional[Set[int]] = None
) -> List[DownloadOption]:
    """
    Extrai o catalogo de episodios da pagina do anime no DarkMahou. Resiliente a
    bloqueios HTTP (403 Cloudflare, 429 rate-limit, 503 indisponivel) e a erros
    de rede/timeout: em qualquer uma dessas situacoes retorna lista vazia em vez
    de propagar a excecao, permitindo que o pipeline continue com as demais fontes.
    """
    try:
        r = _dm_get(url_anime)
    except Exception:
        return []

    if r.status_code == 429:
        # Rate limit: uma unica tentativa extra respeitando Retry-After, sem
        # insistir agressivamente (o restante do pipeline nao deve travar aqui).
        try:
            retry_after = float(r.headers.get("Retry-After", 2))
        except (TypeError, ValueError):
            retry_after = 2.0
        time.sleep(min(retry_after, 10.0))
        try:
            r = _dm_get(url_anime)
        except Exception:
            return []

    if r.status_code in (403, 503):
        # Bloqueio de Cloudflare ou servico indisponivel: nao ha' o que extrair.
        return []

    if r.status_code != 200:
        return []

    try:
        soup = BeautifulSoup(r.text, 'html.parser')
    except Exception:
        return []

    # Palavras que indicam que o bloco e' um PACOTE (temporada/BD completo),
    # nao um episodio isolado -- ex: "1a Temporada BD Completo Legendado".
    # BUG CORRIGIDO: antes, quando a regex de episodio nao batia, o codigo
    # usava `i` (o INDICE da tabela na pagina) como numero do episodio -- entao
    # um bloco de temporada inteira, por ser a 1a tabela da pagina, virava
    # "Ep. 01" mesmo representando TODOS os episodios. Isso enganava tanto a
    # exibicao (parecia um unico episodio) quanto a logica de deduplicacao por
    # numero de episodio (Celula 4). Agora, blocos de pacote sao marcados
    # explicitamente como episodio="Batch" (mesma convencao ja usada pelos
    # pacotes do Nyaa), em vez de adivinhar um numero errado.
    _PALAVRAS_PACOTE_DARKMAHOU = ("temporada", "completo", "complete", "batch", "season", "pack")

    tables = soup.find_all('table')
    catalogo: List[DownloadOption] = []

    for i, table in enumerate(tables, 1):
        prev = table.find_previous(['h2', 'h3', 'h4', 'strong', 'p', 'b'])
        header_text = prev.get_text(strip=True) if prev else f"Episódio {i}"

        # Extrair número de episódio
        m_ep = re.search(r"(?:Epis[oó]dio|EP|E)\s*0*([0-9]{1,4})", header_text, re.IGNORECASE)
        header_lower = header_text.lower()
        e_pacote = any(p in header_lower for p in _PALAVRAS_PACOTE_DARKMAHOU)

        if m_ep:
            ep_num = int(m_ep.group(1))
        elif e_pacote:
            ep_num = "Batch"
        else:
            # Nem a regex bateu, nem o texto indica pacote -- nao ha' como
            # identificar o episodio com confianca. Preferimos marcar como
            # "Batch" (tratado como pendente sempre, nunca falsamente
            # deduplicado) a adivinhar um numero errado via indice da tabela.
            ep_num = "Batch"

        # Se houver filtro de episódios específicos (ex: 20-30), aplicar aqui
        # -- pacotes (episodio="Batch") nao tem como ser filtrados por numero
        # individual, entao sempre passam (o usuario decide na hora de marcar).
        if episodios_filtro is not None and isinstance(ep_num, int) and ep_num not in episodios_filtro:
            continue

        links = []
        for a in table.find_all('a', href=True):
            href = a['href'].strip()
            if not href or href.startswith('#'):
                continue
            label = html.unescape(a.get_text(strip=True))
            protocol = Protocol.MAGNET if href.startswith('magnet:') else (Protocol.GDRIVE if 'drive.google' in href else Protocol.HTTP)
            qual = '1080p' if '1080p' in (href + label).lower() else ('720p' if '720p' in (href + label).lower() else 'HD')
            links.append({
                'label': label,
                'qualidade': qual,
                'protocol': protocol,
                'url': href
            })

        if links:
            opcoes_ordenadas = []
            for l in links:
                if qualidade_preferida != 'Qualquer' and l['qualidade'] == qualidade_preferida:
                    opcoes_ordenadas.insert(0, l)
                else:
                    opcoes_ordenadas.append(l)

            melhor = opcoes_ordenadas[0]
            ep_str_arquivo = f"{ep_num:02d}" if isinstance(ep_num, int) else "Batch"
            ep_str_label = f"Ep. {ep_num:02d}" if isinstance(ep_num, int) else "📦 Pacote"
            filename = sanitizar_nome_arquivo(f"DarkMahou - E{ep_str_arquivo}.mp4") if melhor['protocol'] == Protocol.GDRIVE else sanitizar_nome_arquivo(f"DarkMahou - E{ep_str_arquivo}.mkv")
            opt = DownloadOption(
                label=f"{ep_str_label} · {header_text} [{melhor['qualidade']} - {melhor['label']}]",
                url=melhor['url'],
                protocol=melhor['protocol'],
                quality=melhor['qualidade'],
                filename_hint=filename,
                metadata={
                    'episodio': ep_num,
                    'bloco': header_text,
                    'grupo': 'DarkMahou (Servidor)',
                    'tamanho': 'Direto/Drive',
                    'seeders': '--',
                    'manifest_key': melhor['url'],
                    'alternativas': [o['url'] for o in opcoes_ordenadas[1:]]
                }
            )
            catalogo.append(opt)

        if max_episodios and len(catalogo) >= max_episodios:
            break

    return catalogo


# ==============================================================================
# 6. drive_sync.py
# ==============================================================================
"""
drive_sync.py
=============
Gerencia autenticação resiliente de Service Account (Secrets, Base64, local, URL)
e upload assíncrono para o Google Drive sem necessidade de drive.mount() manual.
"""


import os
import sys
import json
import base64
import zlib
import shutil
from pathlib import Path
from typing import Optional, Dict, Any, Callable

SCOPES = ['https://www.googleapis.com/auth/drive']

def decodificar_base64_ou_json(texto: str) -> Optional[Dict[str, Any]]:
    texto = texto.strip().strip("'").strip('"')
    if texto.startswith("{") and texto.endswith("}"):
        try:
            return json.loads(texto)
        except Exception as _exc:
            _registrar_erro(_exc)
    try:
        raw = base64.b64decode(texto)
        try:
            raw = zlib.decompress(raw)
        except Exception as _exc:
            _registrar_erro(_exc)
        return json.loads(raw.decode("utf-8"))
    except Exception as _exc:
        _registrar_erro(_exc)
    return None

def carregar_credenciais_service_account(
    param_input: Optional[str] = None
) -> Optional[Dict[str, Any]]:
    """
    Carrega credenciais da Service Account testando em ordem de conveniência e segurança:
    1. Google Colab Secrets (Chavinha 'SERVICE_ACCOUNT_JSON' ou 'SERVICE_ACCOUNT_B64')
    2. Entrada direta (JSON bruto, string Base64 ou URL do Google Drive via gdown)
    3. Arquivo local no disco ('service_account.json' ou '/content/service_account.json')
    """
    # 1. Colab Secrets
    try:
        from google.colab import userdata
        val = userdata.get('SERVICE_ACCOUNT_JSON')
        if val:
            return json.loads(val)
        val_b64 = userdata.get('SERVICE_ACCOUNT_B64')
        if val_b64:
            return decodificar_base64_ou_json(val_b64)
    except Exception as _exc:
        _registrar_erro(_exc)

    # 2. Entrada direta via formulário
    if param_input and param_input.strip():
        inp = param_input.strip()
        if inp.startswith("http://") or inp.startswith("https://") or "drive.google.com" in inp:
            try:
                import gdown
                target = Path("./service_account.json")
                gdown.download(inp, str(target), quiet=True, fuzzy=True)
                if target.exists():
                    return json.loads(target.read_text(encoding="utf-8"))
            except Exception as ge:
                print(f"⚠️ Falha ao baixar service_account da URL: {ge}")
        else:
            cred = decodificar_base64_ou_json(inp)
            if cred:
                return cred

    # 3. Arquivo local no disco
    candidatos_locais = [
        Path("service_account.json"),
        Path("/content/service_account.json"),
        Path("C:/darkmahou/service_account.json"),
        Path(os.path.expanduser("~/Downloads/service_account.json"))
    ]
    for c in candidatos_locais:
        if c.exists() and c.is_file():
            try:
                return json.loads(c.read_text(encoding="utf-8"))
            except Exception as _exc:
                _registrar_erro(_exc)

    return None

def autenticar_drive_service(creds_data: Dict[str, Any]):
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    creds = service_account.Credentials.from_service_account_info(creds_data, scopes=SCOPES)
    return build('drive', 'v3', credentials=creds)

def autenticar_drive_oauth(client_id: str, client_secret: str, refresh_token: str):
    """
    Autentica via OAuth 2.0 Pessoal usando um Refresh Token permanente
    (fluxo 'installed app' / Desktop). Diferente da Service Account, usa a
    cota de armazenamento REAL da conta pessoal do usuário e não exige que a
    pasta de destino esteja em um Drive Compartilhado.
    Não abre nenhum popup/tela de login: o refresh token já concede acesso.
    """
    from google.oauth2.credentials import Credentials
    from googleapiclient.discovery import build
    if not refresh_token:
        raise ValueError(
            "Refresh Token OAuth ausente. Configure 'OAUTH_REFRESH_TOKEN' nos "
            "Colab Secrets (🔑) para usar o modo OAuth 2.0 Pessoal."
        )
    creds = Credentials(
        token=None,
        refresh_token=refresh_token,
        token_uri="https://oauth2.googleapis.com/token",
        client_id=client_id,
        client_secret=client_secret,
        scopes=SCOPES,
    )
    return build('drive', 'v3', credentials=creds)

def obter_ou_criar_pasta_drive(service, nome_pasta: str = "Animes", parent_id: Optional[str] = None) -> str:
    # Remove caracteres de controle (\n, \r, \t, NUL, ESC etc.) que sobrevivem a um
    # escape simples de aspas e podem corromper a sintaxe da query da Drive API v3
    # ou poluir a saida do terminal com sequencias ANSI.
    nome_pasta = sanitizar_nome_arquivo(nome_pasta, max_len=100) if "sanitizar_nome_arquivo" in globals() else nome_pasta
    # Escapa barras invertidas e aspas simples para proteger a sintaxe da query da API v3
    nome_escapado = nome_pasta.replace("\\", "\\\\").replace("'", "\\'")
    query = f"name = '{nome_escapado}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
    if parent_id:
        query += f" and '{parent_id}' in parents"
    results = service.files().list(
        q=query,
        fields="files(id, name)",
        supportsAllDrives=True,
        includeItemsFromAllDrives=True
    ).execute()
    items = results.get('files', [])
    if items:
        return items[0]['id']

    file_metadata = {
        'name': nome_pasta,
        'mimeType': 'application/vnd.google-apps.folder'
    }
    if parent_id:
        file_metadata['parents'] = [parent_id]
    folder = service.files().create(
        body=file_metadata,
        fields='id',
        supportsAllDrives=True
    ).execute()
    return folder.get('id')

def verificar_cota_conta_pessoal(service) -> tuple[bool, str]:
    """
    Verifica e formata a cota de armazenamento REAL da conta pessoal autenticada
    via OAuth 2.0 (diferente da Service Account, que tem 0 bytes em drives pessoais).
    """
    try:
        about = service.about().get(fields="user, storageQuota").execute()
        user = about.get("user", {})
        quota = about.get("storageQuota", {})
        email = user.get("emailAddress", "conta pessoal")
        limit = quota.get("limit")
        usage = quota.get("usage")

        def _fmt(n):
            try:
                n = int(n)
            except (TypeError, ValueError):
                return "?"
            for unidade in ["B", "KB", "MB", "GB", "TB"]:
                if n < 1024:
                    return f"{n:.1f}{unidade}" if unidade != "B" else f"{n}{unidade}"
                n /= 1024
            return f"{n:.1f}PB"

        if limit is None:
            return True, f"✅ Conta: {email} — Armazenamento ilimitado/Workspace."
        usado = _fmt(usage) if usage is not None else "?"
        total = _fmt(limit)
        return True, f"✅ Conta: {email} — Cota: {usado} / {total} usados."
    except Exception as e:
        return True, f"ℹ️ Verificação de cota ignorada: {e}"

def verificar_cota_service_account(service, pasta_id: Optional[str] = None) -> tuple[bool, str]:
    """
    Verifica se a Service Account possui cota real de armazenamento ou se a pasta alvo
    está dentro de um Drive Compartilhado (Shared Drive / Team Drive).
    Service Accounts do Google Cloud possuem 0 bytes de cota em drives pessoais (@gmail.com),
    só podendo fazer upload de arquivos (>0B) para Drives Compartilhados.
    """
    try:
        about = service.about().get(fields="storageQuota").execute()
        quota = about.get("storageQuota", {})
        limit = quota.get("limit")
        if limit == "0":
            if pasta_id:
                try:
                    f = service.files().get(
                        fileId=pasta_id,
                        fields="id, name, driveId",
                        supportsAllDrives=True
                    ).execute()
                    if f.get("driveId"):
                        return True, "✅ Pasta em Drive Compartilhado (Shared Drive) detectada. Uploads liberados!"
                except Exception as _exc:
                    _registrar_erro(_exc)
            return False, (
                "⚠️ Limitação do Google: Service Accounts possuem 0 bytes de cota em Drives Pessoais.\n"
                "Para fazer upload:\n"
                "  1) Mova/Crie a pasta dentro de um 'Drive Compartilhado' (Shared Drive) e adicione a Service Account nele;\n"
                "  OU\n"
                "  2) No Colab, utilize o 'Google Drive Mount Tradicional (/content/drive)' para usar a cota do seu próprio e-mail!"
            )
        return True, "✅ Cota de armazenamento disponível."
    except Exception as e:
        return True, f"ℹ️ Verificação de cota ignorada: {e}"

def md5_arquivo(caminho, bloco=8 * 1024 * 1024):
    """MD5 do arquivo, lido em blocos (não carrega o arquivo inteiro na memória)."""
    import hashlib as _hashlib
    h = _hashlib.md5()
    with open(caminho, "rb") as f:
        for pedaco in iter(lambda: f.read(bloco), b""):
            h.update(pedaco)
    return h.hexdigest()


def arquivo_igual_no_drive(service, pasta_id, nome, tamanho, md5):
    """Id de um arquivo já existente na pasta com o mesmo nome, tamanho e MD5. None se não houver."""
    nome_q = nome.replace("\\", "\\\\").replace("'", "\\'")
    try:
        resp = service.files().list(
            q=f"name = '{nome_q}' and '{pasta_id}' in parents and trashed = false",
            fields="files(id,name,size,md5Checksum)",
            supportsAllDrives=True,
            includeItemsFromAllDrives=True,
            pageSize=20,
        ).execute()
    except Exception as exc:
        _registrar_erro(exc, "consulta de duplicata no Drive")
        return None
    for arq in resp.get("files", []):
        if str(arq.get("size")) == str(tamanho) and arq.get("md5Checksum") == md5:
            return arq["id"]
    return None


def verificar_md5_drive(service, file_id, md5, tamanho):
    """Confere MD5 e tamanho do arquivo remoto. Se divergir, remove o remoto e levanta erro."""
    meta = service.files().get(fileId=file_id, fields="md5Checksum,size", supportsAllDrives=True).execute()
    if meta.get("md5Checksum") == md5 and str(meta.get("size")) == str(tamanho):
        return True
    try:
        service.files().delete(fileId=file_id, supportsAllDrives=True).execute()
    except Exception as exc:
        _registrar_erro(exc, "remover arquivo remoto divergente")
    raise RuntimeError("MD5 divergente após o upload: o arquivo remoto foi removido. Tente o envio de novo.")


# ---------------------------------------------------------------------------
# Upload retomável com sessão guardada em disco (v12)
# ---------------------------------------------------------------------------
import threading as _threading_upload

DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
SESSOES_UPLOAD_ARQUIVO = Path("/content/dlp_sessoes_upload.json")  # disco do Colab: sobrevive a reinício do kernel
TAMANHO_BLOCO_UPLOAD = 8 * 1024 * 1024  # múltiplo de 256 KiB, como o protocolo exige
VALIDADE_SESSAO_S = 6 * 24 * 3600       # o Drive encerra a sessão em 1 semana; usa-se 6 dias por margem
_SESSOES_LOCK = _threading_upload.Lock()


class _SessaoInvalida(Exception):
    """O Drive não reconhece mais a sessão (expirou ou foi cancelada): começa outra."""


class _ErroTransitorio(Exception):
    """Falha que vale repetir: 408, 429, 5xx ou queda de conexão."""


def _sessoes_upload_ler() -> dict:
    try:
        return json.loads(SESSOES_UPLOAD_ARQUIVO.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return {}
    except Exception as _exc:
        _registrar_erro(_exc, "sessões de upload ilegíveis; começando do zero")
        return {}


def _sessao_carregar(chave: str):
    import time as _tm
    sessao = _sessoes_upload_ler().get(chave)
    if sessao and _tm.time() - sessao.get("criado_em", 0) > VALIDADE_SESSAO_S:
        return None
    return sessao


def _sessao_atualizar(chave: str, sessao) -> None:
    """Grava a sessão desta chave, ou remove se sessao for None. Escrita atômica e com trava."""
    with _SESSOES_LOCK:
        dados = _sessoes_upload_ler()
        if sessao is None:
            dados.pop(chave, None)
        else:
            dados[chave] = sessao
        _escrever_atomico(SESSOES_UPLOAD_ARQUIVO, json.dumps(dados, ensure_ascii=False, indent=1))


def _classificar_erro(resp, conteudo) -> None:
    trecho = (conteudo or b"")[:200]
    if resp.status in (404, 410):
        raise _SessaoInvalida(f"HTTP {resp.status}")
    if resp.status in (408, 429) or resp.status >= 500:
        raise _ErroTransitorio(f"HTTP {resp.status}: {trecho!r}")
    raise RuntimeError(f"HTTP {resp.status} no upload: {trecho!r}")


def _offset_da_resposta(resp) -> int:
    """Bytes que o Drive já confirmou. O cabeçalho Range diz até onde ele recebeu (bytes=0-N)."""
    faixa = resp.get("range")
    if not faixa:
        return 0
    return int(faixa.split("-")[-1]) + 1


def _pedido(http, uri, method, body, headers):
    """Uma requisição HTTP sem seguir redirecionamentos. O Drive responde 308 ('envio incompleto'),
    que o httplib2 recente trata como redirecionamento: a resposta vem na exceção e é recuperada aqui."""
    try:
        return http.request(uri, method=method, body=body, headers=headers, redirections=0)
    except Exception as exc:
        resposta = getattr(exc, "response", None)
        if resposta is not None and getattr(resposta, "status", None) == 308:
            return resposta, getattr(exc, "content", b"")
        raise


def _nova_sessao(http, nome: str, pasta_id: str, tamanho: int) -> str:
    resp, conteudo = _pedido(http,
        f"{DRIVE_UPLOAD_URL}?uploadType=resumable&supportsAllDrives=true&fields=id",
        method="POST",
        body=json.dumps({"name": nome, "parents": [pasta_id]}).encode("utf-8"),
        headers={
            "Content-Type": "application/json; charset=UTF-8",
            "X-Upload-Content-Type": "application/octet-stream",
            "X-Upload-Content-Length": str(tamanho),
        },
    )
    if resp.status != 200:
        _classificar_erro(resp, conteudo)
    uri = resp.get("location")
    if not uri:
        raise RuntimeError("O Drive não devolveu a URI da sessão de upload.")
    return uri


def _status_sessao(http, uri: str, tamanho: int):
    """Pergunta ao Drive até onde ele recebeu. Devolve ('concluido', arquivo) ou ('incompleto', offset)."""
    resp, conteudo = _pedido(http,
        uri,
        method="PUT",
        body=b"",
        headers={"Content-Length": "0", "Content-Range": f"bytes */{tamanho}"},
    )
    if resp.status in (200, 201):
        return "concluido", json.loads(conteudo)
    if resp.status == 308:
        return "incompleto", _offset_da_resposta(resp)
    _classificar_erro(resp, conteudo)


def _enviar_bloco(http, uri: str, corpo: bytes, inicio: int, fim: int, tamanho: int):
    resp, conteudo = _pedido(http,
        uri,
        method="PUT",
        body=corpo,
        headers={"Content-Length": str(len(corpo)), "Content-Range": f"bytes {inicio}-{fim}/{tamanho}"},
    )
    if resp.status in (200, 201):
        return "concluido", json.loads(conteudo)
    if resp.status == 308:
        return "incompleto", _offset_da_resposta(resp)
    _classificar_erro(resp, conteudo)


def _upload_resumavel_persistente(http, p, pasta_id, nome, md5_local, tamanho, on_chunk=None, max_retries=6):
    """Envia em blocos pelo protocolo retomável do Drive. A sessão fica salva em disco: se o kernel
    reiniciar, a próxima chamada pergunta ao Drive até onde ele recebeu e continua dali."""
    import random as _rnd
    import time as _tm
    chave = f"{pasta_id}|{nome}|{tamanho}|{md5_local}"
    sessao = _sessao_carregar(chave)
    falhas_seguidas = 0
    recomecos = 0
    voltas = 0
    while True:
        voltas += 1
        if voltas > 30:
            raise RuntimeError("O upload não concluiu depois de muitas tentativas; verifique a conexão.")
        try:
            if sessao is None:
                sessao = {"uri": _nova_sessao(http, nome, pasta_id, tamanho), "offset": 0, "criado_em": _tm.time()}
                _sessao_atualizar(chave, sessao)
            estado, valor = _status_sessao(http, sessao["uri"], tamanho)
            while estado == "incompleto" and valor < tamanho:
                inicio = valor
                fim = min(inicio + TAMANHO_BLOCO_UPLOAD, tamanho) - 1
                with open(p, "rb") as f:
                    f.seek(inicio)
                    corpo = f.read(fim - inicio + 1)
                estado, valor_novo = _enviar_bloco(http, sessao["uri"], corpo, inicio, fim, tamanho)
                if estado == "incompleto":
                    if valor_novo <= inicio:
                        raise _ErroTransitorio(f"o Drive não avançou (confirmou até {valor_novo} bytes)")
                    sessao["offset"] = valor_novo
                    _sessao_atualizar(chave, sessao)
                    falhas_seguidas = 0
                    if on_chunk:
                        try:
                            on_chunk(valor_novo / tamanho)
                        except Exception as _exc:
                            _registrar_erro(_exc, "progresso do upload")
                valor = valor_novo
            if estado == "concluido":
                _sessao_atualizar(chave, None)
                if on_chunk:
                    try:
                        on_chunk(1.0)
                    except Exception as _exc:
                        _registrar_erro(_exc, "progresso do upload")
                return valor["id"]
        except _SessaoInvalida as exc:
            _registrar_erro(exc, "sessão de upload expirou; começando outra")
            _sessao_atualizar(chave, None)
            sessao = None
            recomecos += 1
            if recomecos > 1:
                raise RuntimeError("O Drive recusou a sessão de upload duas vezes seguidas.")
        except RuntimeError:
            raise
        except Exception as exc:
            falhas_seguidas += 1
            if falhas_seguidas > max_retries:
                raise
            _registrar_erro(exc, f"upload: nova tentativa {falhas_seguidas}")
            _tm.sleep(min(60, 2 ** falhas_seguidas) + _rnd.uniform(0, 1))


def upload_arquivo_drive(service, caminho_local, pasta_id, nome_destino=None, on_chunk=None, max_retries: int = 6) -> str:
    """Envia um arquivo ao Drive com sessão retomável guardada no disco do Colab (v12).
    Se o kernel reiniciar no meio, a próxima chamada continua do último byte que o Drive confirmou.
    Se o cliente não expuser o HTTP, usa o caminho antigo (_upload_googleapi_legado)."""
    p = Path(caminho_local)
    nome = nome_destino or p.name
    http = getattr(service, "_http", None)
    if http is None:
        return _upload_googleapi_legado(service, caminho_local, pasta_id, nome_destino, on_chunk, max_retries)
    md5_local = md5_arquivo(p)
    tamanho = p.stat().st_size
    existente = arquivo_igual_no_drive(service, pasta_id, nome, tamanho, md5_local)
    if existente:
        _registrar_info(f"já existe no Drive, não reenviado: {nome}", "upload")
        return existente
    file_id = _upload_resumavel_persistente(http, p, pasta_id, nome, md5_local, tamanho, on_chunk, max_retries)
    verificar_md5_drive(service, file_id, md5_local, tamanho)
    return file_id


def _upload_googleapi_legado(
    service,
    caminho_local: str | Path,
    pasta_id: str,
    nome_destino: Optional[str] = None,
    on_chunk: Optional[Callable[[float], None]] = None,
    max_retries: int = 6,
) -> str:
    """
    Upload resumable em chunks para o Drive, com retry/backoff exponencial por
    chunk. Cobre tanto falhas de rede locais (BrokenPipeError, ConnectionError,
    erros de SSL/timeout) quanto erros transitórios da própria API do Drive
    (HTTP 5xx e 429 'rate limit exceeded'), sem reiniciar o upload do zero --
    o protocolo resumable do Drive permite retomar do byte onde parou.
    """
    import time as _time_upload
    import random as _random_upload
    from googleapiclient.http import MediaFileUpload
    try:
        from googleapiclient.errors import HttpError
    except Exception:
        HttpError = Exception  # fallback caso o import específico não exista

    p = Path(caminho_local)
    nome = nome_destino or p.name

    # Dedupe remoto (nome + tamanho + MD5) e verificação MD5 depois do envio
    md5_local = md5_arquivo(p)
    tamanho_local = p.stat().st_size
    existente = arquivo_igual_no_drive(service, pasta_id, nome, tamanho_local, md5_local)
    if existente:
        _registrar_info(f"já existe no Drive, não reenviado: {nome}", "upload")
        return existente

    file_metadata = {
        'name': nome,
        'parents': [pasta_id]
    }
    media = MediaFileUpload(str(p), resumable=True)
    request = service.files().create(
        body=file_metadata,
        media_body=media,
        fields='id',
        supportsAllDrives=True
    )

    response = None
    tentativas_seguidas_com_erro = 0

    while response is None:
        try:
            status, response = request.next_chunk()
            if status and on_chunk:
                on_chunk(status.progress())
            tentativas_seguidas_com_erro = 0  # chunk OK, zera o contador de falhas consecutivas
        except HttpError as he:
            status_code = getattr(getattr(he, "resp", None), "status", None)
            transitorio = status_code in (429, 500, 502, 503, 504)
            tentativas_seguidas_com_erro += 1
            if not transitorio or tentativas_seguidas_com_erro > max_retries:
                raise
            espera = min(60.0, (2 ** tentativas_seguidas_com_erro) + _random_upload.uniform(0, 1))
            print(f"    ⚠️  Upload: erro HTTP {status_code} (transitório) — retomando em {espera:.0f}s "
                  f"(tentativa {tentativas_seguidas_com_erro}/{max_retries})...")
            _time_upload.sleep(espera)
        except (BrokenPipeError, ConnectionError, ConnectionResetError, TimeoutError, OSError) as ce:
            tentativas_seguidas_com_erro += 1
            if tentativas_seguidas_com_erro > max_retries:
                raise
            espera = min(60.0, (2 ** tentativas_seguidas_com_erro) + _random_upload.uniform(0, 1))
            print(f"    ⚠️  Upload: conexão caiu ({type(ce).__name__}: {ce}) — retomando em {espera:.0f}s "
                  f"(tentativa {tentativas_seguidas_com_erro}/{max_retries})...")
            _time_upload.sleep(espera)
        except AttributeError as ae:
            # Caso específico e conhecido: quando a conexão é degradada/cortada no
            # meio da leitura de uma resposta (refresh de token OAuth ou resposta do
            # próprio upload), google-auth/googleapiclient tentam fazer json.loads()
            # no corpo; se ele chegar truncado, o parse falha e a lib usa a string
            # bruta no lugar de um dict, e algum ponto interno da lib chama .get()
            # nela -- gerando "'str' object has no attribute 'get'". Não é um bug
            # deste notebook: é sintoma da mesma degradação de rede que causa os
            # timeouts/erros de SSL tratados acima, só que capturado num ponto
            # diferente do ciclo de vida da requisição. Só tratamos como transitório
            # quando a mensagem bate com esse padrão específico -- um AttributeError
            # genérico de outra origem ainda propaga normalmente, para não mascarar
            # bugs reais de lógica.
            se_e_o_padrao_conhecido = "has no attribute 'get'" in str(ae)
            tentativas_seguidas_com_erro += 1
            if not se_e_o_padrao_conhecido or tentativas_seguidas_com_erro > max_retries:
                raise
            espera = min(60.0, (2 ** tentativas_seguidas_com_erro) + _random_upload.uniform(0, 1))
            print(f"    ⚠️  Upload: resposta malformada por instabilidade de rede ({ae}) — retomando em {espera:.0f}s "
                  f"(tentativa {tentativas_seguidas_com_erro}/{max_retries})...")
            _time_upload.sleep(espera)

    file_id = response.get('id')
    verificar_md5_drive(service, file_id, md5_local, tamanho_local)
    return file_id


# ==============================================================================
# 7. manifest.py
# ==============================================================================
"""
manifest.py (P2P Ingestion Toolkit v5)
=======================================
Registro atômico de downloads concluídos/falhos em manifest.json.
Evita baixar novamente episódios já presentes.
"""


import os
import json
import time
import uuid
import threading
from pathlib import Path
from typing import Optional, Dict, Any

class Manifest:
    """
    Versão thread-safe: todo acesso a self._data e a escrita em disco passam por
    um único RLock, eliminando lost updates e a corrida no replace() do arquivo .tmp
    quando múltiplos workers do ThreadPoolExecutor chamam mark_done/mark_failed.
    """
    def __init__(self, path: str | Path):
        self.path = Path(path)
        self._data: Dict[str, Any] = {}
        self._lock = threading.RLock()
        if self.path.exists():
            try:
                self._data = json.loads(self.path.read_text(encoding="utf-8"))
            except Exception:
                self._data = {}

    def is_done(self, key: str) -> bool:
        with self._lock:
            entry = self._data.get(key)
            return bool(entry and entry.get("status") == "done")

    def mark_done(self, key: str, final_path: str, extra: Optional[dict] = None) -> None:
        with self._lock:
            self._data[key] = {
                "status": "done",
                "final_path": final_path,
                "completed_at": time.time(),
                **(extra or {}),
            }
            self._save_locked()

    def mark_failed(self, key: str, error: str) -> None:
        with self._lock:
            self._data[key] = {
                "status": "failed",
                "error": error,
                "attempted_at": time.time(),
            }
            self._save_locked()

    def _save_locked(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(
            f"{self.path.suffix}.{os.getpid()}.{threading.get_ident()}.{uuid.uuid4().hex[:6]}.tmp"
        )
        tmp.write_text(json.dumps(self._data, indent=2, ensure_ascii=False), encoding="utf-8")
        tmp.replace(self.path)

    def _save(self) -> None:
        with self._lock:
            self._save_locked()


# ==============================================================================
# 8. colab_ui.py (Cards de progresso e Seletor Interativo com Checklist)
# ==============================================================================
"""
colab_ui.py (P2P Ingestion Toolkit v6 - Com Seletor Interativo de Fansub e Episódios)
======================================================================================
UI reativa via ipywidgets para Google Colab e Jupyter.
Cards com barra de progresso individual, botão real de Cancelar,
painel interativo de seleção de episódios por checklist e abas de Fansub.
"""


from typing import Any, Callable, Dict, List, Optional
import ipywidgets as widgets
from IPython.display import display

class JobWidget:
    def __init__(self, label: str, total_items: int, index: int):
        self.title = widgets.HTML(value=f"<b>{label}</b>")
        self.subtitle = widgets.HTML(
            value=f"<span style='opacity:0.6;font-size:12px'>Arquivo {index}/{total_items}</span>"
        )
        self.progress = widgets.IntProgress(
            value=0, min=0, max=100, bar_style="info", layout=widgets.Layout(width="100%")
        )
        self.status_line = widgets.HTML(value="<span style='font-size:12px;opacity:0.8'>⏳ Iniciando conexões...</span>")
        self.cancel_button = widgets.Button(
            description="Cancelar", button_style="danger", layout=widgets.Layout(width="100px")
        )
        self.container = widgets.VBox(
            [self.subtitle, self.title, self.progress, self.status_line, self.cancel_button],
            layout=widgets.Layout(
                border="1px solid #444", padding="8px", margin="4px 0", border_radius="8px"
            ),
        )
        self._cancel_callbacks: list[Callable[[], None]] = []
        self.cancel_button.on_click(self._handle_click)

    def on_cancel(self, callback: Callable[[], None]) -> None:
        self._cancel_callbacks.append(callback)

    def _handle_click(self, _btn) -> None:
        self.cancel_button.disabled = True
        self.cancel_button.description = "Cancelando..."
        for cb in self._cancel_callbacks:
            cb()

    def show_progress(self, percent: float | None, speed: str | None, peers: int | None, eta: str | None) -> None:
        self.progress.bar_style = "info"
        self.progress.value = int(percent or 0)
        p_str = f"{int(percent)}%" if percent is not None else "0%"

        if peers == 0 or (speed in ("0B/s", "0B", "--", None) and (percent or 0) == 0):
            status_color = "#ffaa00"
            peer_icon = "⚠️ 0 peers"
        else:
            status_color = "#4caf50"
            peer_icon = f"👥 {peers or 0} peers"

        self.status_line.value = (
            f"<span style='font-size:12px'>"
            f"<b>{p_str}</b> &nbsp;|&nbsp; ⚡ <span style='color:{status_color}'>{speed or '--'}</span> &nbsp;|&nbsp; "
            f"{peer_icon} &nbsp;|&nbsp; ⏳ {eta or '--'}</span>"
        )

    def show_upload(self, progress: float) -> None:
        self.progress.bar_style = "warning"
        self.progress.value = int(progress * 100)
        self.status_line.value = f"<span style='font-size:12px;color:#f5c518'>☁️ Enviando ao Google Drive: <b>{int(progress * 100)}%</b>...</span>"

    def show_done(self, success: bool, message: str = "") -> None:
        self.cancel_button.disabled = True
        self.progress.bar_style = "success" if success else "danger"
        if success:
            self.progress.value = 100
        icon = "✅" if success else "❌"
        self.status_line.value = f"<span style='font-size:12px'>{icon} {message}</span>"

from dataclasses import dataclass

@dataclass
class JobState:
    slot: int
    label: str
    total: int
    percent: float = 0.0
    speed: str = "--"
    peers: int = 0
    eta: str = "--"
    status_type: str = "pending"  # pending, downloading, uploading, done, error, cancelled
    message: str = "⏳ Aguardando início..."

class JobProxy:
    def __init__(self, slot: int, label: str, total: int, board: JobBoard):
        self.slot = slot
        self.label = label
        self.total = total
        self.board = board
        self._cancel_callbacks: List[Callable[[], None]] = []

    def on_cancel(self, callback: Callable[[], None]) -> None:
        self._cancel_callbacks.append(callback)

    def cancel(self) -> None:
        for cb in self._cancel_callbacks:
            try:
                cb()
            except Exception as _exc:
                _registrar_erro(_exc)

class JobBoard:
    """
    JobBoard com renderização reativa via HTML puro sobre um widget HTML pré-renderizado no DOM.
    Elimina 100% a perda de comm_open no Google Colab e não requer download de extensões CDN externas.
    """
    def __init__(
        self,
        container: Optional[widgets.Box] = None,
        html_widget: Optional[widgets.HTML] = None
    ):
        self._jobs: Dict[int, JobState] = {}
        self._proxies: Dict[int, JobProxy] = {}
        self._total = 0
        self._done = 0
        self._failed = 0
        self._container = container
        self._html_widget = html_widget
        self._displayed = False

        if self._html_widget is None and self._container is not None:
            for ch in self._container.children:
                if isinstance(ch, widgets.HTML):
                    self._html_widget = ch
                    break
            if self._html_widget is None:
                self._html_widget = widgets.HTML(value="")
                self._container.children = (self._html_widget,)
            self._displayed = True

        if self._html_widget is None:
            self._html_widget = widgets.HTML(value="")
            self._displayed = False
        else:
            self._displayed = True

    def display(self) -> None:
        if self._html_widget is not None and not self._displayed:
            display(self._html_widget)
            self._displayed = True

    def _ipython_display_(self) -> None:
        self.display()

    def cancel_all(self) -> None:
        for proxy in list(self._proxies.values()):
            proxy.cancel()

    def get_or_create(self, slot: int, label: str, total: int) -> JobProxy:
        self._total = total
        if not getattr(self, "_displayed", False) and self._container is None and self._html_widget is not None:
            try:
                display(self._html_widget)
                self._displayed = True
            except Exception as _exc:
                _registrar_erro(_exc)
        if slot not in self._jobs:
            self._jobs[slot] = JobState(slot=slot, label=label, total=total)
            self._proxies[slot] = JobProxy(slot=slot, label=label, total=total, board=self)
            self.update_html()
        return self._proxies[slot]

    def handle_event(self, slot: int, kind: str, payload) -> None:
        job = self._jobs.get(slot)
        if job is None:
            return

        if kind == "progress":
            job.percent = float(payload.percent or 0)
            job.speed = payload.speed or "--"
            job.peers = int(payload.peers or 0)
            job.eta = payload.eta or "--"
            job.status_type = "downloading"
            if payload.status_warning:
                job.message = payload.status_warning
        elif kind == "upload":
            job.percent = float(payload * 100)
            job.status_type = "uploading"
            job.message = f"☁️ Enviando ao Google Drive: <b>{int(payload * 100)}%</b>"
        elif kind == "done":
            if payload.success:
                self._done += 1
                job.percent = 100.0
                job.status_type = "done"
                name = payload.final_path.name if payload.final_path else "Concluído"
                job.message = f"✅ Concluído: {name} ({payload.elapsed_seconds:.1f}s)"
                if getattr(payload, "warning", None):
                    job.message += f"<br><span style='color:#ffaa00'>⚠️ {payload.warning}</span>"
            elif payload.cancelled:
                self._failed += 1
                job.status_type = "cancelled"
                job.message = "⏹️ Cancelado pelo usuário"
            else:
                self._failed += 1
                job.status_type = "error"
                job.message = f"❌ {payload.error or 'Falha no download'}"

        self.update_html()

    def update_html(self) -> None:
        if self._html_widget is not None:
            self._html_widget.value = self.to_html()

    def to_html(self) -> str:
        restante = max(0, self._total - self._done - self._failed)
        status_geral = "⚡ Baixando arquivos..." if restante > 0 else "🎉 Lote concluído!"

        cards_html = []
        for slot in sorted(self._jobs):
            job = self._jobs[slot]
            pct = min(100, max(0, int(job.percent)))
            if job.status_type == "done":
                card_border = "#2e7d32"
                bar_color = "#4caf50"
                badge = "<span style='color:#4caf50;font-weight:bold'>✅ Concluído</span>"
                status_line = f"<span style='color:#4caf50;font-size:12px'>{job.message}</span>"
            elif job.status_type == "cancelled":
                card_border = "#c62828"
                bar_color = "#f44336"
                badge = "<span style='color:#ff9800;font-weight:bold'>⏹️ Cancelado</span>"
                status_line = f"<span style='color:#ff9800;font-size:12px'>{job.message}</span>"
            elif job.status_type == "error":
                card_border = "#c62828"
                bar_color = "#f44336"
                badge = "<span style='color:#f44336;font-weight:bold'>❌ Falha</span>"
                status_line = f"<span style='color:#f44336;font-size:12px'>{job.message}</span>"
            elif job.status_type == "uploading":
                card_border = "#f57f17"
                bar_color = "#ffb300"
                badge = "<span style='color:#ffb300;font-weight:bold'>☁️ Drive Upload</span>"
                status_line = f"<span style='color:#fdd835;font-size:12px'>{job.message}</span>"
            elif job.status_type == "downloading":
                card_border = "#1565c0"
                bar_color = "#2196f3"
                badge = "<span style='color:#64b5f6;font-weight:bold'>⚡ Baixando</span>"
                speed_color = "#4caf50" if job.speed not in ("0B/s", "0B", "--") else "#ffaa00"
                peer_icon = f"👥 {job.peers} peers" if job.peers > 0 else "⚠️ 0 peers"
                status_line = (
                    f"<span style='font-size:12px'>"
                    f"<b>{pct}%</b> &nbsp;|&nbsp; ⚡ <span style='color:{speed_color}'>{job.speed}</span> &nbsp;|&nbsp; "
                    f"{peer_icon} &nbsp;|&nbsp; ⏳ {job.eta}</span>"
                )
            else:  # pending
                card_border = "#444"
                bar_color = "#555"
                badge = "<span style='color:#888'>⏳ Na fila</span>"
                status_line = f"<span style='color:#888;font-size:12px'>{job.message}</span>"

            card = f"""
            <div style="border:1px solid {card_border};padding:8px 12px;margin:6px 0;border-radius:8px;background:#212121">
                <div style="display:flex;justify-content:space-between;font-size:11px;opacity:0.7;margin-bottom:3px">
                    <span>Arquivo {job.slot+1}/{job.total}</span>
                    <span>{badge}</span>
                </div>
                <div style="font-size:13px;font-weight:bold;margin-bottom:6px;color:#fff">{job.label}</div>
                <div style="background:#333;border-radius:4px;height:8px;width:100%;overflow:hidden;margin:4px 0">
                    <div style="background:{bar_color};height:100%;width:{pct}%;transition:width 0.2s ease"></div>
                </div>
                <div style="margin-top:4px">{status_line}</div>
            </div>"""
            cards_html.append(card)

        if not cards_html:
            cards_body = "<div style='color:#888;font-size:12px;padding:8px'>⏳ Inicializando fila de downloads...</div>"
        else:
            cards_body = "".join(cards_html)

        return f"""
        <div style="background:#181818;border:1px solid #2e7d32;padding:12px;border-radius:10px;margin:10px 0;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif">
            <div style="font-size:14px;padding-bottom:8px;border-bottom:1px solid #333;margin-bottom:8px">
                <b>{status_geral}</b> &nbsp;|&nbsp;
                <b>Lote:</b> {self._total} arquivo(s) &nbsp;|&nbsp;
                <span style="color:#4caf50">✅ {self._done} OK</span> &nbsp;|&nbsp;
                <span style="color:#f44336">❌ {self._failed} falha(s)</span> &nbsp;|&nbsp;
                <span>⏳ {restante} restante(s)</span>
            </div>
            {cards_body}
        </div>"""

class InteractiveEpisodeSelector:
    """
    Painel Interativo de Seleção:
    - Agrupamento por Fansub / Release Group
    - Checklist com checkboxes individuais para cada episódio
    - Botões rápidos: Marcar Todos / Desmarcar Todos / Inverter
    - Botão de execução direta com indicador de progresso
    """
    def __init__(
        self,
        grupos_catalogo: Dict[str, List[Any]],
        on_start_download: Optional[Callable[[List[Any]], None]] = None,
        anime_nome: str = "",
        sugestoes: Optional[List[Dict[str, Any]]] = None
    ):
        self.grupos = grupos_catalogo
        self.on_start_download = on_start_download
        self.anime_nome = anime_nome
        self.sugestoes = sugestoes or []
        self.group_names = list(grupos_catalogo.keys())

        self.group_items: Dict[str, List[tuple[widgets.Checkbox, Any]]] = {}
        for g_name, options in self.grupos.items():
            items = []
            for opt in options:
                ep = opt.metadata.get("episodio", "?")
                seeds = opt.metadata.get("seeders", "--")
                size = opt.metadata.get("tamanho", "")
                qual = opt.quality or "HD"
                seeds_str = f"👥 {seeds} seeds" if seeds != "--" else "☁️ Direto"
                size_str = f"📦 {size}" if size else ""

                is_batch = opt.metadata.get("is_batch") or opt.metadata.get("episodio") == "Batch"
                if is_batch:
                    batch_info = opt.metadata.get("batch_desc", "Lote Completo")
                    desc = f"📦 [BATCH COMPLETO] {opt.filename_hint or opt.label} · {batch_info}  ({size_str} | {seeds_str} | {qual})"
                else:
                    origem_mix = opt.metadata.get("origem_mix")
                    origem_badge = f" [{origem_mix}]" if origem_mix else ""
                    ep_label = f"Ep. {_formatar_episodio(ep)}" if isinstance(ep, (int, float)) else f"Ep. {ep}"
                    desc = f"{ep_label} · {opt.filename_hint or opt.label}{origem_badge}  ({size_str} | {seeds_str} | {qual})"

                cb = widgets.Checkbox(
                    value=True,
                    description=desc,
                    layout=widgets.Layout(width="100%", margin="2px 0")
                )
                items.append((cb, opt))
            self.group_items[g_name] = items

        self.group_dropdown = widgets.Dropdown(
            options=self.group_names,
            value=self.group_names[0] if self.group_names else None,
            description="<b>Fansub / Grupo:</b>",
            layout=widgets.Layout(width="520px")
        )
        self.group_dropdown.observe(self._on_group_change, names="value")

        self.btn_select_all = widgets.Button(description="Marcar Todos", icon="check-square", layout=widgets.Layout(width="140px"))
        self.btn_deselect_all = widgets.Button(description="Desmarcar Todos", icon="square-o", layout=widgets.Layout(width="140px"))
        self.btn_invert = widgets.Button(description="Inverter", icon="random", layout=widgets.Layout(width="120px"))

        self.btn_select_all.on_click(lambda _: self._set_all_selected(True))
        self.btn_deselect_all.on_click(lambda _: self._set_all_selected(False))
        self.btn_invert.on_click(lambda _: self._invert_selection())

        self.checklist_box = widgets.VBox([], layout=widgets.Layout(
            border="1px solid #555",
            padding="10px",
            border_radius="8px",
            max_height="350px",
            overflow="auto"
        ))

        self.batch_info_card = widgets.HTML(value="")
        self.summary_label = widgets.HTML(value="")

        self.btn_download = widgets.Button(
            description="🚀 Iniciar Download dos Selecionados",
            button_style="success",
            icon="download",
            layout=widgets.Layout(width="380px", height="45px", margin="10px 0")
        )
        if self.on_start_download:
            self.btn_download.on_click(self._trigger_download)

        self.btn_cancel_all = widgets.Button(
            description="⏹️ Cancelar Download",
            button_style="danger",
            icon="stop",
            layout=widgets.Layout(width="200px", height="45px", margin="10px 4px", display="none")
        )
        self.btn_cancel_all.on_click(lambda _: self.job_board.cancel_all() if getattr(self, "job_board", None) else None)

        button_bar = widgets.HBox([self.btn_download, self.btn_cancel_all])

        self.status_html = widgets.HTML(value="", layout=widgets.Layout(width="100%"))
        self.download_box = widgets.VBox([self.status_html], layout=widgets.Layout(margin="10px 0", width="100%"))
        self.download_output = widgets.Output()

        toolbar = widgets.HBox([self.btn_select_all, self.btn_deselect_all, self.btn_invert])
        header_text = f"<h3>📋 Seleção de Episódios: <i>{self.anime_nome or 'Anime'}</i></h3>"

        # Sugestões inteligentes do Jikan/Kitsu
        sug_elements = []
        if self.sugestoes:
            sug_html = "<div style='font-size:12px;margin:4px 0;opacity:0.9'><b>💡 Sugestões MyAnimeList / Kitsu:</b> "
            badges = []
            for s in self.sugestoes[:3]:
                badges.append(f"<span style='background:#2a3b4c;color:#64b5f6;padding:3px 8px;border-radius:10px;margin-right:6px;font-size:11px'>{s['label']}</span>")
            sug_html += "".join(badges) + "</div>"
            sug_elements.append(widgets.HTML(sug_html))

        layout_elements = [
            widgets.HTML(header_text),
            *sug_elements,
            self.group_dropdown,
            self.batch_info_card,
            toolbar,
            self.checklist_box,
            self.summary_label,
            button_bar,
            self.download_box,
            self.download_output
        ]

        self.widget = widgets.VBox(layout_elements, layout=widgets.Layout(padding="12px", border="1px solid #444", border_radius="10px"))

        self._update_checklist_view()

    def _on_group_change(self, _change):
        self._update_checklist_view()

    def _update_checklist_view(self):
        current_g = self.group_dropdown.value
        items = self.group_items.get(current_g, [])
        self.checklist_box.children = tuple(cb for cb, _ in items)
        for cb, _ in items:
            cb.observe(lambda _: self._update_summary(), names="value")

        # Detectar se o grupo selecionado é um batch para exibir o cartão informativo
        batch_details = []
        for _, opt in items:
            if opt.metadata.get("is_batch") or opt.metadata.get("episodio") == "Batch":
                b_desc = opt.metadata.get("batch_desc") or "Pacote completo"
                batch_details.append(b_desc)
        if batch_details:
            self.batch_info_card.value = (
                f"<div style='background:#1b2838;padding:8px 12px;border-radius:6px;margin:6px 0;border-left:4px solid #00bcd4;font-size:12px'>"
                f"ℹ️ <b>Informações do Lote Selecionado:</b> {', '.join(set(batch_details))}. "
                f"<br><span style='color:#aaa'>O motor baixará todos os episódios do lote e organizará individualmente na subpasta do anime no Drive.</span></div>"
            )
        else:
            self.batch_info_card.value = ""

        self._update_summary()

    def _set_all_selected(self, val: bool):
        current_g = self.group_dropdown.value
        for cb, _ in self.group_items.get(current_g, []):
            cb.value = val
        self._update_summary()

    def _invert_selection(self):
        current_g = self.group_dropdown.value
        for cb, _ in self.group_items.get(current_g, []):
            cb.value = not cb.value
        self._update_summary()

    def _update_summary(self):
        selected = self.get_selected_options()
        self.summary_label.value = (
            f"<div style='font-size:13px;padding:4px 0'>"
            f"<span style='color:#4caf50'>✅ <b>{len(selected)}</b> episódio(s) marcado(s) para download.</span>"
            f"</div>"
        )
        self.btn_download.description = f"🚀 Iniciar Download ({len(selected)} episódios)"

    def get_selected_options(self) -> List[Any]:
        current_g = self.group_dropdown.value
        items = self.group_items.get(current_g, [])
        return [opt for cb, opt in items if cb.value]

    def _trigger_download(self, _btn):
        selected = self.get_selected_options()
        if not selected:
            self.summary_label.value = "<span style='color:#f44336'>⚠️ Nenhum episódio selecionado! Marque ao menos um na lista.</span>"
            return
        self.btn_download.disabled = True
        self.btn_download.description = "⏳ Baixando lote selecionado..."
        self.btn_download.button_style = "warning"
        self.btn_cancel_all.layout.display = "inline-flex"

        # Conecta o JobBoard diretamente ao widget status_html pré-montado no DOM do Colab
        self.job_board = JobBoard(html_widget=self.status_html, container=self.download_box)
        self.status_html.value = (
            "<div style='background:#181818;border:1px solid #2e7d32;padding:12px;border-radius:10px;margin:10px 0;"
            "font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;color:#aaa;font-size:13px'>"
            "🚀 <b>Iniciando download...</b> Conectando com aria2c e alocando slots...</div>"
        )

        import threading
        def worker_thread():
            try:
                with self.download_output:
                    if self.on_start_download:
                        import inspect
                        sig = inspect.signature(self.on_start_download)
                        if len(sig.parameters) >= 2:
                            self.on_start_download(selected, self.job_board)
                        else:
                            self.on_start_download(selected)
            except Exception as e:
                import traceback
                traceback.print_exc()
            finally:
                self.btn_download.disabled = False
                self.btn_download.description = f"🚀 Iniciar Download ({len(selected)} episódios)"
                self.btn_download.button_style = "success"
                self.btn_cancel_all.layout.display = "none"

        threading.Thread(target=worker_thread, daemon=True).start()

    def display(self):
        display(self.widget)


# ==============================================================================
# 9. concurrent_runner.py (com suporte a fallback automático)
# ==============================================================================
"""
concurrent_runner.py (P2P Ingestion Toolkit v6)
===============================================
Orquestrador concorrente via ThreadPoolExecutor + queue thread-safe.
Executa download via aria2c com suporte a fallback automático de fonte
e, opcionalmente, upload via Service Account API.
"""


import queue
import shutil
import threading
from concurrent.futures import ThreadPoolExecutor
from typing import Any, Callable, List, Optional



def _pos_processar_upload(
    result: "DownloadResult", option, dest_root: Path,
    drive_service, drive_folder_id, upload_lock: threading.Lock,
    on_chunk: Optional[Callable[[float], None]] = None
) -> "DownloadResult":
    """
    Logica de upload/organizacao pos-download, compartilhada entre run_concurrent
    (motor subprocess-per-download) e run_batch_rpc (motor RPC): envia o
    arquivo/pasta baixado para o Google Drive via Service Account, ou apenas
    reorganiza localmente quando nao ha Service Account configurada. Extraida
    para evitar duplicar esta logica entre os dois orquestradores.
    """
    if not (result.success and result.final_path):
        return result

    if drive_service and drive_folder_id:
        uploaded_ok = 0
        upload_total = 0
        try:
            with upload_lock:
                if result.final_path.is_dir():
                    vfiles = [
                        p for p in result.final_path.rglob("*")
                        if p.is_file() and not p.name.endswith(".aria2") and not p.name.endswith(".torrent") and p.stat().st_size > 1024 * 1024
                    ]
                    upload_total = len(vfiles)
                    total_files = max(1, len(vfiles))
                    for idx, vf in enumerate(vfiles):
                        def on_file_chunk(prg, current_idx=idx, tot=total_files):
                            if on_chunk:
                                on_chunk((current_idx + prg) / tot)
                        upload_arquivo_drive(drive_service, vf, pasta_id=drive_folder_id, on_chunk=on_file_chunk)
                        vf.unlink(missing_ok=True)
                        uploaded_ok += 1
                    shutil.rmtree(result.final_path, ignore_errors=True)
                else:
                    # BUG CORRIGIDO: o caso de arquivo unico (o mais comum -- um
                    # episodio = um .mkv) nunca apagava o arquivo local apos o
                    # upload, diferente do caso de pasta/batch acima (que ja fazia
                    # vf.unlink() + rmtree()). Isso fazia o disco local da VM
                    # acumular TODOS os episodios ja enviados ao Drive sem nunca
                    # liberar espaco, o que esgota rapido o espaco livre real do
                    # Colab (tipicamente uns 40-60GB uteis) ao baixar uma franquia
                    # inteira de varios titulos grandes de uma vez.
                    upload_arquivo_drive(drive_service, result.final_path, pasta_id=drive_folder_id, on_chunk=on_chunk)
                    result.final_path.unlink(missing_ok=True)
        except Exception as ue:
            msg = str(ue)
            if "storageQuotaExceeded" in msg or "do not have storage quota" in msg:
                msg = "Cota excedida (Service Accounts têm 0B de cota em Drive pessoal; use Drive Compartilhado ou Drive Mount)"
            if upload_total > 0 and uploaded_ok > 0:
                msg = f"{uploaded_ok}/{upload_total} arquivos do lote enviados antes da falha: {msg}"
            return DownloadResult(option, False, error=f"Erro upload Drive: {msg}", warning=result.warning)
    elif result.final_path is not None and result.final_path.is_dir() and result.final_path != dest_root:
        try:
            for vf in list(result.final_path.glob("*")):
                if vf.is_file() and not vf.name.endswith(".aria2") and not vf.name.endswith(".torrent"):
                    dest_f = dest_root / vf.name
                    shutil.move(str(vf), str(dest_f))
            shutil.rmtree(result.final_path, ignore_errors=True)
        except Exception as _exc:
            _registrar_erro(_exc)

    return result


def run_concurrent(
    engine,
    options: List[Any],
    dest_root,
    board,
    max_workers: int = 3,
    max_retries: int = 1,
    drive_service: Optional[Any] = None,
    drive_folder_id: Optional[str] = None,
    fallback_resolver: Optional[Callable[[Any], Optional[Any]]] = None,
    disk_gate: Optional["DiskSpaceGate"] = None,
) -> list:
    events_q: queue.Queue[tuple[int, str, Any]] = queue.Queue()
    cancel_events = {i: threading.Event() for i in range(len(options))}
    upload_lock = threading.Lock()
    if disk_gate is None and "DiskSpaceGate" in globals():
        disk_gate = DiskSpaceGate(dest_root)

    def worker(slot: int, option) -> Any:
        """Executa o ciclo completo de download (+fallback +upload) de uma opcao.
        BLINDAGEM: qualquer excecao inesperada e' capturada aqui e convertida em
        um DownloadResult de falha, para nunca escapar via Future.result() e
        derrubar o run_concurrent inteiro (o que deixaria o restante do lote
        sem relatorio no manifest, mesmo apos sucesso)."""
        bytes_reservados = 0
        try:
            def on_progress(telem):
                events_q.put((slot, "progress", telem))

            if disk_gate is not None:
                bytes_estimados = estimar_bytes_de_tamanho_str(option.metadata.get("tamanho")) if "estimar_bytes_de_tamanho_str" in globals() else None
                bytes_reservados = disk_gate.reservar(bytes_estimados, label=option.label)

            result = engine.download_with_retry(
                option,
                dest_dir=dest_root,
                max_retries=max_retries,
                cancel_event=cancel_events[slot],
                on_progress=on_progress,
            )

            # Fallback automatico se a fonte original falhou por timeout / 0 seeds
            if not result.success and not result.cancelled and fallback_resolver:
                try:
                    alt_option = fallback_resolver(option)
                except Exception as fe:
                    alt_option = None
                    logger.warning(f"fallback_resolver falhou para slot {slot}: {fe}")
                if alt_option:
                    events_q.put((slot, "progress", Telemetry(
                        raw_line="", percent=0.0, speed="--", peers=0,
                        eta="Tentando Nyaa.si (PT-BR)..."
                    )))
                    result = engine.download_with_retry(
                        alt_option,
                        dest_dir=dest_root,
                        max_retries=1,
                        cancel_event=cancel_events[slot],
                        on_progress=on_progress,
                    )

            # Se download foi bem-sucedido, organiza localmente ou envia ao Drive
            result = _pos_processar_upload(
                result, option, dest_root, drive_service, drive_folder_id,
                upload_lock, on_chunk=lambda p: events_q.put((slot, "upload", p))
            )

        except Exception as e:
            logger.exception(f"Falha inesperada no worker do slot {slot}")
            result = DownloadResult(option, False, error=f"Erro interno inesperado: {e}")
        finally:
            # SEMPRE libera a reserva de espaco, independente de sucesso, falha
            # ou excecao -- senao o contador interno do disk_gate vaza e os
            # proximos itens ficam esperando por espaco que nunca vai "aparecer".
            if disk_gate is not None and bytes_reservados:
                disk_gate.liberar(bytes_reservados)

        events_q.put((slot, "done", result))
        return result

    for slot, option in enumerate(options):
        widget = board.get_or_create(slot, option.label, total=len(options))
        widget.on_cancel(lambda ev=cancel_events[slot]: ev.set())

    results: list = [None] * len(options)
    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        futures = {pool.submit(worker, slot, opt): slot for slot, opt in enumerate(options)}
        pending = set(futures.keys())
        while pending:
            try:
                slot, kind, payload = events_q.get(timeout=0.3)
                board.handle_event(slot, kind, payload)
            except queue.Empty:
                pass
            done_now = {f for f in pending if f.done()}
            for f in done_now:
                slot = futures[f]
                try:
                    results[slot] = f.result()
                except Exception as e:
                    # Defesa em profundidade: mesmo que a blindagem interna do worker()
                    # falhe por algum motivo, uma excecao aqui nunca deve interromper o
                    # processamento do restante do lote nem impedir o relatorio final.
                    logger.exception(f"Worker do slot {slot} lancou excecao nao tratada")
                    results[slot] = DownloadResult(
                        options[slot], False, error=f"Erro fatal na thread: {e}"
                    )
                    board.handle_event(slot, "done", results[slot])
            pending -= done_now

    while not events_q.empty():
        slot, kind, payload = events_q.get()
        board.handle_event(slot, kind, payload)

    return results


class JobControlPanel:
    """
    Painel de controle com botoes REAIS (ipywidgets.Button) para pausar,
    retomar e cancelar/excluir cada download individualmente. So' faz sentido
    junto do motor RPC (Aria2RPCEngine), onde cada job tem um GID controlavel
    de forma totalmente independente dos demais.

    Design deliberado: mantido como um widget SEPARADO do card de status em
    HTML puro do JobBoard. Botoes ipywidgets dependem do canal de comunicacao
    (comm) do Jupyter/Colab, que pode degradar em sessoes muito longas; ja' o
    HTML puro do JobBoard nao depende disso. Mantendo os dois widgets
    separados, uma eventual falha do comm afeta APENAS os botoes (perde-se o
    controle manual, mas o texto de progresso continua atualizando normalmente)
    em vez de derrubar a visualizacao inteira.
    """

    def __init__(self):
        self._rows: Dict[int, Any] = {}
        self._buttons: Dict[int, Dict[str, Any]] = {}
        self._container = widgets.VBox([])
        self._displayed = False

    def display(self) -> None:
        if not self._displayed:
            display(self._container)
            self._displayed = True

    def _ipython_display_(self) -> None:
        self.display()

    def add_job(self, slot: int, label: str, on_pause: Callable[[], None],
                on_resume: Callable[[], None], on_cancel: Callable[[], None]) -> None:
        label_curto = (label[:42] + "…") if len(label) > 42 else label
        label_widget = widgets.HTML(
            value=f"<span style='font-size:12px'>{label_curto}</span>",
            layout=widgets.Layout(width="50%")
        )
        btn_pause = widgets.Button(description="⏸", tooltip="Pausar este download", layout=widgets.Layout(width="38px"))
        btn_resume = widgets.Button(description="▶", tooltip="Retomar este download", layout=widgets.Layout(width="38px"))
        btn_cancel = widgets.Button(description="🗑", tooltip="Cancelar e excluir este download",
                                     layout=widgets.Layout(width="38px"), button_style="danger")

        def _on_pause_click(_b):
            on_pause()
            btn_pause.disabled = True
            btn_resume.disabled = False

        def _on_resume_click(_b):
            on_resume()
            btn_pause.disabled = False
            btn_resume.disabled = True

        # Confirmacao de duplo-toque para excluir -- evita apagar por engano no celular,
        # onde um toque acidental na tela e' bem mais provavel que num mouse de desktop.
        confirm_state = {"armado": False}

        def _on_cancel_click(_b):
            if not confirm_state["armado"]:
                confirm_state["armado"] = True
                btn_cancel.description = "Confirma?"
                btn_cancel.tooltip = "Toque novamente para confirmar o cancelamento"
            else:
                on_cancel()
                self.mark_finished(slot)

        btn_pause.on_click(_on_pause_click)
        btn_resume.on_click(_on_resume_click)
        btn_cancel.on_click(_on_cancel_click)
        btn_resume.disabled = True

        row = widgets.HBox([label_widget, btn_pause, btn_resume, btn_cancel])
        self._rows[slot] = row
        self._buttons[slot] = {"pause": btn_pause, "resume": btn_resume, "cancel": btn_cancel}
        self._container.children = tuple(self._rows.values())

    def mark_finished(self, slot: int) -> None:
        """Desabilita os botoes de um job que ja terminou (sucesso, falha ou cancelado),
        deixando claro na interface que nao ha mais nada a controlar naquele item."""
        btns = self._buttons.get(slot)
        if btns:
            for b in btns.values():
                b.disabled = True


def run_batch_rpc(
    engine: "Aria2RPCEngine",
    options: List[Any],
    dest_root,
    board,
    control_panel: Optional["JobControlPanel"] = None,
    poll_interval: float = 0.5,
    drive_service: Optional[Any] = None,
    drive_folder_id: Optional[str] = None,
    disk_gate: Optional["DiskSpaceGate"] = None,
) -> list:
    """
    Orquestra um lote inteiro de downloads via Aria2RPCEngine: sobe UM daemon
    para todo o lote, registra cada opcao como um download (GID), e faz o
    polling continuo ate' todos terminarem -- atualizando o JobBoard (texto de
    progresso) e, se fornecido, o JobControlPanel (botoes reais de
    pausar/retomar/cancelar por item).

    Ao contrario de run_concurrent (um subprocess.Popen por download, N threads
    Python gerenciando cada um), aqui a concorrencia e' controlada pelo proprio
    daemon aria2c (--max-concurrent-downloads) e um UNICO loop de polling cobre
    o lote inteiro -- e' esse modelo que permite pausar/retomar/cancelar cada
    item de forma independente, e retomar a fila apos uma queda total do
    ambiente (ver Aria2RPCEngine.start_daemon / stop_daemon).

    IMPORTANTE (gate de espaco): ao contrario da versao anterior, os itens NAO
    sao todos registrados no aria2 de uma vez -- ficam numa fila local e sao
    liberados ao daemon aos poucos, respeitando 'disk_gate'. Isso evita que uma
    selecao grande (ex: uma franquia inteira com varios titulos de 20-30GB
    cada) tente baixar mais arquivos em paralelo do que o espaco livre real da
    VM do Colab aguenta -- o item so' entra na fila do aria2 quando ha espaco
    estimado suficiente, e mais espaco so' aparece quando outro item termina,
    e' enviado ao Drive, e tem o arquivo local apagado (ver
    _pos_processar_upload).

    Retorna results: List[DownloadResult] na MESMA ordem de 'options', para
    ser um substituto direto de run_concurrent do ponto de vista do chamador
    (Celula 4): mesmo contrato de retorno, sem exigir mudancas downstream.
    """
    upload_lock = threading.Lock()
    engine.start_daemon()

    if disk_gate is None and "DiskSpaceGate" in globals():
        disk_gate = DiskSpaceGate(dest_root)

    slot_by_gid: Dict[str, int] = {}
    gid_by_slot: Dict[int, str] = {}
    results: list = [None] * len(options)

    fila_pendente = list(enumerate(options))  # [(slot, option), ...] ainda fora do aria2
    _aviso_espaco_dado = [False]
    # bytes efetivamente reservados por slot (via disk_gate.tentar_reservar),
    # para devolver ao gate quando o download terminar (ver DiskSpaceGate).
    reserva_por_slot: Dict[int, int] = {}

    def _liberar_proximos_da_fila():
        while fila_pendente:
            slot, option = fila_pendente[0]
            bytes_estimados = estimar_bytes_de_tamanho_str(option.metadata.get("tamanho")) if "estimar_bytes_de_tamanho_str" in globals() else None
            if disk_gate is not None:
                reservado = disk_gate.tentar_reservar(bytes_estimados)
                if reservado is None:
                    if not _aviso_espaco_dado[0]:
                        _aviso_espaco_dado[0] = True
                        livre = disk_gate.espaco_livre_bytes()
                        print(f"    ⏳ Espaço em disco baixo ({_fmt_bytes(livre)} livres) — aguardando "
                              f"uploads pendentes liberarem espaço antes de iniciar mais downloads...")
                    return
                reserva_por_slot[slot] = reservado
            _aviso_espaco_dado[0] = False
            fila_pendente.pop(0)
            board.get_or_create(slot, option.label, total=len(options))
            gid = engine.add_download(option, dest_dir=dest_root)
            slot_by_gid[gid] = slot
            gid_by_slot[slot] = gid

            if control_panel is not None:
                control_panel.add_job(
                    slot, option.label,
                    on_pause=lambda g=gid: engine.pause_download(g),
                    on_resume=lambda g=gid: engine.resume_download(g),
                    on_cancel=lambda g=gid: engine.cancel_download(g),
                )

    try:
        _liberar_proximos_da_fila()

        while any(r is None for r in results):
            updates = engine.poll_once()
            for gid, u in updates.items():
                slot = slot_by_gid.get(gid)
                if slot is None:
                    continue

                board.handle_event(slot, "progress", u["telemetry"])

                if u["finished"]:
                    result = u["result"]
                    # Organiza localmente ou envia ao Drive, exatamente como run_concurrent faz
                    result = _pos_processar_upload(
                        result, options[slot], dest_root, drive_service, drive_folder_id,
                        upload_lock, on_chunk=lambda p, s=slot: board.handle_event(s, "upload", p)
                    )
                    results[slot] = result
                    board.handle_event(slot, "done", result)
                    if control_panel is not None:
                        control_panel.mark_finished(slot)
                    # Devolve a reserva de espaco deste slot ao gate -- o
                    # consumo real ja' esta' refletido em shutil.disk_usage()
                    # (sucesso: arquivo apagado apos upload; falha: nunca
                    # chegou a ocupar o tamanho estimado inteiro), entao a
                    # reserva pode ser liberada para os proximos itens da fila.
                    if disk_gate is not None and slot in reserva_por_slot:
                        disk_gate.liberar(reserva_por_slot.pop(slot))

            # Uploads deste ciclo podem ter liberado espaco -- tenta soltar mais
            # itens da fila local para o aria2 antes do proximo poll.
            _liberar_proximos_da_fila()

            time.sleep(poll_interval)
    finally:
        engine.stop_daemon(save_session=True)

    return results



# ==============================================================================
# 10. library_tools.py (Watchlist, Notificações, Mux, Renomeação Plex, Dashboard)
# ==============================================================================
"""
library_tools.py
=================
Camada de funcionalidades de biblioteca/pós-processamento construída sobre o
pipeline existente (DownloadOption, Manifest, drive_service). Cobre:
  - Watchlist persistente (JSON) de animes em acompanhamento.
  - Notificação de conclusão de lote (Discord/Telegram/e-mail).
  - Renomeação para padrão Plex/Jellyfin.
  - Mux automático de legenda externa (.srt/.ass) para dentro do .mkv.
  - Modo "só últimos N dias" de episódios lançados.
  - Dashboard de biblioteca (Drive ou local).
"""

import os
import json
import time
import smtplib
import subprocess
from email.mime.text import MIMEText
from pathlib import Path
from typing import Optional, List, Dict, Any


# ---------------------------------------------------------------------------
# 10.1 Watchlist persistente
# ---------------------------------------------------------------------------

class Watchlist:
    """
    Lista de animes "em acompanhamento", salva em JSON. Cada entrada guarda o
    nome de busca, filtro de qualidade preferida e o timestamp da última
    verificação — permitindo que "verificar tudo" baixe só o que é novo.
    """
    def __init__(self, path: str | Path):
        self.path = Path(path)
        self._data: Dict[str, Any] = {}
        if self.path.exists():
            try:
                self._data = json.loads(self.path.read_text(encoding="utf-8"))
            except Exception:
                self._data = {}

    def _save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        _escrever_atomico(self.path, json.dumps(self._data, indent=2, ensure_ascii=False))

    def add(self, anime_nome: str, qualidade_preferida: str = "1080p", fonte_anime: str = "Auto (Nyaa + DarkMahou)") -> None:
        self._data[anime_nome] = {
            "qualidade_preferida": qualidade_preferida,
            "fonte_anime": fonte_anime,
            "ultima_verificacao": self._data.get(anime_nome, {}).get("ultima_verificacao", 0),
            "adicionado_em": self._data.get(anime_nome, {}).get("adicionado_em", time.time()),
        }
        self._save()

    def remove(self, anime_nome: str) -> None:
        if anime_nome in self._data:
            del self._data[anime_nome]
            self._save()

    def marcar_verificado(self, anime_nome: str) -> None:
        if anime_nome in self._data:
            self._data[anime_nome]["ultima_verificacao"] = time.time()
            self._save()

    def listar(self) -> Dict[str, Any]:
        return dict(self._data)


# ---------------------------------------------------------------------------
# 10.2 Notificação de conclusão de lote
# ---------------------------------------------------------------------------

def notificar_discord(webhook_url: str, titulo: str, mensagem: str) -> bool:
    try:
        import requests
        resp = requests.post(webhook_url, json={"content": f"**{titulo}**\n{mensagem}"}, timeout=15)
        return resp.status_code in (200, 204)
    except Exception as e:
        print(f"⚠️ Falha ao notificar via Discord: {e}")
        return False


def notificar_telegram(bot_token: str, chat_id: str, titulo: str, mensagem: str) -> bool:
    try:
        import requests
        url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        texto = f"*{titulo}*\n{mensagem}"
        resp = requests.post(url, json={"chat_id": chat_id, "text": texto, "parse_mode": "Markdown"}, timeout=15)
        return resp.status_code == 200
    except Exception as e:
        print(f"⚠️ Falha ao notificar via Telegram: {e}")
        return False


def notificar_email(smtp_host: str, smtp_port: int, remetente: str, senha_app: str, destinatario: str, titulo: str, mensagem: str) -> bool:
    try:
        msg = MIMEText(mensagem, "plain", "utf-8")
        msg["Subject"] = titulo
        msg["From"] = remetente
        msg["To"] = destinatario
        with smtplib.SMTP_SSL(smtp_host, smtp_port) as server:
            server.login(remetente, senha_app)
            server.sendmail(remetente, [destinatario], msg.as_string())
        return True
    except Exception as e:
        print(f"⚠️ Falha ao notificar via e-mail: {e}")
        return False


def notificar_conclusao(resumo: str, titulo: str = "🎬 Download concluído") -> None:
    """
    Dispara notificação pelo canal configurado via Colab Secrets (🔑), testando
    na ordem: Discord (NOTIFY_DISCORD_WEBHOOK) -> Telegram (NOTIFY_TELEGRAM_BOT_TOKEN
    + NOTIFY_TELEGRAM_CHAT_ID) -> E-mail (NOTIFY_EMAIL_* ). Se nenhum estiver
    configurado, não faz nada (silenciosamente).
    """
    try:
        from google.colab import userdata
        get = userdata.get
    except Exception:
        get = lambda k: os.environ.get(k)

    def _get(key):
        try:
            return (get(key) or "").strip()
        except Exception:
            return ""

    webhook = _get("NOTIFY_DISCORD_WEBHOOK")
    if webhook:
        if notificar_discord(webhook, titulo, resumo):
            print("🔔 Notificação enviada via Discord.")
            return

    bot_token = _get("NOTIFY_TELEGRAM_BOT_TOKEN")
    chat_id = _get("NOTIFY_TELEGRAM_CHAT_ID")
    if bot_token and chat_id:
        if notificar_telegram(bot_token, chat_id, titulo, resumo):
            print("🔔 Notificação enviada via Telegram.")
            return

    smtp_host = _get("NOTIFY_EMAIL_SMTP_HOST")
    smtp_port = _get("NOTIFY_EMAIL_SMTP_PORT")
    remetente = _get("NOTIFY_EMAIL_FROM")
    senha_app = _get("NOTIFY_EMAIL_APP_PASSWORD")
    destinatario = _get("NOTIFY_EMAIL_TO")
    if smtp_host and smtp_port and remetente and senha_app and destinatario:
        if notificar_email(smtp_host, int(smtp_port), remetente, senha_app, destinatario, titulo, resumo):
            print("🔔 Notificação enviada via e-mail.")
            return


# ---------------------------------------------------------------------------
# 10.3 Renomeação para padrão Plex/Jellyfin
# ---------------------------------------------------------------------------

def nome_estilo_plex(anime_nome: str, temporada: int, episodio: int, extensao: str) -> str:
    nome_limpo = sanitizar_nome_arquivo(anime_nome, max_len=120) if "sanitizar_nome_arquivo" in globals() else anime_nome
    return f"{nome_limpo} - S{temporada:02d}E{episodio:02d}{extensao}"


def organizar_arquivo_plex(caminho_arquivo: str | Path, anime_nome: str, temporada: int, episodio: int, dest_root: str | Path) -> Path:
    """
    Move/renomeia um arquivo já baixado para a estrutura:
    <dest_root>/<Anime>/Season <NN>/<Anime> - S<NN>E<NN>.ext
    """
    p = Path(caminho_arquivo)
    nome_limpo = sanitizar_nome_arquivo(anime_nome, max_len=120) if "sanitizar_nome_arquivo" in globals() else anime_nome
    pasta_temporada = Path(dest_root) / nome_limpo / f"Season {temporada:02d}"
    pasta_temporada.mkdir(parents=True, exist_ok=True)
    novo_nome = nome_estilo_plex(anime_nome, temporada, episodio, p.suffix)
    destino = pasta_temporada / novo_nome
    p.rename(destino)
    return destino


# ---------------------------------------------------------------------------
# 10.4 Mux automático de legenda externa (.srt/.ass) para dentro do .mkv
# ---------------------------------------------------------------------------

def muxar_legenda_no_mkv(video_path: str | Path, legenda_path: str | Path, idioma: str = "por", titulo_faixa: str = "Português") -> Optional[Path]:
    """
    Usa ffmpeg para remuxar um .mkv com uma faixa de legenda externa (.srt/.ass)
    embutida e selecionável, sem recodificar o vídeo/áudio (-c copy).
    Retorna o novo caminho em caso de sucesso, ou None em caso de falha.
    """
    video_path = Path(video_path)
    legenda_path = Path(legenda_path)
    if not video_path.exists() or not legenda_path.exists():
        return None

    saida = video_path.with_name(video_path.stem + ".muxed.mkv")
    cmd = [
        "ffmpeg", "-y",
        "-i", str(video_path),
        "-i", str(legenda_path),
        "-map", "0", "-map", "1",
        "-c", "copy",
        "-c:s", "srt" if legenda_path.suffix.lower() == ".srt" else "ass",
        "-metadata:s:s:0", f"language={idioma}",
        "-metadata:s:s:0", f"title={titulo_faixa}",
        str(saida),
    ]
    try:
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=600)
        if result.returncode == 0 and saida.exists():
            video_path.unlink(missing_ok=True)
            legenda_path.unlink(missing_ok=True)
            final = video_path
            saida.rename(final)
            return final
    except Exception as e:
        print(f"⚠️ Falha ao muxar legenda: {e}")
    return None


def muxar_legendas_pendentes_na_pasta(pasta: str | Path) -> int:
    """
    Varre uma pasta procurando pares <nome>.mkv + <nome>.srt/.ass e muxa cada
    par encontrado. Retorna quantos arquivos foram muxados com sucesso.
    """
    pasta = Path(pasta)
    total = 0
    if not pasta.exists():
        return 0
    for video in pasta.glob("*.mkv"):
        for ext in (".srt", ".ass"):
            candidata = video.with_suffix(ext)
            if candidata.exists():
                if muxar_legenda_no_mkv(video, candidata):
                    total += 1
                break
    return total


# ---------------------------------------------------------------------------
# 10.5 Modo "só últimos N dias" de episódios lançados
# ---------------------------------------------------------------------------

def filtrar_opcoes_por_dias_recentes(opcoes: List[Any], dias: int) -> List[Any]:
    """
    Filtra uma lista de DownloadOption mantendo apenas as que têm metadata
    'publicado_em' (timestamp epoch) dentro da janela dos últimos `dias` dias.
    Opções sem essa metadata são mantidas (não é possível avaliar a idade).
    """
    if not dias or dias <= 0:
        return opcoes
    limite = time.time() - (dias * 86400)
    filtradas = []
    for o in opcoes:
        publicado_em = o.metadata.get("publicado_em") if hasattr(o, "metadata") else None
        if publicado_em is None or publicado_em >= limite:
            filtradas.append(o)
    return filtradas


# ---------------------------------------------------------------------------
# 10.5b Detecção de episódios já existentes na pasta de destino
# ---------------------------------------------------------------------------

def detectar_episodios_existentes(pasta: str | Path) -> set:
    """
    Varre uma pasta (local ou Drive montado) procurando arquivos de vídeo já
    baixados e extrai o número do episódio de cada um via anitopy. Serve como
    segunda camada de proteção contra redownload além do manifest.json — cobre
    o caso de manifest apagado/perdido, downloads feitos por outro método, ou
    arquivos movidos manualmente para a pasta.
    Retorna um set de números de episódio (int) já presentes na pasta.
    """
    pasta = Path(pasta)
    encontrados: set = set()
    if not pasta.exists():
        return encontrados

    exts_video = {".mkv", ".mp4", ".avi", ".webm"}
    try:
        import anitopy
    except Exception:
        anitopy = None

    for arquivo in pasta.glob("*"):
        if not arquivo.is_file() or arquivo.suffix.lower() not in exts_video:
            continue
        numero = None
        if anitopy is not None:
            try:
                parsed = anitopy.parse(arquivo.name)
                ep_str = parsed.get("episode_number")
                if ep_str and str(ep_str).isdigit():
                    numero = int(ep_str)
            except Exception as _exc:
                _registrar_erro(_exc)
        if numero is None:
            # Fallback: procura padrão "E<numero>" ou " - <numero>" no nome
            m = _re_episodio_fallback.search(arquivo.name)
            if m:
                try:
                    numero = int(m.group(1))
                except Exception:
                    numero = None
        if numero is not None:
            encontrados.add(numero)

    return encontrados


def detectar_episodios_existentes_drive(service, pasta_id: str) -> set:
    """
    Equivalente a detectar_episodios_existentes, mas consultando a Drive API
    diretamente (para quando DEST_DIR é só staging local e os episódios já
    enviados só existem de fato no Drive, como no modo OAuth 2.0 Pessoal).
    """
    encontrados: set = set()
    exts_video = (".mkv", ".mp4", ".avi", ".webm")
    try:
        import anitopy
    except Exception:
        anitopy = None

    try:
        arquivos = service.files().list(
            q=f"'{pasta_id}' in parents and trashed = false",
            fields="files(id, name)",
            supportsAllDrives=True,
            includeItemsFromAllDrives=True,
            pageSize=1000,
        ).execute().get("files", [])
    except Exception:
        return encontrados

    for f in arquivos:
        nome = f.get("name", "")
        if not nome.lower().endswith(exts_video):
            continue
        numero = None
        if anitopy is not None:
            try:
                parsed = anitopy.parse(nome)
                ep_str = parsed.get("episode_number")
                if ep_str and str(ep_str).isdigit():
                    numero = int(ep_str)
            except Exception as _exc:
                _registrar_erro(_exc)
        if numero is None:
            m = _re_episodio_fallback.search(nome)
            if m:
                try:
                    numero = int(m.group(1))
                except Exception:
                    numero = None
        if numero is not None:
            encontrados.add(numero)

    return encontrados


def filtrar_opcoes_ja_existentes(opcoes: List[Any], episodios_existentes: set) -> tuple:
    """
    Separa uma lista de DownloadOption em (pendentes, ja_existentes) com base
    no número de episódio (metadata['episodio']). Opções do tipo "Batch"
    (pacotes com vários episódios) não têm um único número e são sempre
    mantidas como pendentes, já que não dá pra saber se o pacote inteiro já
    está coberto pelos arquivos individuais existentes.
    """
    import re as _re_local
    pendentes, ja_existentes = [], []
    for o in opcoes:
        ep = o.metadata.get("episodio") if hasattr(o, "metadata") else None
        ep_num = None
        if isinstance(ep, int):
            ep_num = ep
        elif isinstance(ep, str) and ep.isdigit():
            ep_num = int(ep)
        if ep_num is not None and ep_num in episodios_existentes:
            ja_existentes.append(o)
        else:
            pendentes.append(o)
    return pendentes, ja_existentes


import re as _re_episodio_fallback_mod
_re_episodio_fallback = _re_episodio_fallback_mod.compile(r"(?:^|[ _\-\.])[Ee](\d{1,4})(?:[ _\-\.]|$)")


# ---------------------------------------------------------------------------
# 10.7 Gerenciamento de espaço em disco local (DiskSpaceGate)
# ---------------------------------------------------------------------------
"""
A VM do Colab tem tipicamente ~100-110GB de disco total, com ~50-60GB ja
ocupados pelo sistema/imagem base, sobrando uns 40-60GB uteis de verdade.
Uma franquia inteira (ex: Monogatari Series, ~15 obras) pode facilmente somar
dezenas de GB no total, e com ate 12 downloads simultaneos (ver
'downloads_simultaneos' na Celula 3), o notebook podia tentar baixar varios
arquivos grandes ao mesmo tempo sem nenhuma relacao com o espaco realmente
disponivel -- esgotando o disco no meio de um lote grande.

DiskSpaceGate resolve isso em conjunto com a correcao em _pos_processar_upload
(que agora sempre apaga o arquivo local apos o upload ser confirmado, inclusive
no caso de arquivo unico): antes de iniciar cada NOVO download, o motor
pergunta ao gate se ha espaco livre suficiente; se nao houver, espera (sem
cancelar nada que ja esta rodando) ate que outro item termine, seja enviado ao
Drive, tenha o arquivo local apagado, e libere espaco de verdade.
"""

def estimar_bytes_de_tamanho_str(tamanho_str: Optional[str]) -> Optional[int]:
    """
    Converte uma string de tamanho formatada (ex: "1.3 GiB", "453.5 MiB",
    "700 KiB", "2 GB") para bytes inteiros. Retorna None se nao conseguir
    parsear -- a chamada deve entao usar um valor padrao conservador.
    """
    if not tamanho_str:
        return None
    m = re.match(r"([\d.,]+)\s*([KMGT]?i?B)", str(tamanho_str).strip(), re.IGNORECASE)
    if not m:
        return None
    try:
        valor = float(m.group(1).replace(",", "."))
    except ValueError:
        return None
    unidade = m.group(2).upper()
    multiplicadores = {
        "B": 1,
        "KB": 1000, "KIB": 1024,
        "MB": 1000 ** 2, "MIB": 1024 ** 2,
        "GB": 1000 ** 3, "GIB": 1024 ** 3,
        "TB": 1000 ** 4, "TIB": 1024 ** 4,
    }
    mult = multiplicadores.get(unidade)
    if mult is None:
        return None
    return int(valor * mult)


class DiskSpaceGate:
    """
    Controla quantos downloads podem escrever no disco local ao mesmo tempo,
    evitando que o espaco livre real da VM do Colab seja esgotado no meio de
    um lote grande (ex: uma franquia com varios titulos de 20-30GB cada).
    Thread-safe: usado tanto por varios workers concorrentes (run_concurrent,
    threads separadas) quanto por um unico loop de polling que libera varios
    itens em sequencia (run_batch_rpc) -- em ambos os casos, o proprio gate
    mantem um contador interno de "bytes reservados" para downloads que ja'
    foram autorizados a comecar mas ainda nao terminaram. Sem isso, dois itens
    poderiam ser liberados ao mesmo tempo avaliando o MESMO espaco livre real
    "atual" (que so' reflete o que ja' foi fisicamente escrito no disco),
    permitindo overcommit.
    """
    def __init__(self, caminho_verificacao: str | Path, margem_seguranca_gb: float = 5.0,
                 tamanho_padrao_gb: float = 2.0, intervalo_poll_seg: float = 5.0):
        self.caminho_verificacao = Path(caminho_verificacao)
        self.margem_seguranca_bytes = int(margem_seguranca_gb * (1024 ** 3))
        self.tamanho_padrao_bytes = int(tamanho_padrao_gb * (1024 ** 3))
        self.intervalo_poll_seg = intervalo_poll_seg
        self._lock = threading.Lock()
        self._bytes_reservados = 0  # soma de reservas ativas (downloads autorizados, ainda nao liberados)

    def espaco_livre_bytes(self) -> int:
        alvo = self.caminho_verificacao if self.caminho_verificacao.exists() else self.caminho_verificacao.parent
        try:
            return shutil.disk_usage(alvo).free
        except Exception:
            # Se nao conseguir medir, assume o pior caso (nao ha espaco) em vez
            # de arriscar estourar o disco por falso-positivo de folga.
            return 0

    def _cabe_agora(self, bytes_estimados: int) -> bool:
        necessario = bytes_estimados + self.margem_seguranca_bytes
        return (self.espaco_livre_bytes() - self._bytes_reservados) >= necessario

    def tentar_reservar(self, bytes_estimados: Optional[int]) -> Optional[int]:
        """
        Checagem NAO bloqueante: se houver espaco (considerando reservas ja'
        ativas de outros downloads), reserva atomicamente e retorna a
        quantidade reservada (para repassar depois a `liberar`). Se nao houver
        espaco agora, retorna None sem reservar nada. Usado por run_batch_rpc,
        que roda num unico loop de polling e nao pode bloquear.
        """
        valor = bytes_estimados or self.tamanho_padrao_bytes
        with self._lock:
            if not self._cabe_agora(valor):
                if self._bytes_reservados == 0:
                    livre = self.espaco_livre_bytes()
                    if livre > self.margem_seguranca_bytes:
                        disponivel = livre - self.margem_seguranca_bytes
                        self._bytes_reservados += disponivel
                        return disponivel
                return None
            self._bytes_reservados += valor
            return valor

    def reservar(self, bytes_estimados: Optional[int], label: str = "") -> int:
        """
        Bloqueia a thread chamadora ate' conseguir reservar espaco, e retorna
        a quantidade reservada (para repassar depois a `liberar`). Usado pelo
        motor Classico (run_concurrent), onde cada download roda em sua
        propria thread -- bloquear ali e' seguro e nao trava os demais.
        """
        avisou = False
        while True:
            reservado = self.tentar_reservar(bytes_estimados)
            if reservado is not None:
                return reservado
            if not avisou:
                avisou = True
                livre = self.espaco_livre_bytes()
                print(f"    ⏳ Espaço em disco baixo ({_fmt_bytes(livre)} livres) — aguardando "
                      f"uploads pendentes liberarem espaço antes de baixar '{label}'...")
            time.sleep(self.intervalo_poll_seg)

    def liberar(self, bytes_reservados: int) -> None:
        """
        Devolve uma reserva feita por `reservar`/`tentar_reservar` -- chamar
        SEMPRE que o download correspondente terminar, com sucesso ou falha,
        para nao deixar espaco preso indefinidamente na contabilidade interna.
        """
        with self._lock:
            self._bytes_reservados = max(0, self._bytes_reservados - bytes_reservados)


# ---------------------------------------------------------------------------
# 10.6 Dashboard de biblioteca
# ---------------------------------------------------------------------------

def escanear_biblioteca_local(pasta_raiz: str | Path) -> Dict[str, Any]:
    """
    Varre uma pasta raiz (Drive montado ou local) e monta um resumo: quantos
    animes (subpastas), quantos arquivos de vídeo e o espaço total ocupado.
    """
    pasta_raiz = Path(pasta_raiz)
    resumo = {"animes": 0, "episodios": 0, "espaco_bytes": 0, "detalhes": []}
    if not pasta_raiz.exists():
        return resumo

    exts_video = {".mkv", ".mp4", ".avi", ".webm"}
    for anime_dir in sorted(p for p in pasta_raiz.iterdir() if p.is_dir()):
        arquivos_video = [f for f in anime_dir.rglob("*") if f.is_file() and f.suffix.lower() in exts_video]
        if not arquivos_video:
            continue
        tamanho = sum(f.stat().st_size for f in arquivos_video)
        resumo["animes"] += 1
        resumo["episodios"] += len(arquivos_video)
        resumo["espaco_bytes"] += tamanho
        resumo["detalhes"].append({"nome": anime_dir.name, "episodios": len(arquivos_video), "espaco_bytes": tamanho})

    return resumo


def escanear_biblioteca_drive(service, pasta_id: str) -> Dict[str, Any]:
    """
    Equivalente a escanear_biblioteca_local, mas consultando a Drive API
    diretamente (útil quando os arquivos não estão em disco local/montado,
    como no modo OAuth 2.0 Pessoal sem Drive montado).
    """
    resumo = {"animes": 0, "episodios": 0, "espaco_bytes": 0, "detalhes": []}
    exts_video = (".mkv", ".mp4", ".avi", ".webm")

    subpastas = service.files().list(
        q=f"'{pasta_id}' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
        fields="files(id, name)",
        supportsAllDrives=True,
        includeItemsFromAllDrives=True,
        pageSize=1000,
    ).execute().get("files", [])

    for pasta_anime in subpastas:
        arquivos = service.files().list(
            q=f"'{pasta_anime['id']}' in parents and trashed = false",
            fields="files(id, name, size)",
            supportsAllDrives=True,
            includeItemsFromAllDrives=True,
            pageSize=1000,
        ).execute().get("files", [])
        videos = [f for f in arquivos if f["name"].lower().endswith(exts_video)]
        if not videos:
            continue
        tamanho = sum(int(f.get("size", 0) or 0) for f in videos)
        resumo["animes"] += 1
        resumo["episodios"] += len(videos)
        resumo["espaco_bytes"] += tamanho
        resumo["detalhes"].append({"nome": pasta_anime["name"], "episodios": len(videos), "espaco_bytes": tamanho})

    return resumo


def _fmt_bytes(n: float) -> str:
    for unidade in ["B", "KB", "MB", "GB", "TB"]:
        if n < 1024:
            return f"{n:.1f}{unidade}" if unidade != "B" else f"{n}{unidade}"
        n /= 1024
    return f"{n:.1f}PB"


def imprimir_dashboard(resumo: Dict[str, Any]) -> None:
    print("=" * 60)
    print("📚 DASHBOARD DA BIBLIOTECA")
    print("=" * 60)
    print(f"🎬 Animes: {resumo['animes']}")
    print(f"📺 Episódios: {resumo['episodios']}")
    print(f"💾 Espaço total: {_fmt_bytes(resumo['espaco_bytes'])}")
    print("-" * 60)
    for d in sorted(resumo["detalhes"], key=lambda x: -x["espaco_bytes"]):
        print(f"  • {d['nome']}: {d['episodios']} eps — {_fmt_bytes(d['espaco_bytes'])}")
    print("=" * 60)


print("✅ Todos os módulos carregados com sucesso!")
print("📋 Seletor Interativo, Nyaa.si, DarkMahou, anitopy e filtros prontos para uso.")
print("🆕 Watchlist, notificações, mux de legenda, renomeação Plex e dashboard disponíveis (Células 5-8).")