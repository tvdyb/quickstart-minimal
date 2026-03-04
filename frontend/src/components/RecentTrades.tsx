import React from 'react';

interface Trade {
  id?: string;
  price: number;
  quantity: number;
  executedAt?: string;
  buyer?: string;
  seller?: string;
}
interface Props { trades: Trade[]; }

const RecentTrades: React.FC<Props> = ({ trades }) => (
  <div className="panel">
    <h3>Recent Trades</h3>
    {trades.length === 0 ? (
      <div className="empty-state">No trades yet. Matched orders will appear here.</div>
    ) : (
      <div className="table-responsive">
      <table className="table table-modern">
        <thead>
          <tr>
            <th>Price</th>
            <th>Qty</th>
            <th>Buyer</th>
            <th>Seller</th>
            <th>Executed</th>
          </tr>
        </thead>
        <tbody>
        {trades.slice(0, 20).map((t, i) => (
          <tr key={t.id ?? i}>
            <td className="mono">{t.price.toFixed(4)}</td>
            <td className="mono">{t.quantity.toFixed(2)}</td>
            <td className="mono">{t.buyer ?? '-'}</td>
            <td className="mono">{t.seller ?? '-'}</td>
            <td className="mono">{t.executedAt ? new Date(t.executedAt).toLocaleTimeString() : '-'}</td>
          </tr>
        ))}
        </tbody>
      </table>
      </div>
    )}
  </div>
);

export default RecentTrades;
