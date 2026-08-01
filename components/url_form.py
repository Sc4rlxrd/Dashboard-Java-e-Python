import streamlit as st

from services.url_manager import UrlManager


def render_url_form( url_manager: UrlManager,) -> None:
    with st.expander(
            "➕ Adicionar produto ao monitoramento",
            expanded=False,
    ):
        st.write(
            "Cole abaixo o endereço de um produto "
            "da Amazon ou do BoaDica."
        )

        st.caption(
            "A URL será adicionada ao arquivo usado "
            "pelo collector Java e será processada "
            "na próxima coleta."
        )

        with st.form(
                "add_product_url_form",
                clear_on_submit=True,
        ):
            product_url = st.text_input(
                "URL do produto",
                placeholder=(
                    "https://www.amazon.com.br/...",
                    
                ),
            )

            submitted = st.form_submit_button(
                "Adicionar URL"
            )

        if submitted:
            try:
                (
                    cleaned_url,
                    store_name,
                ) = url_manager.add_url(
                    product_url
                )

                st.success(
                    f"URL da {store_name} adicionada "
                    "com sucesso."
                )

                st.code(
                    cleaned_url,
                    language=None,
                )

                st.info(
                    "O produto será processado na "
                    "próxima execução do collector."
                )

            except ValueError as exception:
                st.warning(str(exception))

            except OSError as exception:
                st.error(
                    "Não foi possível escrever no "
                    f"arquivo de URLs: {exception}"
                )

        _render_active_urls(url_manager)


def _render_active_urls(url_manager: UrlManager,) -> None:
    try:
        active_urls = (
            url_manager.list_active_urls()
        )

        st.caption(
            f"📋 URLs ativas no collector: "
            f"{len(active_urls)}"
        )

        show_active_urls = st.checkbox(
            "Mostrar URLs cadastradas",
            key="show-active-urls",
        )

        if show_active_urls:
            if active_urls:
                st.code(
                    "\n".join(active_urls),
                    language=None,
                )
            else:
                st.info(
                    "Nenhuma URL ativa cadastrada."
                )

    except OSError as exception:
        st.warning(
            "Não foi possível consultar o arquivo "
            f"de URLs: {exception}"
        )
