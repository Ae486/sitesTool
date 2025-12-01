"""Error type definitions for automation execution."""
from enum import Enum


class ErrorType(str, Enum):
    """Enumeration of all possible error types during automation execution."""
    
    # Element interaction errors
    ELEMENT_NOT_FOUND = "ELEMENT_NOT_FOUND"
    ELEMENT_NOT_VISIBLE = "ELEMENT_NOT_VISIBLE"
    ELEMENT_NOT_INTERACTABLE = "ELEMENT_NOT_INTERACTABLE"
    
    # Timing errors
    TIMEOUT = "TIMEOUT"
    WAIT_TIMEOUT = "WAIT_TIMEOUT"
    
    # Navigation errors
    NAVIGATION_ERROR = "NAVIGATION_ERROR"
    PAGE_LOAD_ERROR = "PAGE_LOAD_ERROR"
    
    # Browser/CDP errors
    BROWSER_CRASH = "BROWSER_CRASH"
    BROWSER_CLOSED = "BROWSER_CLOSED"
    CDP_CONNECTION_ERROR = "CDP_CONNECTION_ERROR"
    CDP_DISCONNECTED = "CDP_DISCONNECTED"
    
    # Network errors
    NETWORK_ERROR = "NETWORK_ERROR"
    SSL_ERROR = "SSL_ERROR"
    DNS_ERROR = "DNS_ERROR"
    
    # Permission errors
    PERMISSION_ERROR = "PERMISSION_ERROR"
    FILE_ACCESS_ERROR = "FILE_ACCESS_ERROR"
    
    # Validation errors
    VALIDATION_ERROR = "VALIDATION_ERROR"
    DSL_PARSE_ERROR = "DSL_PARSE_ERROR"
    SELECTOR_INVALID = "SELECTOR_INVALID"
    
    # Process errors
    MANUAL_STOP = "MANUAL_STOP"
    PROCESS_TIMEOUT = "PROCESS_TIMEOUT"
    PROCESS_KILLED = "PROCESS_KILLED"
    
    # Assertion errors
    ASSERTION_FAILED = "ASSERTION_FAILED"
    
    # Script errors
    SCRIPT_ERROR = "SCRIPT_ERROR"
    JAVASCRIPT_ERROR = "JAVASCRIPT_ERROR"
    
    # Unknown
    UNKNOWN = "UNKNOWN"


# Error type classification patterns
# Each pattern is a tuple of (keywords, error_type)
# More specific patterns should come before generic ones
# IMPORTANT: Order matters! Check specific patterns before generic ones.
ERROR_PATTERNS = [
    # Manual stop - must be checked first as it's explicit
    (["manually stopped", "user cancelled", "manual stop", "手动停止", "用户取消"], ErrorType.MANUAL_STOP),
    (["process timeout", "execution timeout", "进程超时", "执行超时"], ErrorType.PROCESS_TIMEOUT),
    (["killed", "terminated", "sigterm", "sigkill"], ErrorType.PROCESS_KILLED),
    
    # Element errors - specific first
    (["element is not visible", "not visible", "visibility"], ErrorType.ELEMENT_NOT_VISIBLE),
    (["element is not interactable", "not interactable", "intercept", "obscured"], ErrorType.ELEMENT_NOT_INTERACTABLE),
    (["no element found", "element not found", "waiting for selector", "locator resolved to"], ErrorType.ELEMENT_NOT_FOUND),
    
    # Timeout errors
    (["timeout", "timed out", "timeouterror", "exceeded"], ErrorType.TIMEOUT),
    (["waiting for", "wait_for"], ErrorType.WAIT_TIMEOUT),
    
    # CDP/Browser errors
    (["cdp", "devtools", "debugger"], ErrorType.CDP_CONNECTION_ERROR),
    (["target closed", "target crashed", "browser closed", "page closed", "context closed"], ErrorType.BROWSER_CLOSED),
    (["browser crash", "browser disconnected", "crashed"], ErrorType.BROWSER_CRASH),
    (["disconnected", "connection closed"], ErrorType.CDP_DISCONNECTED),
    
    # SSL/Certificate errors - MUST be before generic network errors
    (["ssl", "certificate", "err_cert"], ErrorType.SSL_ERROR),
    
    # DNS errors - MUST be before generic network errors
    (["dns", "name not resolved", "getaddrinfo", "err_name_not_resolved"], ErrorType.DNS_ERROR),
    
    # Network errors - generic patterns after specific ones
    (["net::err_", "network error", "failed to fetch", "fetch failed", "connection refused"], ErrorType.NETWORK_ERROR),
    
    # Navigation errors
    (["navigation", "goto", "page.goto"], ErrorType.NAVIGATION_ERROR),
    (["page load", "load event"], ErrorType.PAGE_LOAD_ERROR),
    
    # Permission errors
    (["permission denied", "access denied", "forbidden", "403"], ErrorType.PERMISSION_ERROR),
    (["file not found", "no such file", "enoent"], ErrorType.FILE_ACCESS_ERROR),
    
    # Validation errors - specific patterns first
    (["invalid selector syntax", "syntax error in selector"], ErrorType.SELECTOR_INVALID),
    (["invalid json", "json parse", "jsondecodeerror"], ErrorType.DSL_PARSE_ERROR),
    (["validation error", "valueerror"], ErrorType.VALIDATION_ERROR),
    
    # JavaScript errors
    (["javascript error", "script error", "evaluation failed", "executioncontextdestroyed"], ErrorType.JAVASCRIPT_ERROR),
    (["script", "evaluate"], ErrorType.SCRIPT_ERROR),
    
    # Assertion
    (["assertion", "assert", "expected"], ErrorType.ASSERTION_FAILED),
    
    # Generic selector/element patterns - MUST be last among element-related
    (["selector", "locator", "element"], ErrorType.ELEMENT_NOT_FOUND),
]


