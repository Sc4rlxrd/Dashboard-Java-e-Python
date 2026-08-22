import pandas as pd
import plotly.express as px
import streamlit as st


GENERAL_VIEW = "🌐 Visão geral"


def render_history_chart(dataframe: pd.DataFrame) -> None:
    st.markdown("---")
    st.subheader("📈 Histórico de Variação")

    control_mode, control_product = st.columns(
        [1, 2]
    )

    with control_mode:
        history_mode = st.radio(
            "Detalhamento:",
            options=[
                "📅 Diário",
                "🔎 Todas as coletas",
            ],
            horizontal=True,
            index=0,
            key="history-detail-mode",
            help=(
                "O modo Diário utiliza o último preço "
                "registrado de cada dia e reduz a quantidade "
                "de pontos renderizados."
            ),
        )

    available_models = sorted(
        dataframe["model"]
        .dropna()
        .unique()
        .tolist()
    )

    highlight_options = [
        GENERAL_VIEW,
        *available_models,
    ]

    current_highlight = st.session_state.get(
        "history-highlight-product"
    )

    if (
        current_highlight is not None
        and current_highlight
        not in highlight_options
    ):
        st.session_state[
            "history-highlight-product"
        ] = GENERAL_VIEW

    with control_product:
        highlighted_model = st.selectbox(
            "🎯 Produto em destaque:",
            options=highlight_options,
            index=0,
            key="history-highlight-product",
            help=(
                "Escolha um produto para destacá-lo. "
                "Os demais continuam visíveis em segundo plano."
            ),
        )

    history_df = (
        dataframe[
            [
                "collectionDate",
                "model",
                "price",
                "store",
            ]
        ]
        .sort_values("collectionDate")
        .copy()
    )

    original_points = len(history_df)

    if history_mode == "📅 Diário":
        history_df = _aggregate_daily(
            history_df
        )

    history_df = history_df.sort_values(
        [
            "model",
            "store",
            "collectionDate",
        ]
    )

    rendered_points = len(history_df)

    history_df["priceLabel"] = (
        history_df["price"]
        .map(_format_brl)
    )

    _render_history_summary(
        history_df=history_df,
        original_points=original_points,
        rendered_points=rendered_points,
        history_mode=history_mode,
        highlighted_model=highlighted_model,
    )

    figure = px.line(
        history_df,
        x="collectionDate",
        y="price",
        color="model",
        line_dash="store",
        render_mode="webgl",
        custom_data=[
            "model",
            "store",
            "priceLabel",
        ],
        labels={
            "collectionDate": "Data",
            "price": "Preço",
            "model": "Produto",
            "store": "Loja",
        },
        template="plotly_dark",
    )

    _style_traces(
        figure=figure,
        highlighted_model=highlighted_model,
    )

    hover_date_format = (
        "%d/%m/%Y"
        if history_mode == "📅 Diário"
        else "%d/%m/%Y %H:%M"
    )

    figure.update_traces(
        hovertemplate=(
            "<b>%{customdata[0]}</b>"
            "<br>🏪 %{customdata[1]}"
            "<br>💰 %{customdata[2]}"
            f"<br>📅 %{{x|{hover_date_format}}}"
            "<extra></extra>"
        ),
        connectgaps=False,
    )

    figure.update_xaxes(
        title="Data da coleta",
        showgrid=False,
        zeroline=False,
        showline=False,
    )

    figure.update_yaxes(
        title="Preço",
        tickprefix="R$ ",
        autorange=True,
        showgrid=True,
        gridcolor="rgba(255,255,255,0.08)",
        zeroline=False,
    )

    figure.update_layout(
        height=520,
        showlegend=False,
        hovermode="closest",
        uirevision=f"history-{history_mode}",
        margin={
            "l": 20,
            "r": 20,
            "t": 15,
            "b": 20,
        },
        paper_bgcolor="rgba(0,0,0,0)",
        plot_bgcolor="rgba(0,0,0,0)",
        font={
            "size": 13,
        },
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


def _aggregate_daily(dataframe: pd.DataFrame,) -> pd.DataFrame:
    daily_df = dataframe.copy()

    daily_df["collectionDay"] = (
        daily_df["collectionDate"]
        .dt.floor("D")
    )

    daily_df = (
        daily_df
        .sort_values("collectionDate")
        .groupby(
            [
                "store",
                "model",
                "collectionDay",
            ],
            sort=False,
        )
        .tail(1)
        .copy()
    )

    daily_df["collectionDate"] = (
        daily_df["collectionDay"]
    )

    return daily_df.drop(
        columns=["collectionDay"]
    )


def _style_traces(figure,highlighted_model: str,) -> None:
    general_view = (
        highlighted_model == GENERAL_VIEW
    )

    background_traces = []
    highlighted_traces = []

    for trace in figure.data:
        model_name = _trace_model(
            trace
        )

        if general_view:
            trace.update(
                opacity=0.58,
                mode="lines",
                line={
                    "width": 1.6,
                },
            )

            background_traces.append(
                trace
            )

            continue

        is_highlighted = (
            model_name == highlighted_model
        )

        if is_highlighted:
            trace.update(
                opacity=1.0,
                mode="lines+markers",
                line={
                    "width": 3.5,
                },
                marker={
                    "size": 6,
                },
            )

            highlighted_traces.append(
                trace
            )

        else:
            trace.update(
                opacity=0.10,
                mode="lines",
                line={
                    "width": 1.0,
                },
                hoverinfo="skip",
                hovertemplate=None,
            )

            background_traces.append(
                trace
            )

    if not general_view:
        figure.data = tuple(
            background_traces
            + highlighted_traces
        )


def _trace_model(trace) -> str | None:
    custom_data = trace.customdata

    if (
        custom_data is None
        or len(custom_data) == 0
    ):
        return None

    return custom_data[0][0]


def _render_history_summary(
    history_df: pd.DataFrame,
    original_points: int,
    rendered_points: int,
    history_mode: str,
    highlighted_model: str,
) -> None:
    product_count = (
        history_df["model"]
        .nunique()
    )

    summary = (
        f"📊 {_format_integer(product_count)} produto(s)"
        f" · {_format_integer(rendered_points)} ponto(s)"
    )

    if (
        history_mode == "📅 Diário"
        and original_points > rendered_points
    ):
        reduction = (
            1
            - (
                rendered_points
                / original_points
            )
        ) * 100

        summary += (
            f" de {_format_integer(original_points)} coletas"
            f" · {reduction:.0f}% menos pontos"
        )

    st.caption(summary)

    if highlighted_model != GENERAL_VIEW:
        st.caption(
            f"🎯 Destaque: {highlighted_model} · "
            "os demais produtos estão em segundo plano"
        )


def _format_brl(value: float) -> str:
    formatted = f"{value:,.2f}"

    formatted = (
        formatted
        .replace(",", "_")
        .replace(".", ",")
        .replace("_", ".")
    )

    return f"R$ {formatted}"


def _format_integer(value: int) -> str:
    return f"{value:,}".replace(
        ",",
        ".",
    )