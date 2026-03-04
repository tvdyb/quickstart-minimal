import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useUserStore } from '../stores/userStore';
import { useToast } from '../stores/toastStore';
import { getPool, getPositions, getOracle } from '../services/umbraApi';
import PoolStats from '../components/PoolStats';
import SupplySection from '../components/SupplySection';
import BorrowSection from '../components/BorrowSection';
import MyPositions from '../components/MyPositions';

const LendPage: React.FC = () => {
  const toast = useToast();
  const { user } = useUserStore();
  const trader = user?.party || user?.name || '';
  const [pool, setPool] = useState<any>({});
  const [positions, setPositions] = useState<any[]>([]);
  const [ccPrice, setCcPrice] = useState(100);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refresh = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    const load = async () => {
      try {
        setPool(await getPool());
      } catch (e: any) {
        console.error(e);
        setLoadError(e?.response?.data?.error || e?.message || 'Failed to load pool');
      }
      try {
        const o = await getOracle();
        setCcPrice(o.ccPrice || o.price || 100);
      } catch (e: any) {
        console.error(e);
        setLoadError(e?.response?.data?.error || e?.message || 'Failed to load oracle');
      }
      if (trader) {
        try {
          const p = await getPositions(trader);
          const merged = [
            ...(Array.isArray(p?.supply) ? p.supply : []),
            ...(Array.isArray(p?.borrow) ? p.borrow : []),
          ];
          setPositions(merged);
        } catch (e: any) {
          console.error(e);
          setLoadError(e?.response?.data?.error || e?.message || 'Failed to load positions');
        }
      }
    };
    void load();
    const iv = setInterval(load, 3000);
    return () => clearInterval(iv);
  }, [trader, tick]);

  useEffect(() => {
    if (loadError) {
      toast.displayWarning(`Lending page: ${loadError}`);
    }
  }, [loadError, toast]);

  if (!user) {
    return (
      <section className="page-section">
        <h1 className="page-title">Umbra Lending</h1>
        <p className="page-subtitle">
          Please log in to access lending. Use <Link to="/debug">Debug / Ledger</Link> if data is unavailable.
        </p>
      </section>
    );
  }

  const supplyPositions = (Array.isArray(positions) ? positions : []).filter((p: any) => p.type === 'supply');

  return (
    <section className="page-section">
      <div className="page-head">
        <h1 className="page-title">Umbra Lending</h1>
        <p className="page-subtitle">Supply and borrow against Canton Network assets.</p>
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
        <div className="debug-col-12">
          <PoolStats
            totalSupplied={pool.totalSupplied || 0}
            totalBorrowed={pool.totalBorrowed || 0}
            utilization={pool.utilization || 0}
            supplyApy={pool.supplyApy || 0}
            borrowApy={pool.borrowApy || 0}
            ccPrice={ccPrice}
          />
        </div>
        <div className="debug-col-6">
          <SupplySection trader={trader} positions={supplyPositions} onAction={refresh} />
        </div>
        <div className="debug-col-6">
          <BorrowSection trader={trader} ccPrice={ccPrice} onAction={refresh} />
        </div>
        <div className="debug-col-12">
          <MyPositions positions={Array.isArray(positions) ? positions : []} />
        </div>
      </div>
    </section>
  );
};

export default LendPage;
