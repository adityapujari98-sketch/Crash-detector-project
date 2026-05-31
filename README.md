# Crash Detector Project

Full-stack crash and rebound detector inspired by the Streamlit prototype.

Images of Project - Production work in progress
<img width="866" height="788" alt="Screenshot 2026-05-26 122124" src="https://github.com/user-attachments/assets/14c010a2-ac76-4821-9f6b-cd0eabae7bfb" />
<img width="1378" height="812" alt="Screenshot 2026-05-23 233952" src="https://github.com/user-attachments/assets/fa16fcb9-a5b2-4533-a693-56d77131dc32" />



## Stack

- `frontend/`: React + Vite
- `backend/`: Java 21 + Spring Boot

## Features

- Multi-ticker analysis
- Configurable crash threshold
- Yahoo Finance daily OHLC data fetch
- Crash/rebound detection ported from the Streamlit reference
- Summary cards and per-ticker detail panels

## Repo Structure

```text
.
|-- backend
|   |-- pom.xml
|   `-- src/main
|       |-- java/com/crashdetector/api
|       `-- resources
|-- frontend
|   |-- package.json
|   |-- vite.config.js
|   `-- src
|-- .gitignore
`-- README.md
```

## Run Locally

### Backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs on `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

## API

`GET /api/analyze?tickers=AAPL,MSFT,NVDA&period=10y&threshold=0.15`

## Notes

- The current workspace did not have Node.js, npm, Java, or Maven available on PATH while scaffolding, so the project structure was created but not executed locally yet.
- The backend uses Yahoo Finance's chart endpoint at runtime to fetch daily candles.

