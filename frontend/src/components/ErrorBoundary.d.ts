import { Component, ErrorInfo, ReactNode } from "react";
interface Props {
    children: ReactNode;
    fallback?: ReactNode;
}
interface State {
    hasError: boolean;
    error: Error | null;
    errorInfo: ErrorInfo | null;
}
/**
 * React Error Boundary component.
 * Catches JavaScript errors in child component tree and displays a fallback UI.
 */
declare class ErrorBoundary extends Component<Props, State> {
    constructor(props: Props);
    static getDerivedStateFromError(error: Error): Partial<State>;
    componentDidCatch(error: Error, errorInfo: ErrorInfo): void;
    handleReload: () => void;
    handleReset: () => void;
    render(): ReactNode;
}
export default ErrorBoundary;
