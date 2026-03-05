import React, { useState } from 'react';
import { borrow } from '../services/umbraApi';
import { useToast } from '../stores/toastStore';

interface Props { trader: string; ccPrice: number; onAction: () => void; }

const BorrowSection: React.FC<Props> = ({ trader, ccPrice, onAction }) => {
  const toast = useToast();
  const [collateral, setCollateral] = useState('');
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);

  const collateralVal = parseFloat(collateral) || 0;
  const borrowVal = parseFloat(amount) || 0;
  const maxBorrow = collateralVal * ccPrice * 0.667; // 150% collateralization
  const healthFactor = borrowVal > 0 ? (collateralVal * ccPrice) / (borrowVal * 1.5) : Infinity;

  const doBorrow = async () => {
    if (!trader) {
      toast.displayError('No authenticated party found.');
      return;
    }
    if (!collateral || !amount || !isFinite(collateralVal) || !isFinite(borrowVal) || collateralVal <= 0 || borrowVal <= 0) {
      toast.displayWarning('Collateral and borrow amount must be positive numbers.');
      return;
    }
    setLoading(true);
    try {
      await borrow({
        borrower: trader,
        collateralAmount: collateralVal,
        borrowAmount: borrowVal,
      });
      toast.displaySuccess('Borrow submitted.');
      setCollateral('');
      setAmount('');
      onAction();
    }
    catch (e: any) {
      console.error(e);
      const message = e?.response?.data?.error || e?.response?.data?.message || e?.message || 'Borrow failed';
      toast.displayError(message);
    }
    setLoading(false);
  };

  return (
    <div className="panel">
      <h3>Borrow USDCx</h3>
      <div className="mb-2">
        <label className="form-label">CC Collateral</label>
        <input type="number" placeholder="0.00" value={collateral} onChange={e => setCollateral(e.target.value)} className="form-control" />
      </div>
      <div className="mb-2">
        <label className="form-label">USDCx Amount</label>
        <input type="number" placeholder="0.00" value={amount} onChange={e => setAmount(e.target.value)} className="form-control" />
      </div>
      <div className="d-flex justify-content-between small text-muted mb-1">
        <span>Max borrowable</span><span className="mono">${maxBorrow.toFixed(2)}</span>
      </div>
      <div className="d-flex justify-content-between small mb-3">
        <span className="text-muted">Health Factor</span>
        <span className={`mono fw-semibold ${healthFactor >= 1.5 ? 'text-success' : healthFactor >= 1 ? 'text-warning' : 'text-danger'}`}>
          {healthFactor === Infinity ? '∞' : healthFactor.toFixed(2)}
        </span>
      </div>
      <button onClick={doBorrow} disabled={loading} className="btn btn-primary w-100">
        {loading ? 'Submitting...' : 'Borrow'}
      </button>
    </div>
  );
};

export default BorrowSection;
