import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useToast } from '../stores/toastStore';
import { useUserStore } from '../stores/userStore';
import { getLedgerDiagnostics, LedgerDiagnostics } from '../services/debugApi';

const DebugLedgerView: React.FC = () => {
    const toast = useToast();
    const { lastError } = useUserStore();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [diagnostics, setDiagnostics] = useState<LedgerDiagnostics | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const start = performance.now();
            const data = await getLedgerDiagnostics(5, 20);
            const elapsed = Math.round(performance.now() - start);
            console.info('[debug] diagnostics loaded', {
                elapsedMs: elapsed,
                templateCount: data.templates?.length ?? 0,
                activeTemplates: data.activeContracts?.length ?? 0,
            });
            setDiagnostics(data);
        } catch (e: any) {
            console.error('[debug] diagnostics failed', e);
            const message = e?.response?.data?.message || e?.message || 'Failed to load diagnostics';
            setError(message);
            toast.displayError(`Diagnostics load failed: ${message}`);
        } finally {
            setLoading(false);
        }
    }, [toast]);

    useEffect(() => {
        void load();
    }, [load]);

    const sortedContracts = useMemo(() => {
        if (!diagnostics?.activeContracts) return [];
        return [...diagnostics.activeContracts]
            .sort((a, b) => (b.activeCount ?? -1) - (a.activeCount ?? -1));
    }, [diagnostics]);

    const copyDiagnostics = async () => {
        if (!diagnostics) return;
        try {
            await navigator.clipboard.writeText(JSON.stringify(diagnostics, null, 2));
            toast.displaySuccess('Diagnostics JSON copied to clipboard.');
        } catch (e: any) {
            toast.displayError(`Copy failed: ${e?.message || 'unknown error'}`);
        }
    };

    return (
        <section className="page-section">
            <div className="page-head">
                <h1 className="page-title">Debug / Ledger</h1>
                <p className="page-subtitle">
                    Deep backend introspection for auth, ledger connectivity, packages, templates, active contracts, and recent events.
                </p>
                <div className="action-row">
                    <button className="btn btn-primary btn-sm" onClick={() => void load()} disabled={loading}>
                        {loading ? 'Refreshing...' : 'Refresh'}
                    </button>
                    <button className="btn btn-outline-primary btn-sm" onClick={copyDiagnostics} disabled={!diagnostics}>
                        Copy Diagnostics JSON
                    </button>
                </div>
            </div>

            {error && (
                <div className="diagnostic-alert mb-3">
                    <div className="diagnostic-alert-head">
                        <h3 className="diagnostic-alert-title">Diagnostics Request Failed</h3>
                        <span className="status-chip status-fail">Fail</span>
                    </div>
                    <p className="diagnostic-alert-body">{error}</p>
                </div>
            )}

            {lastError && (
                <div className="diagnostic-alert mb-3">
                    <div className="diagnostic-alert-head">
                        <h3 className="diagnostic-alert-title">Latest User Fetch Error</h3>
                        <span className="status-chip status-warn">Observed</span>
                    </div>
                    <p className="diagnostic-alert-body">
                        <code>{lastError.url}</code> returned status <strong>{lastError.status ?? 'Unknown'}</strong> at{' '}
                        {new Date(lastError.timestamp).toLocaleString()}.
                    </p>
                    <div className="json-box">
                        <pre>{JSON.stringify(lastError.responseBody ?? lastError, null, 2)}</pre>
                    </div>
                </div>
            )}

            {loading && !diagnostics ? (
                <div className="empty-state">Loading diagnostics...</div>
            ) : diagnostics ? (
                <div className="debug-grid">
                    <div className="debug-col-6">
                        <div className="panel">
                            <h3>Health Checks</h3>
                            <div className="action-row">
                                <StatusItem label="Backend API" ok={!!diagnostics.health?.backend?.ok} />
                                <StatusItem label="Postgres / PQS" ok={!!diagnostics.health?.postgres?.ok} latency={diagnostics.health?.postgres?.latencyMs} />
                                <StatusItem label="Ledger API" ok={!!diagnostics.health?.ledger?.ok} latency={diagnostics.health?.ledger?.latencyMs} />
                            </div>
                            {diagnostics.health?.ledger?.error && (
                                <p className="small text-danger mt-2 mb-0">Ledger error: {String(diagnostics.health.ledger.error)}</p>
                            )}
                        </div>
                    </div>

                    <div className="debug-col-6">
                        <div className="panel">
                            <h3>Who Am I?</h3>
                            <div className="mono">Auth mode: {diagnostics.authMode}</div>
                            <div className="mono">Authenticated: {String(diagnostics.identity?.authenticated)}</div>
                            <div className="mono">Party: {diagnostics.identity?.partyFromContext ?? '-'}</div>
                            <div className="mono">Ledger app-id: {diagnostics.backend?.applicationId ?? '-'}</div>
                            <details className="mt-2">
                                <summary>User payload</summary>
                                <div className="json-box mt-2">
                                    <pre>{JSON.stringify(diagnostics.identity?.user, null, 2)}</pre>
                                </div>
                            </details>
                        </div>
                    </div>

                    <div className="debug-col-12">
                        <div className="panel">
                            <h3>Ledger Packages</h3>
                            <p className="page-subtitle">Loaded package IDs: {diagnostics.ledger?.packageCount ?? 0}</p>
                            {(diagnostics.ledger?.packageIds?.length ?? 0) === 0 ? (
                                <div className="empty-state">No package IDs returned.</div>
                            ) : (
                                <div className="json-box">
                                    <pre>{JSON.stringify(diagnostics.ledger?.packageIds ?? [], null, 2)}</pre>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="debug-col-12">
                        <div className="panel">
                            <h3>Templates and Active Contracts</h3>
                            <div className="table-responsive">
                                <table className="table table-modern">
                                    <thead>
                                    <tr>
                                        <th>Template</th>
                                        <th>Active Count</th>
                                        <th>Status</th>
                                        <th>Sample Contracts</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {sortedContracts.map((row, idx) => (
                                        <tr key={`${row.template}-${idx}`}>
                                            <td className="mono">{row.template}</td>
                                            <td className="mono">{row.activeCount ?? '-'}</td>
                                            <td>
                                                {row.error ? (
                                                    <span className="status-chip status-warn">Query Error</span>
                                                ) : (
                                                    <span className={`status-chip ${(row.activeCount ?? 0) > 0 ? 'status-ok' : 'status-warn'}`}>
                                                        {(row.activeCount ?? 0) > 0 ? 'Active' : 'Empty'}
                                                    </span>
                                                )}
                                            </td>
                                            <td>
                                                {row.error ? (
                                                    <span className="small text-danger">{row.error}</span>
                                                ) : (
                                                    <details>
                                                        <summary>View ({(row.sample ?? []).length})</summary>
                                                        <div className="json-box mt-2">
                                                            <pre>{JSON.stringify(row.sample ?? [], null, 2)}</pre>
                                                        </div>
                                                    </details>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>

                    <div className="debug-col-12">
                        <div className="panel">
                            <h3>Recent Contract Events</h3>
                            {(diagnostics.recentEvents?.length ?? 0) === 0 ? (
                                <div className="empty-state">No recent events returned.</div>
                            ) : (
                                <div className="json-box">
                                    <pre>{JSON.stringify(diagnostics.recentEvents, null, 2)}</pre>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="debug-col-12">
                        <div className="panel">
                            <h3>Raw Diagnostics</h3>
                            <details>
                                <summary>Expand full payload</summary>
                                <div className="json-box mt-2">
                                    <pre>{JSON.stringify(diagnostics, null, 2)}</pre>
                                </div>
                            </details>
                        </div>
                    </div>
                </div>
            ) : null}
        </section>
    );
};

const StatusItem: React.FC<{ label: string; ok: boolean; latency?: number }> = ({ label, ok, latency }) => (
    <div className="metric-box">
        <div className="metric-label">{label}</div>
        <div className="d-flex align-items-center gap-2 mt-1">
            <span className={`status-chip ${ok ? 'status-ok' : 'status-fail'}`}>{ok ? 'OK' : 'FAIL'}</span>
            {typeof latency === 'number' && <span className="mono">{latency}ms</span>}
        </div>
    </div>
);

export default DebugLedgerView;