def classify_error(error: Exception | str) -> ErrorType:
    """
    Classify an error into a specific ErrorType.
    
    Uses pattern matching on error message to determine the most specific error type.
    Patterns are checked in order, with more specific patterns first.
    
    Args:
        error: Exception object or error message string
        
    Returns:
        ErrorType enum value
    """
    error_str = str(error).lower()
    error_type_name = type(error).__name__.lower() if isinstance(error, Exception) else ""
    
    # Combine error string and type name for matching
    full_error = f"{error_type_name} {error_str}"
    
    # Check patterns in order (most specific first)
    for keywords, error_type in ERROR_PATTERNS:
        for keyword in keywords:
            if keyword.lower() in full_error:
                return error_type
    
    # If no pattern matched, return UNKNOWN
    return ErrorType.UNKNOWN


def get_error_display_info(error_type: ErrorType) -> dict:
    """
    Get display information for an error type.
    
    Returns:
        Dict with 'label' (Chinese), 'description', and 'color' for UI display
    """
    INFO_MAP = {
        ErrorType.ELEMENT_NOT_FOUND: {
            "label": "元素未找到",
            "description": "页面上未找到指定的元素",
            "color": "orange"
        },
        ErrorType.ELEMENT_NOT_VISIBLE: {
            "label": "元素不可见",
            "description": "元素存在但不可见",
            "color": "orange"
        },
        ErrorType.ELEMENT_NOT_INTERACTABLE: {
            "label": "元素不可交互",
            "description": "元素被遮挡或禁用",
            "color": "orange"
        },
        ErrorType.TIMEOUT: {
            "label": "超时",
            "description": "操作超时未完成",
            "color": "red"
        },
        ErrorType.WAIT_TIMEOUT: {
            "label": "等待超时",
            "description": "等待元素或条件超时",
            "color": "red"
        },
        ErrorType.NAVIGATION_ERROR: {
            "label": "导航错误",
            "description": "页面导航失败",
            "color": "red"
        },
        ErrorType.PAGE_LOAD_ERROR: {
            "label": "页面加载失败",
            "description": "页面未能正常加载",
            "color": "red"
        },
        ErrorType.BROWSER_CRASH: {
            "label": "浏览器崩溃",
            "description": "浏览器进程崩溃",
            "color": "red"
        },
        ErrorType.BROWSER_CLOSED: {
            "label": "浏览器已关闭",
            "description": "浏览器或页面意外关闭",
            "color": "red"
        },
        ErrorType.CDP_CONNECTION_ERROR: {
            "label": "CDP连接错误",
            "description": "无法连接到浏览器调试端口",
            "color": "red"
        },
        ErrorType.CDP_DISCONNECTED: {
            "label": "CDP连接断开",
            "description": "与浏览器的连接断开",
            "color": "red"
        },
        ErrorType.NETWORK_ERROR: {
            "label": "网络错误",
            "description": "网络请求失败",
            "color": "red"
        },
        ErrorType.SSL_ERROR: {
            "label": "SSL错误",
            "description": "SSL证书验证失败",
            "color": "red"
        },
        ErrorType.DNS_ERROR: {
            "label": "DNS错误",
            "description": "域名解析失败",
            "color": "red"
        },
        ErrorType.PERMISSION_ERROR: {
            "label": "权限错误",
            "description": "无权限执行操作",
            "color": "volcano"
        },
        ErrorType.FILE_ACCESS_ERROR: {
            "label": "文件访问错误",
            "description": "无法访问文件",
            "color": "volcano"
        },
        ErrorType.VALIDATION_ERROR: {
            "label": "验证错误",
            "description": "参数验证失败",
            "color": "gold"
        },
        ErrorType.DSL_PARSE_ERROR: {
            "label": "DSL解析错误",
            "description": "流程配置格式错误",
            "color": "gold"
        },
        ErrorType.SELECTOR_INVALID: {
            "label": "选择器无效",
            "description": "CSS/XPath选择器格式错误",
            "color": "gold"
        },
        ErrorType.MANUAL_STOP: {
            "label": "手动停止",
            "description": "用户手动停止了执行",
            "color": "blue"
        },
        ErrorType.PROCESS_TIMEOUT: {
            "label": "进程超时",
            "description": "执行进程超时被终止",
            "color": "red"
        },
        ErrorType.PROCESS_KILLED: {
            "label": "进程终止",
            "description": "执行进程被强制终止",
            "color": "red"
        },
        ErrorType.ASSERTION_FAILED: {
            "label": "断言失败",
            "description": "验证条件未满足",
            "color": "orange"
        },
        ErrorType.SCRIPT_ERROR: {
            "label": "脚本错误",
            "description": "脚本执行出错",
            "color": "red"
        },
        ErrorType.JAVASCRIPT_ERROR: {
            "label": "JavaScript错误",
            "description": "页面JavaScript执行错误",
            "color": "red"
        },
        ErrorType.UNKNOWN: {
            "label": "未知错误",
            "description": "无法识别的错误类型",
            "color": "default"
        },
    }
    return INFO_MAP.get(error_type, INFO_MAP[ErrorType.UNKNOWN])
