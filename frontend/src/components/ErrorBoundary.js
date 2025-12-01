import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Button, Result } from "antd";
import { Component } from "react";
/**
 * React Error Boundary component.
 * Catches JavaScript errors in child component tree and displays a fallback UI.
 */
class ErrorBoundary extends Component {
    constructor(props) {
        super(props);
        Object.defineProperty(this, "handleReload", {
            enumerable: true,
            configurable: true,
            writable: true,
            value: () => {
                window.location.reload();
            }
        });
        Object.defineProperty(this, "handleReset", {
            enumerable: true,
            configurable: true,
            writable: true,
            value: () => {
                this.setState({ hasError: false, error: null, errorInfo: null });
            }
        });
        this.state = {
            hasError: false,
            error: null,
            errorInfo: null,
        };
    }
    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }
    componentDidCatch(error, errorInfo) {
        this.setState({ errorInfo });
        // Log error to console in development
        console.error("ErrorBoundary caught an error:", error, errorInfo);
    }
    render() {
        if (this.state.hasError) {
            if (this.props.fallback) {
                return this.props.fallback;
            }
            return (_jsx("div", { style: {
                    display: "flex",
                    justifyContent: "center",
                    alignItems: "center",
                    minHeight: "100vh",
                    padding: 24,
                    background: "#f5f5f5",
                }, children: _jsx(Result, { status: "error", title: "\u9875\u9762\u51FA\u9519\u4E86", subTitle: "\u62B1\u6B49\uFF0C\u9875\u9762\u9047\u5230\u4E86\u4E00\u4E9B\u95EE\u9898\u3002\u8BF7\u5C1D\u8BD5\u5237\u65B0\u9875\u9762\u6216\u8FD4\u56DE\u9996\u9875\u3002", extra: [
                        _jsx(Button, { type: "primary", onClick: this.handleReload, children: "\u5237\u65B0\u9875\u9762" }, "reload"),
                        _jsx(Button, { onClick: this.handleReset, children: "\u91CD\u8BD5" }, "reset"),
                    ], children: import.meta.env.DEV && this.state.error && (_jsxs("div", { style: {
                            marginTop: 16,
                            padding: 16,
                            background: "#fff1f0",
                            borderRadius: 8,
                            textAlign: "left",
                            maxWidth: 600,
                            overflow: "auto",
                        }, children: [_jsx("p", { style: { color: "#cf1322", fontWeight: "bold", marginBottom: 8 }, children: "\u9519\u8BEF\u4FE1\u606F (\u4EC5\u5F00\u53D1\u73AF\u5883\u663E\u793A):" }), _jsx("pre", { style: { color: "#666", fontSize: 12, whiteSpace: "pre-wrap" }, children: this.state.error.toString() }), this.state.errorInfo && (_jsx("pre", { style: { color: "#999", fontSize: 11, marginTop: 8, whiteSpace: "pre-wrap" }, children: this.state.errorInfo.componentStack }))] })) }) }));
        }
        return this.props.children;
    }
}
export default ErrorBoundary;
