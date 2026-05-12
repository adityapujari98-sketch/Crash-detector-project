import streamlit as st
import yfinance as yf
import pandas as pd
import numpy as np
import plotly.graph_objects as go

st.set_page_config(page_title="Crash & Rebound Detector", layout="wide")

# Custom CSS
st.markdown("""
<style>
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;600&family=IBM+Plex+Sans:wght@300;400;600&display=swap');

  html, body, [class*="css"] {
    font-family: 'IBM Plex Sans', sans-serif;
    background-color: #0d0f14;
    color: #e2e8f0;
  }
  .stApp { background-color: #0d0f14; }

  h1 { font-family: 'IBM Plex Mono', monospace; color: #f8fafc; letter-spacing: 0; }
  h3 { font-family: 'IBM Plex Mono', monospace; color: #94a3b8; font-size: 0.85rem; font-weight: 400; text-transform: uppercase; letter-spacing: 2px; }

  .metric-card {
    background: #161b26;
    border: 1px solid #1e2736;
    border-radius: 8px;
    padding: 1rem 1.25rem;
    text-align: center;
  }
  .metric-value { font-family: 'IBM Plex Mono', monospace; font-size: 1.6rem; font-weight: 600; }
  .metric-label { font-size: 0.75rem; color: #64748b; text-transform: uppercase; letter-spacing: 1px; margin-top: 2px; }
  .crash-val  { color: #f87171; }
  .rebound-val{ color: #34d399; }
  .neutral-val{ color: #60a5fa; }

  .legend-pill {
    display: inline-block;
    padding: 3px 12px;
    border-radius: 20px;
    font-size: 0.75rem;
    font-family: 'IBM Plex Mono', monospace;
    margin-right: 8px;
  }
  .pill-crash   { background: rgba(248,113,113,0.15); color: #f87171; border: 1px solid rgba(248,113,113,0.3); }
  .pill-rebound { background: rgba(52,211,153,0.15);  color: #34d399; border: 1px solid rgba(52,211,153,0.3); }

  div[data-testid="stSlider"] label { color: #94a3b8; }
  div[data-testid="stTextInput"] label { color: #94a3b8; }
</style>
""", unsafe_allow_html=True)


def calculate_volatility(prices: pd.Series, window: int = 20):
    """
    Calculate rolling volatility (standard deviation of log returns).
    """
    log_returns = np.log(prices / prices.shift(1))
    volatility = log_returns.rolling(window=window).std()
    return volatility


