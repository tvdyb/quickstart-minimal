import React from 'react';
import { Link } from 'react-router-dom';
import { useUserStore } from '../stores/userStore';

const UserFetchErrorCard: React.FC = () => {
    const { lastError, fetchUser, clearLastError } = useUserStore();

    if (!lastError) return null;

    return (
        <section className="diagnostic-alert" role="alert">
            <div className="diagnostic-alert-head">
                <h3 className="diagnostic-alert-title">User Context Error</h3>
                <span className="status-chip status-fail">Needs Attention</span>
            </div>
            <p className="diagnostic-alert-body">
                Could not load authenticated user details from <code>{lastError.url}</code>.
                This often indicates auth/session issues or backend routing mismatches.
            </p>
            <div className="diagnostic-meta">
                <span>Status: {lastError.status ?? 'Unknown'}</span>
                <span>Time: {new Date(lastError.timestamp).toLocaleString()}</span>
            </div>
            <div className="action-row">
                <button className="btn btn-primary" onClick={() => void fetchUser()}>Retry</button>
                <Link className="btn btn-outline-primary" to="/debug">Open Diagnostics</Link>
                <button className="btn btn-outline-secondary" onClick={clearLastError}>Dismiss</button>
            </div>
        </section>
    );
};

export default UserFetchErrorCard;
