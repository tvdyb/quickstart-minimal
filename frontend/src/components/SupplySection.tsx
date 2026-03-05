import React, { useState } from 'react';
import { supply } from '../services/umbraApi';
import { useToast } from '../stores/toastStore';

interface Position { id: string; amount: number; }
interface Props { trader: string; positions: Position[]; onAction: () => void; }

const SupplySection: React.FC<Props> = ({ trader, positions, onAction }) => {
  const toast = useToast();
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);

  const doSupply = async () => {
    if (!trader) {
      toast.displayError('No authenticated party found.');
      return;
    }
    const parsed = parseFloat(amount);
    if (!amount || !isFinite(parsed) || parsed <= 0) {
      toast.displayWarning('Supply amount must be a positive number.');
      return;
    }
    setLoading(true);
    try {
      await supply({ supplier: trader, amount: parsed });
      toast.displaySuccess('Supply submitted.');
      setAmount('');
      onAction();
    }
    catch (e: any) {
      console.error(e);
      const message = e?.response?.data?.error || e?.response?.data?.message || e?.message || 'Supply failed';
      toast.displayError(message);
    }
    setLoading(false);
  };

  return (
    <div className="panel">
      <h3>Supply USDCx</h3>
      <div className="d-flex gap-2 mb-3">
        <input type="number" placeholder="Amount" value={amount} onChange={e => setAmount(e.target.value)} className="form-control" />
        <button onClick={doSupply} disabled={loading} className="btn btn-success">
          {loading ? 'Submitting...' : 'Supply'}
        </button>
      </div>
      {positions.length > 0 && (
        <div className="table-responsive">
          <table className="table table-modern">
            <thead>
              <tr>
                <th>Position ID</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
          {positions.map(p => (
            <tr key={p.id}>
              <td className="mono">{p.id}</td>
              <td className="mono">${p.amount.toLocaleString()}</td>
              <td><span className="status-chip status-ok">Active</span></td>
            </tr>
          ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default SupplySection;
