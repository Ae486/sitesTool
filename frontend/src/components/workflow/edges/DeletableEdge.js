import { jsx as _jsx, Fragment as _Fragment, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Deletable Edge - Edge with delete button on hover/click
 */
import { memo, useState } from "react";
import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, useReactFlow, } from "@xyflow/react";
import { CloseCircleFilled } from "@ant-design/icons";
function DeletableEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style = {}, markerEnd, selected, }) {
    const { setEdges } = useReactFlow();
    const [isHovered, setIsHovered] = useState(false);
    // Use smooth step path for better visual
    const [edgePath, labelX, labelY] = getSmoothStepPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
        borderRadius: 10,
    });
    const handleDelete = (event) => {
        event.stopPropagation();
        setEdges((edges) => edges.filter((e) => e.id !== id));
    };
    const showDeleteButton = isHovered || selected;
    return (_jsxs(_Fragment, { children: [_jsx("path", { d: edgePath, fill: "none", strokeWidth: 20, stroke: "transparent", onMouseEnter: () => setIsHovered(true), onMouseLeave: () => setIsHovered(false), style: { cursor: "pointer" } }), _jsx(BaseEdge, { id: id, path: edgePath, markerEnd: markerEnd, style: {
                    ...style,
                    strokeWidth: selected ? 3 : 2,
                    stroke: selected ? "#1890ff" : isHovered ? "#595959" : "#b1b1b7",
                    transition: "stroke 0.15s ease, stroke-width 0.15s ease",
                } }), showDeleteButton && (_jsx(EdgeLabelRenderer, { children: _jsx("div", { style: {
                        position: "absolute",
                        transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
                        pointerEvents: "all",
                    }, onMouseEnter: () => setIsHovered(true), onMouseLeave: () => setIsHovered(false), children: _jsx("button", { onClick: handleDelete, style: {
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            width: 20,
                            height: 20,
                            borderRadius: "50%",
                            background: "#fff",
                            border: "1px solid #d9d9d9",
                            cursor: "pointer",
                            padding: 0,
                            boxShadow: "0 2px 4px rgba(0,0,0,0.1)",
                            transition: "all 0.15s ease",
                        }, onMouseEnter: (e) => {
                            e.currentTarget.style.background = "#ff4d4f";
                            e.currentTarget.style.borderColor = "#ff4d4f";
                            e.currentTarget.style.color = "#fff";
                        }, onMouseLeave: (e) => {
                            e.currentTarget.style.background = "#fff";
                            e.currentTarget.style.borderColor = "#d9d9d9";
                            e.currentTarget.style.color = "inherit";
                        }, title: "\u5220\u9664\u8FDE\u7EBF", children: _jsx(CloseCircleFilled, { style: { fontSize: 12, color: "inherit" } }) }) }) }))] }));
}
export default memo(DeletableEdge);
