import pandas as pd
import streamlit as st


def render_price_drop_cards(
    dataframe: pd.DataFrame,
    limit: int = 5,
) -> None:
    st.markdown("---")
    st.subheader("🔥 Quedas de Preço")

    price_drops = _find_price_drops(
        dataframe
    )

    if price_drops.empty:
        st.info(
            "Nenhuma queda de preço foi detectada "
            "na coleta mais recente."
        )
        return

    price_drops = (
        price_drops
        .sort_values(
            "discountPercent",
            ascending=False,
        )
        .head(limit)
    )

    columns = st.columns(
        len(price_drops)
    )

    for column, (_, product) in zip(
        columns,
        price_drops.iterrows(),
    ):
        current_price = product[
            "currentPrice"
        ]

        previous_price = product[
            "previousPrice"
        ]

        discount_percent = product[
            "discountPercent"
        ]

        saved_amount = (
            previous_price
            - current_price
        )

        column.metric(
            label=(
                f"{_truncate(product['model'])}"
                f" · {product['store']}"
            ),
            value=_format_brl(
                current_price
            ),
            delta=(
                f"-{discount_percent:.1f}% "
                f"({_format_brl(saved_amount)})"
            ),
            delta_color="inverse",
        )

        column.caption(
            "Antes: "
            + _format_brl(
                previous_price
            )
        )


def _find_price_drops(
    dataframe: pd.DataFrame,
) -> pd.DataFrame:
    results = []

    grouped = (
        dataframe
        .sort_values("collectionDate")
        .groupby(
            ["store", "model"]
        )
    )

    for (
        store,
        model,
    ), history in grouped:
        history = history.sort_values(
            "collectionDate"
        )

 
        if len(history) < 2:
            continue

        previous = history.iloc[-2]
        current = history.iloc[-1]

        previous_price = float(
            previous["price"]
        )

        current_price = float(
            current["price"]
        )

        if current_price >= previous_price:
            continue

        discount_percent = (
            (
                previous_price
                - current_price
            )
            / previous_price
            * 100
        )

        results.append(
            {
                "store": store,
                "model": model,
                "previousPrice": previous_price,
                "currentPrice": current_price,
                "discountPercent": discount_percent,
                "collectionDate": current[
                    "collectionDate"
                ],
            }
        )

    return pd.DataFrame(
        results
    )


def _truncate(
    model: str,
    limit: int = 24,
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