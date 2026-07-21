import os
from pathlib import Path

import pandas as pd
import plotly.express as px
import streamlit as st


JSON_FILE = Path(
    os.getenv("PRICE_DATA_FILE", "dadosParaDashBoards/precos.json")
)

st.set_page_config(
    page_title="Monitor de Preços Scarlxrd",
    page_icon="💸",
    layout="wide",
)

st.title("💸 Monitoramento de Preços Inteligente do Scarlxrd")
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
# CARDS DA ÚLTIMA COLETA
# ==========================================================

latest_products = (
    df_filtered.sort_values("collectionDate")
    .groupby(["store", "model"], as_index=False)
    .tail(1)
    .sort_values("collectionDate", ascending=False)
    .head(5)
)

columns = st.columns(len(latest_products)) if len(latest_products) else [st]

for column, (_, product) in zip(columns, latest_products.iterrows()):
    product_history = df_filtered[
        (df_filtered["model"] == product["model"])
        & (df_filtered["store"] == product["store"])
    ]

    current_price = product["price"]
    minimum_price = product_history["price"].min()
    difference = current_price - minimum_price
    percent_diff = (
        (difference / minimum_price * 100) if minimum_price else 0
    )

    if difference <= 0:
        delta = "Menor preço histórico"
    else:
        delta = f"+{percent_diff:.1f}% (R$ {difference:,.2f}) acima do mínimo"

    column.metric(
        label=f"{product['model'][:20]} · {product['store']}",
        value=f"R$ {current_price:,.2f}",
        delta=delta,
        delta_color="inverse",
    )


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

st.markdown("---")
st.subheader("📈 Histórico de Variação (Evolução Temporal)")

figure_line = px.line(
    df_filtered,
    x="collectionDate",
    y="price",
    color="model",
    line_dash="store",
    markers=True,
    hover_data={
        "store": True,
        "model": True,
        "price": ":.2f",
        "collectionDate": True,
    },
    labels={
        "collectionDate": "Data da coleta",
        "price": "Preço",
        "model": "Produto",
        "store": "Loja",
    },
    title="Oscilação de Preços ao Longo do Tempo",
    template="plotly_dark",
)

figure_line.update_yaxes(tickprefix="R$ ", autorange=True)
figure_line.update_layout(legend_title_text="Produtos e lojas")
figure_line.update_layout(showlegend=False)
st.plotly_chart(figure_line, width="stretch")



# ==========================================================
# COMPARATIVO DA ÚLTIMA COLETA
# ==========================================================

st.markdown("---")
st.subheader("📊 Comparativo da Última Coleta")

latest_collection = (
    df_filtered.sort_values("collectionDate")
    .groupby(["store", "model"], as_index=False)
    .tail(1)
)

figure_bar = px.bar(
    latest_collection,
    x="model",
    y="price",
    color="store",
    barmode="group",
    text_auto=".2f",
    template="plotly_dark",
    labels={"model": "Produto", "price": "Preço", "store": "Loja"},
)

figure_bar.update_yaxes(tickprefix="R$ ")
figure_bar.update_layout(
    xaxis_title="Produto",
    yaxis_title="Preço atual",
    legend_title_text="Loja",
)

st.plotly_chart(figure_bar, width="stretch")


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