def label_crashes_and_rebounds(prices: pd.Series, w: float = 0.15, volatility_window: int = 20):
    """
    Implements trend labeling with volatility-based rebound detection.
    Labels turning points in a price series:
      +1 -> rebound (when volatility stabilizes after crash)
      -1 -> crash onset (w% drop from local peak)
       0 -> normal
    """
    prices = prices.dropna()
    price_values = prices.values
    total_points = len(price_values)
    labels = np.zeros(total_points, dtype=int)

    if total_points == 0:
        return pd.Series(labels, index=prices.index)

    # Calculate volatility
    volatility = calculate_volatility(prices, window=volatility_window)
    volatility_values = volatility.values
    
    # Calculate baseline volatility (pre-crash normal volatility)
    pre_crash_volatility = np.nanmean(volatility_values[:max(50, total_points // 10)])
    volatility_threshold = pre_crash_volatility * 1.5  # Rebound when volatility drops to 1.5x baseline

    local_peak = price_values[0]
    local_trough = price_values[0]
    trend = 0
    start_index = 0
    crash_volatility = None

    for i in range(total_points):
        if price_values[i] > local_peak * (1 + w):
            local_peak = price_values[i]
            trend = 1
            start_index = i
            labels[i] = -1
            crash_volatility = volatility_values[i]
            break
        if price_values[i] < local_trough * (1 - w):
            local_trough = price_values[i]
            trend = -1
            start_index = i
            labels[i] = 1
            crash_volatility = volatility_values[i]
            break

    for i in range(start_index, total_points):
        if trend == 1:
            if price_values[i] > local_peak:
                local_peak = price_values[i]
            if price_values[i] < local_peak * (1 - w):
                local_trough = price_values[i]
                labels[i] = -1
                trend = -1
                crash_volatility = volatility_values[i]
        elif trend == -1:
            if price_values[i] < local_trough:
                local_trough = price_values[i]
            # Rebound when volatility stabilizes below threshold
            if not np.isnan(volatility_values[i]) and volatility_values[i] < volatility_threshold:
                local_peak = price_values[i]
                labels[i] = 1
                trend = 1

    return pd.Series(labels, index=prices.index)


def find_crash_windows(labels: pd.Series):
    """
    Returns list of (crash_date, rebound_date) pairs.
    A crash window opens on a -1 label and closes on the next +1 label.
    """
    windows = []
    crash_date = None

    for date, label in labels.items():
        if label == -1:
            crash_date = date
        elif label == 1 and crash_date is not None:
            windows.append((crash_date, date))
            crash_date = None

    if crash_date is not None:
        windows.append((crash_date, None))

    return windows


st.title("Crash & Rebound Detector")
st.markdown("*Methodology from Xiu, Wang & Chan (2021) - NYSE Composite Index paper*")
st.markdown("---")

col_input, col_thresh, col_period = st.columns([2, 1, 1])

with col_input:
    raw = st.text_input(
        "Stock tickers (comma-separated)",
        "AAPL, MSFT, NVDA",
        help="e.g. AAPL, TSLA, SPY",
    )
    tickers = [ticker.strip().upper() for ticker in raw.split(",") if ticker.strip()]

with col_thresh:
    w = st.slider(
        "Threshold w",
        min_value=0.05,
        max_value=0.40,
        value=0.15,
        step=0.01,
        help=(
            "Paper uses w=0.15 (15%). A crash is flagged when price drops "
            "w% from a local peak. Rebound is detected when volatility stabilizes "
            "(drops below 1.5x baseline volatility)."
        ),
    )

with col_period:
    period = st.selectbox("Period", ["5y", "10y", "15y", "20y", "max"], index=1)

if not tickers:
    st.warning("Please enter at least one ticker.")
    st.stop()

all_data = {}
errors = []

with st.spinner("Downloading price data..."):
    for ticker in tickers:
        raw_df = yf.download(
            ticker,
            period=period,
            interval="1d",
            auto_adjust=False,
            progress=False,
        )

        if raw_df.empty:
            errors.append(ticker)
            continue

        if isinstance(raw_df.columns, pd.MultiIndex):
            raw_df.columns = raw_df.columns.get_level_values(0)

        required = ["Open", "High", "Low", "Close"]
        if not all(column in raw_df.columns for column in required):
            errors.append(ticker)
            continue

        df = raw_df[required].dropna()
        if len(df) < 50:
            errors.append(ticker)
            continue

        labels = label_crashes_and_rebounds(df["Close"], w=w)
        windows = find_crash_windows(labels)
        all_data[ticker] = {"df": df, "labels": labels, "windows": windows}

if errors:
    st.warning(f"Could not load data for: {', '.join(errors)}")

if not all_data:
    st.error("No valid data found for any ticker.")
    st.stop()

st.markdown("### Summary")
summary_cols = st.columns(len(all_data))

for col, (ticker, data) in zip(summary_cols, all_data.items()):
    windows = data["windows"]
    n_crashes = len(windows)
    n_complete = sum(1 for _, rebound_date in windows if rebound_date is not None)

    durations = []
    for crash_date, rebound_date in windows:
        if rebound_date is not None:
            durations.append((rebound_date - crash_date).days)

    avg_days = int(np.mean(durations)) if durations else 0

    with col:
        st.markdown(f"""
        <div class="metric-card">
          <div class="metric-value neutral-val">{ticker}</div>
          <div style="margin-top:12px; display:flex; justify-content:space-around; gap:12px;">
            <div>
              <div class="metric-value crash-val">{n_crashes}</div>
              <div class="metric-label">Crashes</div>
            </div>
            <div>
              <div class="metric-value rebound-val">{n_complete}</div>
              <div class="metric-label">Rebounds</div>
            </div>
            <div>
              <div class="metric-value neutral-val">{avg_days}d</div>
              <div class="metric-label">Avg Recovery</div>
            </div>
          </div>
        </div>
        """, unsafe_allow_html=True)

st.markdown("---")

tab_labels = list(all_data.keys())
tabs = st.tabs(tab_labels)

for tab, ticker in zip(tabs, tab_labels):
    data = all_data[ticker]
    df = data["df"]
    labels = data["labels"]
    windows = data["windows"]

    rebound_dates = labels[labels == 1].index.tolist()

    with tab:
        st.markdown(
            f'<span class="legend-pill" style="background:rgba(250,204,21,0.15);color:#facc15;border:1px solid rgba(250,204,21,0.35);">Crash window</span>'
            f'<span class="legend-pill" style="background:rgba(96,165,250,0.15);color:#60a5fa;border:1px solid rgba(96,165,250,0.35);">Rebound</span>'
            f'&nbsp;<small style="color:#64748b;font-size:0.75rem">'
            f'w = {w:.2f} ({w * 100:.0f}% threshold)'
            f'</small>',
            unsafe_allow_html=True,
        )

        fig = go.Figure()

        for crash_date, rebound_date in windows:
            end = rebound_date if rebound_date is not None else df.index[-1]
            fig.add_vrect(
                x0=crash_date,
                x1=end,
                fillcolor="rgba(250,204,21,0.10)",
                line_color="rgba(250,204,21,0.30)",
                line_width=1,
            )

        fig.add_trace(go.Candlestick(
            x=df.index,
            open=df["Open"],
            high=df["High"],
            low=df["Low"],
            close=df["Close"],
            increasing_line_color="#34d399",
            decreasing_line_color="#f87171",
            increasing_fillcolor="rgba(52,211,153,0.55)",
            decreasing_fillcolor="rgba(248,113,113,0.55)",
            name="Price",
            showlegend=False,
        ))

        valid_rebound = [date for date in rebound_dates if date in df.index]
        if valid_rebound:
            rebound_prices = df.loc[valid_rebound, "Low"] * 0.982
            fig.add_trace(go.Scatter(
                x=rebound_prices.index,
                y=rebound_prices.values,
                mode="markers",
                marker=dict(
                    symbol="circle",
                    color="#60a5fa",
                    size=10,
                    line=dict(width=1.5, color="#1d4ed8"),
                ),
                name="Rebound",
                hovertemplate="<b>Rebound</b><br>%{x|%Y-%m-%d}<extra></extra>",
            ))

        fig.update_layout(
            height=560,
            template="plotly_dark",
            paper_bgcolor="#0d0f14",
            plot_bgcolor="#111827",
            xaxis=dict(
                rangeslider_visible=False,
                gridcolor="#1e2736",
                showgrid=True,
            ),
            yaxis=dict(
                gridcolor="#1e2736",
                title="Price (USD)",
                title_font_color="#64748b",
            ),
            legend=dict(
                orientation="h",
                yanchor="bottom",
                y=1.02,
                bgcolor="rgba(0,0,0,0)",
                font_color="#94a3b8",
            ),
            margin=dict(l=0, r=0, t=10, b=0),
            hovermode="x unified",
        )

        fig.update_xaxes(
            rangeselector=dict(
                buttons=[
                    dict(count=1, label="1Y", step="year", stepmode="backward"),
                    dict(count=3, label="3Y", step="year", stepmode="backward"),
                    dict(count=5, label="5Y", step="year", stepmode="backward"),
                    dict(step="all", label="All"),
                ],
                bgcolor="#161b26",
                activecolor="#334155",
                font_color="#94a3b8",
            )
        )

        st.plotly_chart(fig, use_container_width=True, key=f"chart_{ticker}")

        if windows:
            table_rows = []

            for crash_date, rebound_date in windows:
                crash_price = df.loc[crash_date, "Close"] if crash_date in df.index else np.nan

                if rebound_date is not None and rebound_date in df.index:
                    rebound_price = df.loc[rebound_date, "Close"]
                    duration = (rebound_date - crash_date).days
                    trough = df.loc[crash_date:rebound_date, "Close"].min()
                    max_drop = (trough - crash_price) / crash_price * 100
                    recovery = (rebound_price - trough) / trough * 100

                    table_rows.append({
                        "Crash Date": crash_date.strftime("%Y-%m-%d"),
                        "Rebound Date": rebound_date.strftime("%Y-%m-%d"),
                        "Days to Rebound": duration,
                        "Max Drawdown": f"{max_drop:.1f}%",
                        "Recovery": f"+{recovery:.1f}%",
                        "Status": "Recovered",
                    })
                else:
                    table_rows.append({
                        "Crash Date": crash_date.strftime("%Y-%m-%d"),
                        "Rebound Date": "-",
                        "Days to Rebound": "-",
                        "Max Drawdown": "-",
                        "Recovery": "-",
                        "Status": "Ongoing / No rebound yet",
                    })

            st.dataframe(
                pd.DataFrame(table_rows),
                use_container_width=True,
                hide_index=True,
            )

st.caption(
    "Algorithm: Wu et al. (2020) trend-labeling with w threshold. "
    "Crash = price drops w% from local peak. "
    "Rebound = detected when volatility stabilizes (drops below 1.5x baseline), "
    "not based on price recovery percentage."
)

