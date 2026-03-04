import React from 'react';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';

interface Props {
  trades: { price: number; executedAt?: string }[];
}

const PriceChart: React.FC<Props> = ({ trades }) => {
  const data = trades.slice(-50).map((t, i) => ({
    i,
    price: t.price,
    at: t.executedAt ? new Date(t.executedAt).toLocaleTimeString() : `${i}`
  }));
  if (data.length === 0) return (
    <div className="panel">
      No trade data for chart
    </div>
  );

  return (
    <div className="panel">
      <h3>Price Trend</h3>
      <ResponsiveContainer width="100%" height={210}>
        <LineChart data={data}>
          <XAxis dataKey="at" minTickGap={30} tick={{ fill: '#63708a', fontSize: 11 }} />
          <YAxis domain={['auto', 'auto']} tick={{ fill: '#63708a', fontSize: 11 }} width={56} />
          <Tooltip
            contentStyle={{ backgroundColor: '#ffffff', border: '1px solid #dbe3ef', borderRadius: 8, fontSize: 12 }}
            formatter={(value: number | string | undefined) => [`${Number(value ?? 0).toFixed(4)}`, 'Price']}
          />
          <Line type="monotone" dataKey="price" stroke="#2f6df6" strokeWidth={2.5} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
};

export default PriceChart;
