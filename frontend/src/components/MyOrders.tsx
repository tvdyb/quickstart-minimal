import React from 'react';
import { cancelOrder } from '../services/umbraApi';
import { useToast } from '../stores/toastStore';

interface Order { id: string; contractId?: string; side: string; price: number; quantity: number; }
interface Props { orders: Order[]; onCancelled: () => void; }

const MyOrders: React.FC<Props> = ({ orders, onCancelled }) => {
  const toast = useToast();
  const cancel = async (id: string) => {
    try {
      await cancelOrder(id);
      onCancelled();
      toast.displaySuccess('Order cancelled.');
    } catch (e: any) {
      console.error(e);
      const message = e?.response?.data?.error || e?.response?.data?.message || e?.message || 'Cancel failed';
      toast.displayError(message);
    }
  };

  return (
    <div className="panel">
      <h3>My Orders</h3>
      {orders.length === 0 ? (
        <div className="empty-state">No active orders yet.</div>
      ) : (
        <div className="table-responsive">
          <table className="table table-modern">
            <thead>
              <tr>
                <th>Side</th>
                <th>Price</th>
                <th>Qty</th>
                <th />
              </tr>
            </thead>
            <tbody>
          {orders.map(o => (
            <tr key={o.id}>
              <td>
              <span className={(o.side || '').toLowerCase() === 'buy' ? 'status-chip status-ok' : 'status-chip status-fail'}>
                {(o.side || '').toUpperCase()}
              </span>
              </td>
              <td className="mono">{o.price.toFixed(4)}</td>
              <td className="mono">{o.quantity.toFixed(2)}</td>
              <td>
                <button onClick={() => cancel(o.contractId || o.id)} className="btn btn-sm btn-outline-danger">Cancel</button>
              </td>
            </tr>
          ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default MyOrders;
