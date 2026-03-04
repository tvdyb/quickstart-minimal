// Copyright (c) 2026, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: 0BSD

import React, { createContext, useContext, useState, useCallback } from 'react';
import { useToast } from './toastStore';
import api from '../api';
import { useNavigate } from 'react-router-dom';
import type { AuthenticatedUser, Client } from "../openapi.d.ts";

export interface UserFetchError {
    url: string;
    status?: number;
    message: string;
    responseBody?: unknown;
    timestamp: string;
}

interface UserContextType {
    user: AuthenticatedUser | null;
    loading: boolean;
    lastError: UserFetchError | null;
    fetchUser: () => Promise<void>;
    clearUser: () => void;
    clearLastError: () => void;
    logout: () => Promise<void>;
}

const UserContext = createContext<UserContextType | undefined>(undefined);

export const UserProvider = ({ children }: { children: React.ReactNode }) => {
    const [user, setUser] = useState<AuthenticatedUser | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [lastError, setLastError] = useState<UserFetchError | null>(null);
    const toast = useToast();
    const navigate = useNavigate();

    const extractError = (error: unknown): UserFetchError => {
        const err = error as any;
        return {
            url: '/api/user',
            status: err?.response?.status,
            message: err?.response?.data?.message || err?.message || 'Unknown error',
            responseBody: err?.response?.data,
            timestamp: new Date().toISOString(),
        };
    };

    const fetchUser = useCallback(async () => {
        const url = '/api/user';
        console.info('[user] fetching', { url, at: new Date().toISOString() });
        setLoading(true);
        try {
            const client: Client = await api.getClient();
            const response = await client.getAuthenticatedUser();
            console.info('[user] fetch ok', { url, status: response.status });
            setLastError(null);
            setUser(response.data);
        } catch (error) {
            const details = extractError(error);
            console.error('[user] fetch failed', details);
            if (details.status === 401) {
                setUser(null);
                setLastError(null);
            } else {
                setLastError(details);
                toast.displayError('Failed to load user context. Open Debug for diagnostics.');
            }
        } finally {
            setLoading(false);
        }
    }, [setUser, setLoading, toast, setLastError]);

    const clearUser = useCallback(() => {
        setUser(null);
    }, [setUser]);

    const clearLastError = useCallback(() => {
        setLastError(null);
    }, [setLastError]);

    const getCsrfToken = (): string => {
        const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    };

    const logout = useCallback(async () => {
        try {
            const response = await fetch('/api/logout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': getCsrfToken(),
                },
            });
            if (response.ok) {
                clearUser();
                clearLastError();
                navigate('/');
            } else {
                toast.displayError('Error logging out');
            }
        } catch (error) {
            toast.displayError('Error logging out');
        }
    }, [clearUser, clearLastError, toast, navigate, getCsrfToken]);

    return (
        <UserContext.Provider value={{ user, loading, lastError, fetchUser, clearUser, clearLastError, logout }}>
            {children}
        </UserContext.Provider>
    );
};

export const useUserStore = () => {
    const context = useContext(UserContext);
    if (context === undefined) {
        throw new Error('useUser must be used within a UserProvider');
    }
    return context;
};
