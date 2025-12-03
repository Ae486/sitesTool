import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
/**
 * Node Properties Panel - Edit selected node's fields
 * With debounced updates for better performance
 */
import { useCallback, memo } from "react";
import { Form, Input, InputNumber, Switch, Select, Button, Typography, Divider, Tag } from "antd";
import { DeleteOutlined } from "@ant-design/icons";
import { useDebounceFn } from "ahooks";
import { DSL_SCHEMA, isContainerType } from "../../../constants/dsl";
import SelectorInput from "../../common/SelectorInput";
const { Text, Title } = Typography;
// Check if field should be visible based on showWhen condition
const shouldShowField = (field, fields) => {
    if (!field.showWhen || field.showWhen.length === 0)
        return true;
    const conditionType = fields.condition_type;
    return field.showWhen.includes(conditionType);
};
// Field renderer component
const FieldRenderer = memo(function FieldRenderer({ field, value, onChange, }) {
    // Debounce text input changes (200ms)
    const { run: debouncedChange } = useDebounceFn(onChange, { wait: 200 });
    if (field.type === "boolean") {
        return (_jsx(Switch, { size: "small", checked: Boolean(value), onChange: onChange }));
    }
    if (field.type === "number") {
        return (_jsx(InputNumber, { size: "small", style: { width: "100%" }, placeholder: field.placeholder, value: typeof value === "number" ? value : undefined, onChange: onChange }));
    }
    if (field.type === "select" && field.options) {
        return (_jsx(Select, { size: "small", style: { width: "100%" }, placeholder: field.placeholder, value: value || undefined, onChange: onChange, options: field.options }));
    }
    // Selector input with auto-parser
    if (field.hasAutoParser) {
        return (_jsx(SelectorInput, { size: "small", value: typeof value === "string" ? value : "", onChange: (val) => onChange(val), placeholder: field.placeholder }));
    }
    // Text input with debounce
    return (_jsx(Input, { size: "small", placeholder: field.placeholder, defaultValue: typeof value === "string" ? value : "", onChange: (e) => debouncedChange(e.target.value) }));
});
function NodeProperties({ node, onUpdate, onDelete }) {
    const { data } = node;
    const schema = DSL_SCHEMA[data.stepType];
    const isContainer = isContainerType(data.stepType);
    // Memoized field change handler
    const handleFieldChange = useCallback((fieldName, value) => {
        onUpdate({
            fields: {
                ...data.fields,
                [fieldName]: value,
            },
        });
    }, [data.fields, onUpdate]);
    // Debounced description change
    const { run: handleDescriptionChange } = useDebounceFn((value) => onUpdate({ description: value }), { wait: 200 });
    if (!schema) {
        return (_jsx("div", { style: { padding: 16 }, children: _jsxs(Text, { type: "secondary", children: ["\u672A\u77E5\u6B65\u9AA4\u7C7B\u578B: ", data.stepType] }) }));
    }
    return (_jsxs("div", { style: { padding: 12 }, children: [_jsxs("div", { style: {
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: 8,
                }, children: [_jsxs("div", { children: [_jsx(Title, { level: 5, style: { margin: 0, fontSize: 14 }, children: schema.label }), isContainer && (_jsx(Tag, { color: "orange", style: { marginTop: 4, fontSize: 10 }, children: "\u5BB9\u5668\u8282\u70B9" }))] }), _jsx(Button, { danger: true, size: "small", type: "text", icon: _jsx(DeleteOutlined, {}), onClick: onDelete })] }), _jsx(Text, { type: "secondary", style: { fontSize: 11, display: "block", marginBottom: 12 }, children: schema.description }), _jsx(Divider, { style: { margin: "8px 0" } }), _jsxs(Form, { layout: "vertical", size: "small", children: [schema.fields
                        .filter(field => !field.advanced && shouldShowField(field, data.fields))
                        .map((field) => (_jsx(Form.Item, { label: _jsx("span", { style: { fontSize: 12 }, children: field.label }), required: field.required, style: { marginBottom: 10 }, children: _jsx(FieldRenderer, { field: field, value: data.fields[field.name], onChange: (val) => handleFieldChange(field.name, val) }) }, field.name))), _jsx(Form.Item, { label: _jsx("span", { style: { fontSize: 12 }, children: "\u6B65\u9AA4\u8BF4\u660E" }), style: { marginBottom: 10 }, children: _jsx(Input.TextArea, { size: "small", rows: 2, placeholder: "\u53EF\u9009\uFF1A\u63CF\u8FF0\u8FD9\u4E2A\u6B65\u9AA4", defaultValue: data.description || "", onChange: (e) => handleDescriptionChange(e.target.value) }) }), schema.fields.filter(field => field.advanced && shouldShowField(field, data.fields)).length > 0 && (_jsxs(_Fragment, { children: [_jsx(Divider, { style: { margin: "8px 0", fontSize: 11 }, children: _jsx(Text, { type: "secondary", children: "\u9AD8\u7EA7\u9009\u9879" }) }), schema.fields
                                .filter(field => field.advanced && shouldShowField(field, data.fields))
                                .map((field) => (_jsx(Form.Item, { label: _jsx("span", { style: { fontSize: 12 }, children: field.label }), required: field.required, style: { marginBottom: 10 }, children: _jsx(FieldRenderer, { field: field, value: data.fields[field.name], onChange: (val) => handleFieldChange(field.name, val) }) }, field.name)))] }))] }), _jsxs("div", { style: { marginTop: 12, fontSize: 10, color: "#bfbfbf" }, children: ["ID: ", node.id] })] }));
}
export default memo(NodeProperties);
