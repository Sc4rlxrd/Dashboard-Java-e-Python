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
    # Filtra apenas preços válidos e ordena por data
    df = df[df['price'] > 0].sort_values(by='collectionDate')

    st.sidebar.header("🎯 Painel de Filtros")
    selecionados_modelos = st.sidebar.multiselect("Produtos:", options=sorted(df['model'].unique()), default=df['model'].unique())
    selecionadas_lojas = st.sidebar.multiselect("Lojas:", options=sorted(df['store'].unique()), default=df['store'].unique())
    
    df_filtrado = df[(df['model'].isin(selecionados_modelos)) & (df['store'].isin(selecionadas_lojas))]

    if not df_filtrado.empty:
        
        col1, col2, col3 = st.columns(3)
        modelos_cards = selecionados_modelos[:3]
        cols = [col1, col2, col3]

        for i, modelo in enumerate(modelos_cards):
            dados_m = df_filtrado[df_filtrado['model'] == modelo]
            if not dados_m.empty:
                atual = dados_m.iloc[-1]['price']
                minimo = dados_m['price'].min()
                delta = "MENOR PREÇO!" if atual <= minimo else f"+ R$ {atual - minimo:.2f}"
                
                cols[i].metric(label=f"📍 {modelo[:20]}...", value=f"R$ {atual:,.2f}", 
                               delta=delta, delta_color="normal" if atual > minimo else "inverse")

        st.markdown("---")

      
        df_recente = df_filtrado.sort_values('collectionDate').groupby(['model', 'store']).tail(1)
        
        fig_bar = px.bar(
            df_recente, 
            x='model', 
            y='price', 
            color='store',
            text_auto='.2f',
            title="Comparativo de Preços Atuais (R$)",
            template="plotly_dark"
        )
        fig_bar.update_yaxes(tickprefix="R$ ", autorange=True)
        st.plotly_chart(fig_bar, use_container_width=True)

     
        st.markdown("---")
        with st.expander("📋 Ver Histórico Completo de Coletas"):
            df_view = df_filtrado.sort_values(by='collectionDate', ascending=False).copy()
            df_view['price'] = df_view['price'].map('R$ {:,.2f}'.format)
            st.dataframe(df_view[['model', 'store', 'price', 'collectionDate']], use_container_width=True, hide_index=True)
    else:
        st.info("Selecione um produto para visualizar os dados.")
else:
    st.warning("Arquivo JSON não encontrado. Execute o coletor Java.")