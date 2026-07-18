import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { AppSessionProvider } from './contexts/AppSessionContext';
import './styles/global.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppSessionProvider>
      <App />
    </AppSessionProvider>
  </React.StrictMode>,
);
