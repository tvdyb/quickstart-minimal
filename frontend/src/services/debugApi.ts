import axios from 'axios';

export interface LedgerDiagnostics {
  timestamp: string;
  authMode: string;
  identity: any;
  backend: any;
  health: any;
  ledger: any;
  templates: string[];
  activeContracts: Array<{
    template: string;
    activeCount: number | null;
    sample: Array<{ contractId: string; payload: any }>;
    error?: string;
  }>;
  recentEvents: any[];
  latencyMs: number;
}

const debugHttp = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

export const getLedgerDiagnostics = async (sampleLimit = 5, eventLimit = 20): Promise<LedgerDiagnostics> => {
  const response = await debugHttp.get('/debug/ledger', {
    params: { sampleLimit, eventLimit }
  });
  return response.data;
};
