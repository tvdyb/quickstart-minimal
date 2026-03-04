import React, { useState } from 'react';
import { placeOrder } from '../services/umbraApi';

interface Props { trader: string; onPlaced: () => void; }

const OrderEntry: React.FC<Props> = ({ trader, onPlaced }) => {
  const [side, setSide] = useState<'buy' | 'sell'>('buy');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (!price || !quantity) return;
    setLoading(true);
    try {
      await placeOrder({ trader, side, price: parseFloat(price), quantity: parseFloat(quantity) });
      setPrice(''); setQuantity('');
      onPlaced();
    } catch (e) { console.error(e); }
    setLoading(false);
  };

  return (
    <div className="panel">
      <h3>Place Order</h3>
      <div className="d-flex gap-2 mb-3">
        {(['buy', 'sell'] as const).map(s => (
          <button key={s} onClick={() => setSide(s)} className={`btn flex-fill ${
              side === s
                ? s === 'buy' ? 'btn-success' : 'btn-danger'
                : 'btn-outline-secondary'
            }`}>
            {s.toUpperCase()}
          </button>
        ))}
      </div>
      <div className="mb-2">
        <label className="form-label">Price</label>
        <input type="number" placeholder="0.0000" value={price} onChange={e => setPrice(e.target.value)} className="form-control" />
      </div>
      <div className="mb-3">
        <label className="form-label">Quantity</label>
        <input type="number" placeholder="0.00" value={quantity} onChange={e => setQuantity(e.target.value)} className="form-control" />
      </div>
      <button onClick={submit} disabled={loading} className={`btn w-100 ${side === 'buy' ? 'btn-success' : 'btn-danger'}`}>
        {loading ? 'Submitting...' : `${side.toUpperCase()} CC`}
      </button>
    </div>
  );
};

export default OrderEntry;
