"""Tests for DSL parser."""
import pytest

from app.services.automation.dsl_parser import DSLParser, ParsedStep, StepType


@pytest.fixture
def parser():
    """Create a DSL parser instance."""
    return DSLParser()


class TestDSLParser:
    """Tests for DSLParser class."""

    def test_parse_empty_steps(self, parser: DSLParser):
        """Test parsing DSL with empty steps array."""
        dsl = '{"steps": []}'
        result = parser.parse(dsl)
        assert result == []

    def test_parse_navigate_step(self, parser: DSLParser):
        """Test parsing a navigate step."""
        dsl = '{"steps": [{"type": "navigate", "url": "https://example.com"}]}'
        result = parser.parse(dsl)
        assert len(result) == 1
        assert result[0].type == StepType.NAVIGATE
        assert result[0].params["url"] == "https://example.com"

    def test_parse_click_step(self, parser: DSLParser):
        """Test parsing a click step."""
        dsl = '{"steps": [{"type": "click", "selector": "#button"}]}'
        result = parser.parse(dsl)
        assert len(result) == 1
        assert result[0].type == StepType.CLICK
        assert result[0].params["selector"] == "#button"

    def test_parse_input_step(self, parser: DSLParser):
        """Test parsing an input step."""
        dsl = '{"steps": [{"type": "input", "selector": "#email", "value": "test@example.com"}]}'
        result = parser.parse(dsl)
        assert len(result) == 1
        assert result[0].type == StepType.INPUT
        assert result[0].params["selector"] == "#email"
        assert result[0].params["value"] == "test@example.com"

    def test_parse_wait_time_step(self, parser: DSLParser):
        """Test parsing a wait_time step."""
        dsl = '{"steps": [{"type": "wait_time", "duration": 1000}]}'
        result = parser.parse(dsl)
        assert len(result) == 1
        assert result[0].type == StepType.WAIT_TIME
        assert result[0].params["duration"] == 1000

    def test_parse_invalid_json(self, parser: DSLParser):
        """Test parsing invalid JSON."""
        with pytest.raises(ValueError, match="Invalid JSON"):
            parser.parse("not valid json")

    def test_parse_missing_steps(self, parser: DSLParser):
        """Test parsing DSL without steps field."""
        with pytest.raises(ValueError, match="must contain 'steps'"):
            parser.parse('{"version": 1}')

    def test_parse_unknown_step_type(self, parser: DSLParser):
        """Test parsing unknown step type."""
        dsl = '{"steps": [{"type": "unknown_type"}]}'
        with pytest.raises(ValueError, match="Unknown step type"):
            parser.parse(dsl)

    def test_parse_step_with_description(self, parser: DSLParser):
        """Test parsing step with description."""
        dsl = '{"steps": [{"type": "navigate", "url": "https://example.com", "description": "Go to homepage"}]}'
        result = parser.parse(dsl)
        assert result[0].description == "Go to homepage"

    def test_parse_multiple_steps(self, parser: DSLParser):
        """Test parsing multiple steps."""
        dsl = '''{"steps": [
            {"type": "navigate", "url": "https://example.com"},
            {"type": "click", "selector": "#login"},
            {"type": "input", "selector": "#email", "value": "test@example.com"}
        ]}'''
        result = parser.parse(dsl)
        assert len(result) == 3
        assert result[0].type == StepType.NAVIGATE
        assert result[1].type == StepType.CLICK
        assert result[2].type == StepType.INPUT

    def test_validate_wait_time_requires_positive_duration(self, parser: DSLParser):
        """Test that wait_time requires positive duration."""
        dsl = '{"steps": [{"type": "wait_time", "duration": -1}]}'
        with pytest.raises(ValueError, match="must be greater than zero"):
            parser.parse(dsl)

    def test_validate_extract_requires_variable(self, parser: DSLParser):
        """Test that extract step requires variable parameter."""
        dsl = '{"steps": [{"type": "extract", "selector": "#data"}]}'
        with pytest.raises(ValueError, match="requires 'variable'"):
            parser.parse(dsl)
