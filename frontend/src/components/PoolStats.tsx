import React from 'react';

interface Props {
  totalSupplied: number; totalBorrowed: number; utilization: number;
  supplyApy: number; borrowApy: number; ccPrice: number;
}

const PoolStats: React.FC<Props> = ({ totalSupplied, totalBorrowed, utilization, supplyApy, borrowApy, ccPrice }) => (
  <div className="panel">
    <h3>Pool Overview</h3>
    <div className="metrics-row mb-3">
      <Stat label="Total Supplied" value={`$${totalSupplied.toLocaleString()}`} />
      <Stat label="Total Borrowed" value={`$${totalBorrowed.toLocaleString()}`} />
      <Stat label="CC Price" value={`$${ccPrice.toFixed(4)}`} />
    </div>
    <div className="mb-4">
      <div className="d-flex justify-content-between small text-muted mb-1">
        <span>Utilization</span><span>{(utilization * 100).toFixed(1)}%</span>
      </div>
      <div style={{ height: 9, background: '#e6edf8', borderRadius: 999, overflow: 'hidden' }}>
        <div style={{ height: 9, width: `${utilization * 100}%`, background: '#2f6df6' }} />
      </div>
    </div>
    <div className="metrics-row">
      <Stat label="Supply APY" value={`${(supplyApy * 100).toFixed(2)}%`} tone="ok" />
      <Stat label="Borrow APY" value={`${(borrowApy * 100).toFixed(2)}%`} tone="warn" />
      <Stat label="TVL" value={`$${(totalSupplied - totalBorrowed).toLocaleString()}`} />
    </div>
  </div>
);

const Stat: React.FC<{ label: string; value: string; tone?: 'ok' | 'warn' }> = ({ label, value, tone }) => (
  <div className="metric-box">
    <div className="metric-label">{label}</div>
    <div className="metric-value mono" style={{ color: tone === 'ok' ? '#166f3f' : tone === 'warn' ? '#8a5606' : undefined }}>
      {value}
    </div>
  </div>
);

export default PoolStats;
