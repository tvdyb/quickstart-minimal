import React from 'react';

interface Level { price: number; quantity: number; }
interface Props { bids: Level[]; asks: Level[]; }

const Orderbook: React.FC<Props> = ({ bids, asks }) => {
  const maxQty = Math.max(...[...bids, ...asks].map((l) => l.quantity), 1);
  return (
    <div className="panel">
      <h3>Orderbook</h3>
      <div className="page-subtitle mb-2">Live aggregated levels. Green = bids, red = asks.</div>
      {bids.length === 0 && asks.length === 0 ? (
        <div className="empty-state">No orders yet. Place the first order to populate depth.</div>
      ) : (
      <>
      <div className="umbra-orderbook-head">
        <div><span>Bid Price</span><span>Qty</span></div>
        <div><span>Ask Price</span><span>Qty</span></div>
      </div>
      <div className="umbra-orderbook-grid">
        <div className="umbra-orderbook-side">
          {bids.slice(0, 12).map((l, i) => (
            <div key={`bid-${i}`} className="umbra-level-row">
              <div className="umbra-level-bar bid" style={{ width: `${(l.quantity / maxQty) * 100}%` }} />
              <span className="mono text-success">{l.price.toFixed(4)}</span>
              <span className="mono text-end">{l.quantity.toFixed(2)}</span>
            </div>
          ))}
        </div>
        <div className="umbra-orderbook-side">
          {asks.slice(0, 12).map((l, i) => (
            <div key={`ask-${i}`} className="umbra-level-row">
              <div className="umbra-level-bar ask" style={{ width: `${(l.quantity / maxQty) * 100}%` }} />
              <span className="mono text-danger">{l.price.toFixed(4)}</span>
              <span className="mono text-end">{l.quantity.toFixed(2)}</span>
            </div>
          ))}
        </div>
      </div>
      </>
      )}
    </div>
  );
};

export default Orderbook;
