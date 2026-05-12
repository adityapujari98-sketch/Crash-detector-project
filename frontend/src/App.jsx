import { useEffect, useState } from 'react';
import Plot from 'react-plotly.js';
import { analyzeTickers } from './api';

const periods = ['5y', '10y', '15y', '20y', 'max'];

const initialForm = {
  tickers: 'AAPL, MSFT, NVDA',
  period: '10y',
  threshold: 0.15,
};

function formatPercent(value) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '-';
  }
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`;
}

function buildCrashWindowShapes(windows, candles) {
  const fallbackEnd = candles[candles.length - 1]?.date;

  return windows.map((window) => ({
    type: 'rect',
    xref: 'x',
    yref: 'paper',
    x0: window.crashDate,
    x1: window.reboundDate ?? fallbackEnd,
    y0: 0,
    y1: 1,
    fillcolor: 'rgba(250,204,21,0.10)',
    line: {
      color: 'rgba(250,204,21,0.30)',
      width: 1,
    },
    layer: 'below',
  }));
}

function buildReboundTrace(candles) {
  const reboundCandles = candles.filter((candle) => candle.label === 1);
  if (!reboundCandles.length) {
    return null;
  }

  return {
    type: 'scatter',
    mode: 'markers',
    x: reboundCandles.map((candle) => candle.date),
    y: reboundCandles.map((candle) => candle.low * 0.982),
    marker: {
      symbol: 'circle',
      color: '#60a5fa',
      size: 10,
      line: {
        width: 1.5,
        color: '#1d4ed8',
      },
    },
    name: 'Rebound',
    hovertemplate: '<b>Rebound</b><br>%{x}<extra></extra>',
  };
}

function buildPlotData(candles) {
  const traces = [
    {
      type: 'candlestick',
      x: candles.map((candle) => candle.date),
      open: candles.map((candle) => candle.open),
      high: candles.map((candle) => candle.high),
      low: candles.map((candle) => candle.low),
      close: candles.map((candle) => candle.close),
      name: 'Price',
      showlegend: false,
      increasing: {
        line: { color: '#34d399' },
        fillcolor: 'rgba(52,211,153,0.55)',
      },
      decreasing: {
        line: { color: '#f87171' },
        fillcolor: 'rgba(248,113,113,0.55)',
      },
    },
  ];

  const reboundTrace = buildReboundTrace(candles);
  if (reboundTrace) {
    traces.push(reboundTrace);
  }

  return traces;
}

function buildTableRows(windows) {
  return windows.map((window) => ({
    crashDate: window.crashDate,
    reboundDate: window.reboundDate ?? '-',
    daysToRebound: window.daysToRebound ?? '-',
    maxDrawdownPct: formatPercent(window.maxDrawdownPct),
    recoveryPct: formatPercent(window.recoveryPct),
    status: window.status,
  }));
}

function App() {
  const [form, setForm] = useState(initialForm);
  const [data, setData] = useState({ tickers: [], errors: [], meta: { source: 'api', message: '' } });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTicker, setActiveTicker] = useState('');

  useEffect(() => {
    handleAnalyze(initialForm);
  }, []);

  async function handleAnalyze(nextForm = form) {
    try {
      setLoading(true);
      setError('');
      const response = await analyzeTickers(nextForm);
      setData(response);
      setActiveTicker(response.tickers[0]?.ticker ?? '');
    } catch (requestError) {
      setError(requestError.message);
      setData({ tickers: [], errors: [], meta: { source: 'api', message: '' } });
    } finally {
      setLoading(false);
    }
  }

  const selectedTicker = data.tickers.find((item) => item.ticker === activeTicker) ?? data.tickers[0];
  const plotData = selectedTicker ? buildPlotData(selectedTicker.candles) : [];
  const tableRows = selectedTicker ? buildTableRows(selectedTicker.windows) : [];
  const plotLayout = selectedTicker
    ? {
        height: 560,
        template: 'plotly_dark',
        paper_bgcolor: '#0d0f14',
        plot_bgcolor: '#111827',
        margin: { l: 0, r: 0, t: 10, b: 0 },
        hovermode: 'x unified',
        showlegend: true,
        legend: {
          orientation: 'h',
          yanchor: 'bottom',
          y: 1.02,
          x: 0,
          bgcolor: 'rgba(0,0,0,0)',
          font: { color: '#94a3b8' },
        },
        shapes: buildCrashWindowShapes(selectedTicker.windows, selectedTicker.candles),
        xaxis: {
          rangeslider: { visible: false },
          gridcolor: '#1e2736',
          showgrid: true,
          rangeselector: {
            buttons: [
              { count: 1, label: '1Y', step: 'year', stepmode: 'backward' },
              { count: 3, label: '3Y', step: 'year', stepmode: 'backward' },
              { count: 5, label: '5Y', step: 'year', stepmode: 'backward' },
              { step: 'all', label: 'All' },
            ],
            bgcolor: '#161b26',
            activecolor: '#334155',
            font: { color: '#94a3b8' },
          },
          tickfont: { color: '#94a3b8' },
          zeroline: false,
        },
        yaxis: {
          gridcolor: '#1e2736',
          title: {
            text: 'Price (USD)',
            font: { color: '#64748b' },
          },
          tickfont: { color: '#94a3b8' },
          zeroline: false,
        },
      }
    : undefined;

  return (
    <div className="app-shell">
      <header className="hero">
        <h1>Crash &amp; Rebound Detector</h1>
        <p className="methodology">Methodology from Xiu, Wang &amp; Chan (2021) - NYSE Composite Index paper</p>
      </header>

      <div className="rule" />

      <section className="controls-panel">
        <label className="field field-grow">
          <span>Stock tickers (comma-separated)</span>
          <input
            value={form.tickers}
            onChange={(event) => setForm((current) => ({ ...current, tickers: event.target.value }))}
            placeholder="AAPL, MSFT, NVDA"
          />
        </label>

        <label className="field">
          <span>Threshold w</span>
          <div className="range-stack">
            <input
              type="range"
              min="0.05"
              max="0.40"
              step="0.01"
              value={form.threshold}
              onChange={(event) =>
                setForm((current) => ({ ...current, threshold: Number(event.target.value) }))
              }
            />
            <strong>{form.threshold.toFixed(2)}</strong>
          </div>
        </label>

        <label className="field">
          <span>Period</span>
          <select
            value={form.period}
            onChange={(event) => setForm((current) => ({ ...current, period: event.target.value }))}
          >
            {periods.map((period) => (
              <option key={period} value={period}>
                {period}
              </option>
            ))}
          </select>
        </label>

        <button className="primary-button" type="button" onClick={() => handleAnalyze()}>
          Analyze
        </button>
      </section>

      {error ? <div className="message error">{error}</div> : null}
      {data.meta?.message ? <div className="message info">{data.meta.message}</div> : null}
      {data.errors?.length ? (
        <div className="message warning">Could not load data for: {data.errors.join(', ')}</div>
      ) : null}

      <h3 className="section-title">Summary</h3>
      <section className="summary-grid">
        {data.tickers.map((ticker) => (
          <div key={ticker.ticker} className="summary-card">
            <div className="summary-title neutral-val">{ticker.ticker}</div>
            <div className="summary-metrics">
              <div>
                <strong className="metric crash">{ticker.summary.crashCount}</strong>
                <span>Crashes</span>
              </div>
              <div>
                <strong className="metric rebound">{ticker.summary.reboundCount}</strong>
                <span>Rebounds</span>
              </div>
              <div>
                <strong className="metric neutral">{ticker.summary.averageRecoveryDays}d</strong>
                <span>Avg Recovery</span>
              </div>
            </div>
          </div>
        ))}
      </section>

      <div className="rule" />

      <section className="tabs-shell">
        <div className="tab-row" role="tablist" aria-label="Tickers">
          {data.tickers.map((ticker) => (
            <button
              key={ticker.ticker}
              type="button"
              className={`tab-button ${activeTicker === ticker.ticker ? 'is-active' : ''}`}
              onClick={() => setActiveTicker(ticker.ticker)}
              role="tab"
              aria-selected={activeTicker === ticker.ticker}
            >
              {ticker.ticker}
            </button>
          ))}
        </div>

        {selectedTicker ? (
          <div className="tab-panel" role="tabpanel">
            <div className="chart-toolbar">
              <span className="legend-pill warning">Crash window</span>
              <span className="legend-pill info">Rebound</span>
              <span className="threshold-copy">
                w = {form.threshold.toFixed(2)} ({(form.threshold * 100).toFixed(0)}% threshold)
              </span>
            </div>

            <div className="chart-wrap">
              {loading ? (
                <div className="empty-state">Loading analysis...</div>
              ) : (
                <Plot
                  className="market-plot"
                  data={plotData}
                  layout={plotLayout}
                  config={{
                    displayModeBar: false,
                    responsive: true,
                  }}
                  useResizeHandler
                  style={{ width: '100%', height: '560px' }}
                />
              )}
            </div>

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Crash Date</th>
                    <th>Rebound Date</th>
                    <th>Days to Rebound</th>
                    <th>Max Drawdown</th>
                    <th>Recovery</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {tableRows.length ? (
                    tableRows.map((row, index) => (
                      <tr key={`${row.crashDate}-${index}`}>
                        <td>{row.crashDate}</td>
                        <td>{row.reboundDate}</td>
                        <td>{row.daysToRebound}</td>
                        <td>{row.maxDrawdownPct}</td>
                        <td>{row.recoveryPct}</td>
                        <td>{row.status}</td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan="6" className="empty-row">
                        No crash windows found.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          <div className="empty-state">No valid ticker data found.</div>
        )}
      </section>

      <p className="caption">
        Algorithm: Wu et al. (2020) trend-labeling with w threshold. Crash = price drops w% from local peak.
        Rebound = detected when volatility stabilizes (drops below 1.5x baseline), not based on price recovery percentage.
      </p>
    </div>
  );
}

export default App;
