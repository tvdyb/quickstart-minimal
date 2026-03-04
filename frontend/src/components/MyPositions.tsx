import React from 'react';

interface Position { id: string; type: 'supply' | 'borrow'; amount: number; collateral?: number; healthFactor?: number; }
interface Props { positions: Position[]; }

const MyPositions: React.FC<Props> = ({ positions }) => (
  <div className="panel">
    <h3>My Positions</h3>
    {positions.length === 0 ? (
      <div className="empty-state">No positions yet. Supply or borrow to create positions.</div>
    ) : (
      <div className="table-responsive">
        <table className="table table-modern">
          <thead>
            <tr>
              <th>Type</th>
              <th>Amount</th>
              <th>Collateral</th>
              <th>Health</th>
            </tr>
          </thead>
          <tbody>
        {positions.map(p => (
          <tr key={p.id}>
            <td>
              <span className={`status-chip ${p.type === 'supply' ? 'status-ok' : 'status-warn'}`}>
              {p.type.toUpperCase()}
              </span>
            </td>
            <td className="mono">${p.amount.toLocaleString()}</td>
            <td className="mono">{p.collateral ? `$${p.collateral.toLocaleString()}` : '-'}</td>
            <td className="mono">
              {p.healthFactor === undefined
                ? '-'
                : p.healthFactor.toFixed(2)}
            </td>
          </tr>
        ))}
          </tbody>
        </table>
      </div>
    )}
  </div>
);

export default MyPositions;
