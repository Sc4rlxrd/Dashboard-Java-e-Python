import os
from pathlib import Path

import pandas as pd
import plotly.express as px
import streamlit as st

from components.history_chart import render_history_chart
from components.url_form import render_url_form
from services.url_manager import UrlManager
from components.latest_comparison_chart import render_latest_comparison_chart
from components.price_drop_cards import render_price_drop_cards

BASE_DIR = Path(__file__).resolve().parent

JSON_FILE = Path(
    os.getenv(
        "PRICE_DATA_FILE",
        str(
            BASE_DIR
            / "dadosParaDashBoards"
            / "precos.json"
        ),
    )
)

URLS_FILE = Path(
    os.getenv(
        "URLS_FILE",
        str(
            BASE_DIR
            / "datacollector"
            / "urls.txt"
        ),
    )
)

SUPPORTED_STORES = {
    "amazon.com.br": "Amazon",
    "boadica.com.br": "BoaDica",
    "kabum.com.br": "KaBuM",
}


st.set_page_config(
    page_title="Monitor de Preços Scarlxrd",
    page_icon="💸",
    layout="wide",
)

st.title("💸 Monitoramento de Preços Inteligente do Scarlxrd")

url_manager = UrlManager(
    urls_file=URLS_FILE,
    supported_stores=SUPPORTED_STORES,
)

render_url_form(url_manager)
st.markdown("---")

if not JSON_FILE.exists():
    st.warning(
        "JSON não encontrado. Verifique se o volume do Docker "
        "está montado corretamente."
    )
    st.stop()


# ==========================================================
# CARREGAMENTO (com cache)
# ==========================================================

@st.cache_data(ttl=300)  # recarrega no máximo a cada 5 min
def load_data(path: Path, mtime: float) -> pd.DataFrame:
    dataframe = pd.read_json(path)

    required_columns = {"collectionDate", "model", "price", "store"}
    missing_columns = required_columns.difference(dataframe.columns)
    if missing_columns:
        raise ValueError(
            "O JSON não possui as colunas necessárias: "
            + ", ".join(sorted(missing_columns))
        )

    dataframe["collectionDate"] = pd.to_datetime(
        dataframe["collectionDate"], errors="coerce"
    )
    dataframe["price"] = pd.to_numeric(dataframe["price"], errors="coerce")
    dataframe["model"] = dataframe["model"].astype(str).str.strip()
    dataframe["store"] = dataframe["store"].astype(str).str.strip()

    dataframe = (
        dataframe.dropna(subset=["collectionDate", "model", "price", "store"])
        .loc[lambda df_: df_["price"] > 0]
        .sort_values(by="collectionDate")
    )

    return dataframe


try:
    df = load_data(JSON_FILE, JSON_FILE.stat().st_mtime)
except ValueError as exception:
    st.error(f"Não foi possível ler o JSON: {exception}")
    st.stop()

if df.empty:
    st.warning("Nenhuma coleta válida foi encontrada.")
    st.stop()

# ==========================================================
# FILTROS
# ==========================================================

st.sidebar.header("🎯 Painel de Filtros")

stores = sorted(df["store"].unique().tolist())

selected_stores = st.sidebar.multiselect(
    "Loja(s):",
    options=stores,
    default=stores,
)

if not selected_stores:
    st.info("Selecione ao menos uma loja para continuar.")
    st.stop()

df_store = df[df["store"].isin(selected_stores)].copy()

if df_store.empty:
    st.info("Nenhum dado encontrado para a(s) loja(s) selecionada(s).")
    st.stop()

available_models = sorted(df_store["model"].unique().tolist())

selected_models = st.sidebar.multiselect(
    "Produtos:",
    options=available_models,
    default=available_models,
)

if not selected_models:
    st.info("Selecione ao menos um produto para continuar.")
    st.stop()

# --- filtro de período ---
min_date = df_store["collectionDate"].min().date()
max_date = df_store["collectionDate"].max().date()

date_range = st.sidebar.date_input(
    "Período:",
    value=(min_date, max_date),
    min_value=min_date,
    max_value=max_date,
    format="DD/MM/YYYY",
)

if isinstance(date_range, tuple) and len(date_range) == 2:
    start_date, end_date = date_range
else:
    start_date, end_date = min_date, max_date

df_filtered = df_store[
    df_store["model"].isin(selected_models)
    & (df_store["collectionDate"].dt.date >= start_date)
    & (df_store["collectionDate"].dt.date <= end_date)
    ].copy()

st.sidebar.markdown("---")
st.sidebar.caption(f"🏪 Lojas: {', '.join(selected_stores) or '—'}")
st.sidebar.caption(f"📦 Produtos selecionados: {len(selected_models)}")
st.sidebar.caption(f"📋 Coletas encontradas: {len(df_filtered)}")

if df_filtered.empty:
    st.info("Nenhum produto encontrado para os filtros selecionados.")
    st.stop()


# ==========================================================
# MELHOR OFERTA ATUAL POR PRODUTO (entre lojas)
# ==========================================================

st.markdown("---")
st.subheader("🏆 Melhor Oferta Atual por Produto")

best_offers = (
    df_filtered.sort_values("collectionDate")
    .groupby(["store", "model"], as_index=False)
    .tail(1)  # último preço coletado por loja+modelo
    .sort_values("price")
    .drop_duplicates(subset="model", keep="first")  # menor preço por modelo
    .sort_values("model")
)

st.dataframe(
    best_offers.rename(
        columns={
            "model": "Produto",
            "store": "Loja mais barata",
            "price": "Preço",
            "collectionDate": "Coletado em",
        }
    )[["Produto", "Loja mais barata", "Preço", "Coletado em"]].assign(
        Preço=lambda df_: df_["Preço"].map("R$ {:,.2f}".format)
    ),
    width="stretch",
    hide_index=True,
)

# ==========================================================
# GRÁFICO DE HISTÓRICO
# ==========================================================

render_history_chart(
    dataframe=df_filtered
)

# ==========================================================
# COMPARATIVO DA ÚLTIMA COLETA
# ==========================================================

render_latest_comparison_chart(
    dataframe=df_filtered,
)

# ==========================================================
# QUEDAS DE PREÇO
# ==========================================================

render_price_drop_cards(
    dataframe=df_filtered,limit=10
)


# ==========================================================
# TABELA + EXPORT
# ==========================================================

st.markdown("---")

with st.expander("📋 Ver Histórico Completo de Coletas"):
    df_view = df_filtered.sort_values(
        by="collectionDate", ascending=False
    ).copy()

    csv_bytes = df_view.rename(
        columns={
            "collectionDate": "Data da coleta",
            "model": "Produto",
            "price": "Preço",
            "store": "Loja",
        }
    )[["Data da coleta", "Produto", "Preço", "Loja"]].to_csv(
        index=False
    ).encode("utf-8-sig")

    st.download_button(
        "⬇️ Baixar CSV filtrado",
        data=csv_bytes,
        file_name="historico_precos.csv",
        mime="text/csv",
    )

    df_view["price"] = df_view["price"].map("R$ {:,.2f}".format)
    df_view = df_view.rename(
        columns={
            "collectionDate": "Data da coleta",
            "model": "Produto",
            "price": "Preço",
            "store": "Loja",
        }
    )

    st.dataframe(
        df_view[["Data da coleta", "Produto", "Preço", "Loja"]],
        width="stretch",
        hide_index=True,
    )
