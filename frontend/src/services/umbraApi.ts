import axios from 'axios';

const umbra = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

// Orderbook
export const getOrderbook = () => umbra.get('/orderbook').then(r => r.data);
export const getMyOrders = () => umbra.get('/orders/mine').then(r => r.data);
export const placeOrder = (order: {
  trader?: string;
  side: 'buy' | 'sell' | 'Buy' | 'Sell';
  price: number;
  quantity: number;
  baseAsset?: string;
  quoteAsset?: string;
}) =>
  umbra.post('/orders', order).then(r => r.data);
export const cancelOrder = (id: string) => umbra.delete(`/orders/${id}`).then(r => r.data);

// Trades
export const getTrades = (trader: string) => umbra.get(`/trades/${trader}`).then(r => r.data);

// Pool
export const getPool = () => umbra.get('/pool').then(r => r.data);
export const supply = (data: { supplier?: string; trader?: string; amount: number }) =>
  umbra.post('/pool/supply', data).then(r => r.data);
export const borrow = (data: {
  borrower?: string;
  trader?: string;
  collateralAmount?: number;
  collateral?: number;
  borrowAmount?: number;
  amount?: number;
  oracleCid?: string;
  collateralOracleCid?: string;
}) => umbra.post('/pool/borrow', data).then(r => r.data);
export const repay = (data: {
  borrower?: string;
  trader?: string;
  contractId?: string;
  positionId?: string;
  repayAmount?: number;
  amount?: number;
}) => umbra.post('/pool/repay', data).then(r => r.data);

// Positions
export const getPositions = (trader?: string) =>
  trader ? umbra.get(`/positions/${trader}`).then(r => r.data) : umbra.get('/positions/me').then(r => r.data);

// Oracle
export const getOracle = () => umbra.get('/oracle').then(r => r.data);
