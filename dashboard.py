import streamlit as st
import pandas as pd
import plotly.express as px
import os

st.set_page_config(page_title="Monitor de Preços Scarlxrd", layout="wide")
JSON_FILE = "dadosParaDashBoards/precos.json"

st.title("💸 Monitoramento de Preços Inteligente Do Scarlxrd")
st.markdown("---")

if os.path.exists(JSON_FILE):
    df = pd.read_json(JSON_FILE)
    df['collectionDate'] = pd.to_datetime(df['collectionDate'])
    
    # IMPORTANTE: Ordenar por data para o gráfico de linha funcionar
    df = df[df['price'] > 0].sort_values(by='collectionDate')

    st.sidebar.header("🎯 Painel de Filtros")
    selecionados_modelos = st.sidebar.multiselect("Produtos:", options=sorted(df['model'].unique()), default=df['model'].unique())
    
    df_filtrado = df[df['model'].isin(selecionados_modelos)]

    if not df_filtrado.empty:
        # --- CARDS DE RESUMO (Última Coleta) ---
        cols = st.columns(len(selecionados_modelos[:5]))
        for i, modelo in enumerate(selecionados_modelos[:5]):
            dados_m = df_filtrado[df_filtrado['model'] == modelo]
            atual = dados_m.iloc[-1]['price']
            minimo = dados_m['price'].min()
            delta = "MENOR PREÇO!" if atual <= minimo else f"+ R$ {atual - minimo:.2f}"
            cols[i].metric(label=modelo[:15], value=f"R$ {atual:,.2f}", delta=delta, delta_color="inverse")

            # --- GRÁFICO 1: EVOLUÇÃO (LINHAS) ---
        # Aqui é onde você verá os dados de ONTEM e HOJE conectados
        st.subheader("📈 Histórico de Variação (Evolução Temporal)")
        fig_line = px.line(
            df_filtrado, 
            x='collectionDate', 
            y='price', 
            color='model',
            markers=True,
            title="Oscilação de Preços ao Longo do Tempo",
            template="plotly_dark"
        )
        fig_line.update_yaxes(tickprefix="R$ ", autorange=True)
        st.plotly_chart(fig_line, width="stretch")

        st.markdown("---")
        st.subheader("📊 Comparativo da Última Coleta")
        df_recente = df_filtrado.groupby(['model', 'store']).tail(1)
        fig_bar = px.bar(
            df_recente, x='model', y='price', color='model',
            text_auto='.2f', template="plotly_dark"
        )
        st.plotly_chart(fig_bar, width="stretch")

        # --- TABELA DE HISTÓRICO ---
        st.markdown("---")
        with st.expander("📋 Ver Histórico Completo de Coletas"):
            df_view = df_filtrado.sort_values(by='collectionDate', ascending=False).copy()
            df_view['price'] = df_view['price'].map('R$ {:,.2f}'.format)
            st.dataframe(df_view[['collectionDate', 'model', 'price', 'store']], width="stretch", hide_index=True)
    else:
        st.info("Selecione um produto para visualizar.")
else:
    st.warning("JSON não encontrado. Certifique-se de que o volume do Docker está montado corretamente.")