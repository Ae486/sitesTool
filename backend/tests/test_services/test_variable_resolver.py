"""Tests for variable resolver compatibility.

These tests protect existing behavior while allowing gradual refactors.
"""

from app.services.automation.handlers.base import resolve_variables as handler_resolve
from app.services.automation.variable_resolver import resolve_variables


def test_resolve_variables_supports_double_curly_braces():
    variables = {"name": "alice", "n": 3}
    assert resolve_variables("hello {{name}}", variables) == "hello alice"
    assert resolve_variables("{{n}} items", variables) == "3 items"


def test_resolve_variables_supports_dollar_braces():
    variables = {"name": "alice", "n": 3}
    assert resolve_variables("hello ${name}", variables) == "hello alice"
    assert resolve_variables("${n} items", variables) == "3 items"


def test_resolve_variables_non_str_passthrough_by_default():
    variables = {"x": "y"}
    assert resolve_variables(123, variables) == 123
    assert resolve_variables(True, variables) is True


def test_resolve_variables_can_stringify_non_str_when_requested():
    variables = {"x": "y"}
    assert resolve_variables(123, variables, stringify_non_str=True) == "123"
    assert resolve_variables(True, variables, stringify_non_str=True) == "True"


def test_handler_resolve_variables_preserves_previous_stringify_behavior():
    # handler/base.py historically stringified non-string input
    variables = {"name": "alice"}
    assert handler_resolve(123, variables) == "123"
    assert handler_resolve("{{name}}", variables) == "alice"
    assert handler_resolve("${name}", variables) == "alice"
