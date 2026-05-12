const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

function buildMockTicker(ticker, threshold) {
  const baseDate = new Date('2024-01-02T00:00:00Z');
  const closes = [
    186, 189, 191, 194, 197, 201, 198, 193, 188, 176, 169, 162,
    158, 154, 157, 161, 166, 171, 176, 181, 184, 188, 192, 195,
  ];

  const crashStart = 9;
  const reboundIndex = 16;

  const candles = closes.map((close, index) => {
    const currentDate = new Date(baseDate);
    currentDate.setUTCDate(baseDate.getUTCDate() + index * 7);

    return {
      date: currentDate.toISOString().slice(0, 10),
      open: close - 2.4,
      high: close + 3.2,
      low: close - 4.1,
      close,
      label: index === reboundIndex ? 1 : 0,
    };
  });

  const crashDate = candles[crashStart].date;
  const reboundDate = candles[reboundIndex].date;
  candles[crashStart].label = -1;

  return {
    ticker,
    summary: {
      crashCount: 1,
      reboundCount: 1,
      averageRecoveryDays: 49,
    },
    candles,
    windows: [
      {
        crashDate,
        reboundDate,
        daysToRebound: 49,
        maxDrawdownPct: -20.5,
        recoveryPct: 7.8,
        status: `Recovered (${Math.round(threshold * 100)}% threshold demo)`,
      },
    ],
  };
}

function buildMockResponse({ tickers, threshold }) {
  const tickerList = tickers
    .split(',')
    .map((value) => value.trim().toUpperCase())
    .filter(Boolean)
    .slice(0, 3);

  const fallbackTickers = tickerList.length ? tickerList : ['AAPL', 'MSFT', 'NVDA'];

  return {
    tickers: fallbackTickers.map((ticker) => buildMockTicker(ticker, threshold)),
    errors: [],
    meta: {
      source: 'mock',
      message: 'Backend unavailable. Showing built-in demo data until the Java API is running.',
    },
  };
}

export async function analyzeTickers({ tickers, period, threshold }) {
  const params = new URLSearchParams({
    tickers,
    period,
    threshold: String(threshold),
  });

  try {
    const response = await fetch(`${API_BASE_URL}/api/analyze?${params.toString()}`);
    if (!response.ok) {
      throw new Error(`API request failed with status ${response.status}`);
    }

    const payload = await response.json();
    return {
      ...payload,
      meta: {
        source: 'api',
        message: '',
      },
    };
  } catch (error) {
    return buildMockResponse({ tickers, threshold });
  }
}
