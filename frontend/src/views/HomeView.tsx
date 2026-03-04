// Copyright (c) 2026, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: 0BSD

import React, { useEffect } from 'react';
import { useUserStore } from '../stores/userStore';
import { Link, useNavigate } from 'react-router-dom';

const HomeView: React.FC = () => {
    const { user, loading } = useUserStore();
    const navigate = useNavigate();

    useEffect(() => {
        if (!loading && user === null) {
            navigate('/login');
        }
    }, [user, loading, navigate]);

    if (loading) {
        return <section className="page-section"><p className="page-subtitle">Loading dashboard...</p></section>;
    }

    return (
        <section className="page-section">
            <div className="page-head">
                <h1 className="page-title">Control Center</h1>
                <p className="page-subtitle">
                    Manage app installs, licenses, and Umbra flows. Use the Debug tab for full Canton/DAML diagnostics.
                </p>
            </div>
            <div className="metrics-row">
                <div className="metric-box">
                    <div className="metric-label">Current User</div>
                    <div className="metric-value" style={{ fontSize: '1.05rem' }}>{user?.name ?? 'Not logged in'}</div>
                    <div className="mono">{user?.party ?? '-'}</div>
                </div>
                <div className="metric-box">
                    <div className="metric-label">Tenant</div>
                    <div className="metric-value" style={{ fontSize: '1.05rem' }}>{user?.isAdmin ? 'AppProvider (Admin)' : 'App User'}</div>
                    <div className="mono">{(user?.roles ?? []).join(', ') || '-'}</div>
                </div>
                <div className="metric-box">
                    <div className="metric-label">Backend Diagnostics</div>
                    <div className="metric-value" style={{ fontSize: '1.05rem' }}>Ready</div>
                    <Link to="/debug" className="mono">Open Debug / Ledger</Link>
                </div>
            </div>
            <div className="debug-grid mt-3">
                <div className="debug-col-6">
                    <div className="panel">
                        <h3>Operations</h3>
                        <p className="page-subtitle">Run your daily flows from these pages.</p>
                        <div className="action-row">
                            <Link className="btn btn-primary" to="/app-installs">App Installs</Link>
                            <Link className="btn btn-outline-primary" to="/licenses">Licenses</Link>
                            <Link className="btn btn-outline-primary" to="/trade">Trade</Link>
                            <Link className="btn btn-outline-primary" to="/lend">Lend</Link>
                        </div>
                    </div>
                </div>
                <div className="debug-col-6">
                    <div className="panel">
                        <h3>Troubleshooting</h3>
                        <ul className="data-list">
                            <li>Use <strong>Debug</strong> to verify user, ledger connectivity, and templates.</li>
                            <li>If data looks empty, check active contract counts in Debug.</li>
                            <li>For auth issues, retry login and inspect the Diagnostics JSON export.</li>
                        </ul>
                    </div>
                </div>
            </div>
        </section>
    );
};

export default HomeView;
