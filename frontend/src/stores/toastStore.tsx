// Copyright (c) 2026, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: 0BSD

import React, { createContext, useContext, useState, useCallback, useRef } from 'react'

type ToastType = 'error' | 'success' | 'info' | 'warning';

interface ToastState {
    type: ToastType;
    title: string;
    message: string;
}

interface ToastContextType {
    toast: ToastState | null
    show: boolean
    displayError: (message: string) => void
    displaySuccess: (message: string) => void
    displayInfo: (message: string) => void
    displayWarning: (message: string) => void
    hideError: () => void
}

interface ToastProviderProps {
    children: React.ReactNode
}

const ToastContext = createContext<ToastContextType | undefined>(undefined)

export const ToastProvider = ({ children }: ToastProviderProps) => {
    const [toast, setToast] = useState<ToastState | null>(null)
    const [show, setShow] = useState(false)
    const timeoutIdRef = useRef<ReturnType<typeof setTimeout> | null>(null)

    const hideError = useCallback(() => {
        setToast(null)
        setShow(false)
        if (timeoutIdRef.current !== null) {
            clearTimeout(timeoutIdRef.current)
            timeoutIdRef.current = null
        }
    }, [])

    const displayError = useCallback(
        (msg: string) => {
            setToast({ type: 'error', title: 'Error', message: msg })
            setShow(true)
            if (timeoutIdRef.current !== null) {
                clearTimeout(timeoutIdRef.current)
            }
            timeoutIdRef.current = setTimeout(() => {
                hideError()
            }, 10000)
        },
        [hideError]
    )

    const displaySuccess = useCallback(
        (msg: string) => {
            setToast({ type: 'success', title: 'Success', message: msg })
            setShow(true)
            if (timeoutIdRef.current !== null) {
                clearTimeout(timeoutIdRef.current)
            }
            timeoutIdRef.current = setTimeout(() => {
                hideError()
            }, 5000)
        },
        [hideError]
    )

    const displayInfo = useCallback(
        (msg: string) => {
            setToast({ type: 'info', title: 'Info', message: msg })
            setShow(true)
            if (timeoutIdRef.current !== null) {
                clearTimeout(timeoutIdRef.current)
            }
            timeoutIdRef.current = setTimeout(() => {
                hideError()
            }, 6000)
        },
        [hideError]
    )

    const displayWarning = useCallback(
        (msg: string) => {
            setToast({ type: 'warning', title: 'Warning', message: msg })
            setShow(true)
            if (timeoutIdRef.current !== null) {
                clearTimeout(timeoutIdRef.current)
            }
            timeoutIdRef.current = setTimeout(() => {
                hideError()
            }, 8000)
        },
        [hideError]
    )

    return (
        <ToastContext.Provider value={{ toast, show, displayError, displaySuccess, displayInfo, displayWarning, hideError }}>
            {children}
        </ToastContext.Provider>
    )
}

export const useToast = () => {
    const context = useContext(ToastContext)
    if (context === undefined) {
        throw new Error('useToast must be used within a ToastProvider')
    }
    return context
}
