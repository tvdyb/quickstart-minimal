// Copyright (c) 2026, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: 0BSD

import React from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useUserStore } from '../stores/userStore';

const Header: React.FC = () => {
    const { user, loading, fetchUser, logout } = useUserStore();

    React.useEffect(() => {
        void fetchUser();
    }, [fetchUser]);

    return (
        <header className="app-header">
            <div className="app-header-inner">
                <div className="brand-wrap">
                    <Link to="/" className="brand-link">
                        <span className="brand-dot" />
                        <span className="brand-text">Canton Network Quickstart</span>
                    </Link>
                </div>
                <nav className="nav-tabs-main" aria-label="Main Navigation">
                    <NavTab to="/">Home</NavTab>
                    <NavTab to="/app-installs">App Installs</NavTab>
                    <NavTab to="/licenses">Licenses</NavTab>
                    <NavTab to="/trade">Trade</NavTab>
                    <NavTab to="/lend">Lend</NavTab>
                    <NavTab to="/debug">Debug</NavTab>
                    {user?.isAdmin && <NavTab to="/tenants">Tenants</NavTab>}
                </nav>
                <div className="user-panel">
                    {loading ? (
                        <span className="text-muted small">Loading user…</span>
                    ) : user ? (
                        <>
                            <div className="user-chip">
                                <div className="user-name">{user.name}</div>
                                <div className="user-party">{user.party}</div>
                            </div>
                            <button className="btn btn-outline-secondary btn-sm" onClick={() => void logout()}>
                                Logout
                            </button>
                        </>
                    ) : (
                        <Link className="btn btn-primary btn-sm" to="/login">Login</Link>
                    )}
                </div>
            </div>
        </header>
    );
};

const NavTab: React.FC<{ to: string; children: React.ReactNode }> = ({ to, children }) => (
    <NavLink
        to={to}
        className={({ isActive }) => `nav-tab ${isActive ? 'active' : ''}`}
    >
        {children}
    </NavLink>
);

export default Header;
