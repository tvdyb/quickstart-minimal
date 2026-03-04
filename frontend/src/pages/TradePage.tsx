import React, { useState, useEffect, useCallback } from 'react';
import { useUserStore } from '../stores/userStore';
import { getOrderbook, getTrades, getMyOrders } from '../services/umbraApi';
import Orderbook from '../components/Orderbook';
import OrderEntry from '../components/OrderEntry';
import MyOrders from '../components/MyOrders';
import RecentTrades from '../components/RecentTrades';
import PriceChart from '../components/PriceChart';
import { useToast } from '../stores/toastStore';
import { Link } from 'react-router-dom';

const TradePage: React.FC = () => {
  const toast = useToast();
  const { user } = useUserStore();
  const trader = user?.party || user?.name || '';
  const [orderbook, setOrderbook] = useState<any>({ bids: [], asks: [] });
  const [myOrders, setMyOrders] = useState<any[]>([]);
  const [trades, setTrades] = useState<any[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refresh = useCallback(() => setTick(t => t + 1), []);

  useEffect(() => {
    const load = async () => {
      try {
        const ob = await getOrderbook();
        setOrderbook({
          bids: ob.bids || ob.buys || [],
          asks: ob.asks || ob.sells || [],
        });
      } catch (e: any) {
        console.error(e);
        const msg = e?.response?.data?.error || e?.message || 'Failed to load orderbook';
        setLoadError(msg);
      }

      try {
        const mine = await getMyOrders();
        setMyOrders(Array.isArray(mine) ? mine : []);
      } catch (e: any) {
        console.error(e);
        const msg = e?.response?.data?.error || e?.message || 'Failed to load orders';
        setLoadError(msg);
      }

      if (trader) {
        try {
          setTrades(await getTrades(trader));
        } catch (e: any) {
          console.error(e);
          const msg = e?.response?.data?.error || e?.message || 'Failed to load trades';
          setLoadError(msg);
        }
      }
    };
    void load();
    const iv = setInterval(load, 2500);
    return () => clearInterval(iv);
  }, [trader, tick]);

  useEffect(() => {
    if (loadError) {
      toast.displayWarning(`Trade page: ${loadError}`);
    }
  }, [loadError, toast]);

  if (!user) {
    return (
      <section className="page-section">
        <h1 className="page-title">Umbra Dark Pool</h1>
        <p className="page-subtitle">
          Please log in to trade. If login fails, open <Link to="/debug">Debug / Ledger</Link>.
        </p>
      </section>
    );
  }

  return (
    <section className="page-section">
      <div className="page-head">
        <h1 className="page-title">Umbra Dark Pool</h1>
        <p className="page-subtitle">Private orderbook trading on Canton Network</p>
      </div>
      {loadError && (
        <div className="diagnostic-alert mb-3">
          <div className="diagnostic-alert-head">
            <h3 className="diagnostic-alert-title">Data Loading Warning</h3>
            <span className="status-chip status-warn">Degraded</span>
          </div>
          <p className="diagnostic-alert-body">{loadError}</p>
          <div className="action-row">
            <button className="btn btn-sm btn-primary" onClick={refresh}>Retry</button>
            <Link to="/debug" className="btn btn-sm btn-outline-primary">Open Debug</Link>
          </div>
        </div>
      )}
      <div className="debug-grid">
          <div className="debug-col-8">
            <PriceChart trades={trades} />
            <div className="mt-3">
              <Orderbook bids={orderbook.bids || []} asks={orderbook.asks || []} />
            </div>
          </div>
          <div className="debug-col-4">
            <OrderEntry trader={trader} onPlaced={refresh} />
            <div className="mt-3">
              <MyOrders orders={myOrders} onCancelled={refresh} />
            </div>
          </div>
          <div className="debug-col-12">
            <RecentTrades trades={trades} />
          </div>
        </div>
    </section>
  );
};

export default TradePage;
