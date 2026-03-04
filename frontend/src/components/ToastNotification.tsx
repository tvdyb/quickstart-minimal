// Copyright (c) 2026, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: 0BSD

import React from 'react';
import { useToast } from '../stores/toastStore';

const ToastNotification: React.FC = () => {
    const { toast, show, hideError } = useToast();

    if (!show || !toast) return null;

    const classByType: Record<string, string> = {
        error: 'toast-panel toast-error',
        success: 'toast-panel toast-success',
        info: 'toast-panel toast-info',
        warning: 'toast-panel toast-warning',
    };

    return (
        <div className="toast-wrap" role="status" aria-live="polite" aria-atomic="true">
            <div className={classByType[toast.type]}>
                <div className="toast-title-row">
                    <strong>{toast.title}</strong>
                    <button type="button" className="toast-close" onClick={hideError} aria-label="Close">
                        ×
                    </button>
                </div>
                <div className="toast-message">{toast.message}</div>
            </div>
        </div>
    );
};

export default ToastNotification;
