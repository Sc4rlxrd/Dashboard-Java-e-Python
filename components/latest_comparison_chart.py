import pandas as pd
import plotly.express as px
import streamlit as st


def render_latest_comparison_chart(
    dataframe: pd.DataFrame,
) -> None:
    st.markdown("---")
    st.subheader("📊 Comparativo da Última Coleta")

    latest_collection = (
        dataframe
        .sort_values("collectionDate")
        .groupby(
            ["store", "model"],
            as_index=False,
        )
        .tail(1)
        .sort_values(
            "price",
            ascending=True,
        )
        .copy()
    )

    if latest_collection.empty:
        st.info(
            "Nenhuma coleta disponível "
            "para comparação."
        )
        return

    latest_collection["priceLabel"] = (
        latest_collection["price"]
        .map(_format_brl)
    )

    latest_collection["modelLabel"] = (
        latest_collection["model"]
        .map(_truncate_model)
    )

    product_count = len(
        latest_collection
    )

    chart_height = max(
        500,
        product_count * 34,
    )

    figure = px.bar(
        latest_collection,
        x="price",
        y="modelLabel",
        color="store",
        orientation="h",
        custom_data=[
            "model",
            "store",
            "priceLabel",
            "collectionDate",
        ],
        labels={
            "price": "Preço atual",
            "modelLabel": "Produto",
            "store": "Loja",
        },
        template="plotly_dark",
    )

    figure.update_traces(
        text=latest_collection[
            "priceLabel"
        ],
        textposition="outside",
        cliponaxis=False,
        hovertemplate=(
            "<b>%{customdata[0]}</b>"
            "<br>🏪 %{customdata[1]}"
            "<br>💰 %{customdata[2]}"
            "<br>📅 %{customdata[3]|%d/%m/%Y %H:%M}"
            "<extra></extra>"
        ),
    )

    figure.update_xaxes(
        title="Preço atual",
        tickprefix="R$ ",
        showgrid=True,
        gridcolor="rgba(255,255,255,0.08)",
        zeroline=False,
    )

    figure.update_yaxes(
        title=None,
        categoryorder="total ascending",
        showgrid=False,
    )

    figure.update_layout(
        height=chart_height,
        legend_title_text="Loja",
        margin={
            "l": 20,
            "r": 80,
            "t": 20,
            "b": 20,
        },
        paper_bgcolor="rgba(0,0,0,0)",
        plot_bgcolor="rgba(0,0,0,0)",
        font={
            "size": 12,
        },
        bargap=0.25,
    )

    st.caption(
        f"📦 {product_count} produto(s) "
        "comparados pela coleta mais recente"
    )

    st.plotly_chart(
        figure,
        width="stretch",
        config={
            "displaylogo": False,
            "responsive": True,
            "scrollZoom": False,
            "modeBarButtonsToRemove": [
                "lasso2d",
                "select2d",
            ],
        },
    )


def _truncate_model(
    model: str,
    limit: int = 52,
) -> str:
    model = str(model).strip()

    if len(model) <= limit:
        return model

    return (
        model[: limit - 1].rstrip()
        + "…"
    )


def _format_brl(
    value: float,
) -> str:
    formatted = f"{value:,.2f}"

    formatted = (
        formatted
        .replace(",", "_")
        .replace(".", ",")
        .replace("_", ".")
    )

    return f"R$ {formatted}"