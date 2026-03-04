// Copyright (c) 2026, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: 0BSD

import React, { useEffect, useState } from 'react';
import { useToast } from '../stores/toastStore';
import api from '../api';
import { Client, LoginLink, FeatureFlags } from "../openapi";

const LoginView: React.FC = () => {
    const [loginLinks, setLoginLinks] = useState<LoginLink[]>([]);
    const [featureFlags, setFeatureFlags] = useState<FeatureFlags | null>(null);
    const toast = useToast();

    useEffect(() => {
        const fetchLoginLinks = async () => {
            try {
                const client: Client = await api.getClient();
                const response = await client.listLinks();
                setLoginLinks(response.data);
            } catch (error) {
                toast.displayError('Error fetching login links');
            }
        };
        const fetchFeatureFlags = async () => {
            try {
                const client: Client = await api.getClient();
                const response = await client.getFeatureFlags();
                setFeatureFlags(response.data);
                if (response.data.authMode === 'oauth2') {
                    await fetchLoginLinks();
                }
            } catch (error) {
                toast.displayError('Error fetching feature flags');
            }
        };
        fetchFeatureFlags();
    }, [toast]);

    return (
        featureFlags?.authMode === 'oauth2' ? (
            <section className="page-section">
                <div className="page-head">
                    <h1 className="page-title">Sign In</h1>
                    <p className="page-subtitle">Choose your OAuth identity provider account.</p>
                </div>
                <table className="table table-modern">
                    <tbody>
                        {loginLinks.map((link) => (
                            <tr key={link.url}>
                                <td>
                                    <a className="btn btn-primary btn-sm" href={link.url}>{link.name}</a>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                <div className="panel mt-3">
                    <div>AppProvider user: <code>app-provider</code>, password: <code>abc123</code></div>
                    <div>AppUser user: <code>app-user</code>, password: <code>abc123</code></div>
                </div>
            </section>
        ) : (
            <section className="page-section">
                <div className="page-head">
                    <h1 className="page-title">Login</h1>
                    <p className="page-subtitle">Shared-secret mode</p>
                </div>
                <form name="f" action="login/shared-secret" method="POST" className="login-form">
                    <div className="form-group">
                        <label htmlFor="username" className="form-label">User:</label>
                        <input type="text" id="username" name="username" className="form-input" />
                        <button type="submit" name="submit" className="form-button">Sign in</button>
                    </div>
                </form>
                <div className="panel mt-3">
                    <div>AppProvider user: <code>app-provider</code></div>
                    <div>AppUser user: <code>app-user</code></div>
                </div>
            </section>

        )
    );
}

export default LoginView;
